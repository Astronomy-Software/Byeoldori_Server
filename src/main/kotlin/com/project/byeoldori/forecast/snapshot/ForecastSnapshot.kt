package com.project.byeoldori.forecast.snapshot

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 예보 메모리 캐시(실황·초단기·단기)를 Redis 에 남겨 두는 스냅샷 종류.
 * 블루그린 배포로 새 컨테이너가 뜰 때 기상청에서 전부 다시 받지 않고 여기서 바로 올린다.
 * 키는 고정이고, TTL 은 각 예보가 쓸모 있는 기간보다 조금 길게 잡는다(정기 갱신이 몇 번 실패해도 대체값으로 남게).
 */
enum class ForecastSnapshotKind(val redisKey: String, val ttl: Duration) {
    LIVE("forecast:snapshot:live", Duration.ofHours(2)),    // 실황: 매시 발표, 10분마다 갱신
    ULTRA("forecast:snapshot:ultra", Duration.ofHours(3)),  // 초단기: 30분 발표, 6시간 앞까지
    SHORT("forecast:snapshot:short", Duration.ofHours(9)),  // 단기: 3시간 발표, 3일 앞까지
}

/** 기동 시 스냅샷 복원 결과 */
enum class SnapshotRestore {
    FRESH,  // 지금 받아야 할 발표(tmfc)와 같다 → 기상청 재적재를 건너뛴다
    STALE,  // 이전 발표다 → 일단 메모리에 올려 두고 기상청에서 새로 받는다
    NONE,   // 없음·형식 버전 다름·Redis 장애 → 기존처럼 기상청에서 받는다
}

data class ForecastSnapshot<C>(
    val tmfc: String,
    val savedAt: Instant,
    val slots: LinkedHashMap<String, MutableList<MutableList<C>>>,
)

/** 격자 셀 ↔ 숫자 필드 변환. 모든 값은 Float 로 저장하고 null 은 NaN 으로 둔다(정수 필드도 Float 로 손실 없이 담긴다). */
interface GridCellCodec<C> {
    val fieldCount: Int
    fun field(cell: C, index: Int): Float?
    fun cell(values: FloatArray): C

    fun Float.orNull(): Float? = if (isNaN()) null else this
    fun Float.intOrNull(): Int? = if (isNaN()) null else toInt()
}

/**
 * 스냅샷 직렬화 형식.
 * 단기예보는 tmef 약 70개 × 변수 11개 × 격자 253×149 라 JSON 으로는 수백 MB 가 되므로,
 * 변수별로 모은 Float 배열을 gzip 한 이진 형식으로 저장한다.
 * 맨 앞에 MAGIC·SCHEMA_VERSION 을 두어, 형식이 바뀌면 예전 스냅샷은 읽지 않고(null) 기상청 경로로 돌아간다.
 */
object ForecastSnapshotCodec {
    const val MAGIC = 0x42445346          // "BDFS"
    const val SCHEMA_VERSION = 1
    private const val MAX_SLOTS = 200
    private const val MAX_DIM = 2_000

    fun <C> encode(
        kind: ForecastSnapshotKind,
        tmfc: String,
        savedAt: Instant,
        slots: Map<String, List<List<C>>>,
        codec: GridCellCodec<C>,
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(BufferedOutputStream(GZIPOutputStream(bytes), 64 * 1024)).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(SCHEMA_VERSION)
            out.writeUTF(kind.name)
            out.writeUTF(tmfc)
            out.writeLong(savedAt.toEpochMilli())
            out.writeInt(codec.fieldCount)
            out.writeInt(slots.size)
            for ((slot, grid) in slots) {
                out.writeUTF(slot)
                out.writeInt(grid.size)
                grid.forEach { row -> out.writeInt(row.size) }
                // 변수별로 이어 써야 같은 값이 몰려 압축이 잘 된다
                for (f in 0 until codec.fieldCount) {
                    for (row in grid) for (cell in row) out.writeFloat(codec.field(cell, f) ?: Float.NaN)
                }
            }
        }
        return bytes.toByteArray()
    }

    /** 형식(MAGIC·버전·종류·필드 수)이 맞지 않으면 null. 손상된 데이터는 예외를 던진다(호출자가 처리). */
    fun <C> decode(bytes: ByteArray, kind: ForecastSnapshotKind, codec: GridCellCodec<C>): ForecastSnapshot<C>? {
        DataInputStream(BufferedInputStream(GZIPInputStream(ByteArrayInputStream(bytes)), 64 * 1024)).use { inp ->
            if (inp.readInt() != MAGIC) return null
            if (inp.readInt() != SCHEMA_VERSION) return null
            if (inp.readUTF() != kind.name) return null
            val tmfc = inp.readUTF()
            val savedAt = Instant.ofEpochMilli(inp.readLong())
            if (inp.readInt() != codec.fieldCount) return null
            val slotCount = inp.readInt()
            require(slotCount in 0..MAX_SLOTS) { "slot 수 이상: $slotCount" }

            val slots = LinkedHashMap<String, MutableList<MutableList<C>>>(slotCount)
            repeat(slotCount) {
                val slot = inp.readUTF()
                val rows = inp.readInt()
                require(rows in 0..MAX_DIM) { "행 수 이상: $rows" }
                val cols = IntArray(rows) { inp.readInt().also { c -> require(c in 0..MAX_DIM) { "열 수 이상: $c" } } }
                val total = cols.sum()
                val values = Array(codec.fieldCount) { FloatArray(total) }
                for (f in 0 until codec.fieldCount) {
                    val arr = values[f]
                    for (i in 0 until total) arr[i] = inp.readFloat()
                }
                val buf = FloatArray(codec.fieldCount)
                var idx = 0
                val grid = MutableList(rows) { r ->
                    MutableList(cols[r]) {
                        for (f in 0 until codec.fieldCount) buf[f] = values[f][idx]
                        idx++
                        codec.cell(buf)
                    }
                }
                slots[slot] = grid
            }
            return ForecastSnapshot(tmfc, savedAt, slots)
        }
    }
}
