import kotlin.math.*

fun latLonToGrid(lat: Double, lon: Double): Pair<Int, Int> {
    require(!lat.isNaN() && !lon.isNaN()) { "위도 또는 경도 값이 NaN입니다. lat=$lat, lon=$lon" }
    require(lat in -90.0..90.0) { "위도 값이 범위를 벗어났습니다. lat=$lat" }
    require(lon in -180.0..180.0) { "경도 값이 범위를 벗어났습니다. lon=$lon" }

    // PDF에 명시된 상수 값들
    val RE = 6371.00877       // 지구 반경 (km)
    val GRID = 5.0            // 격자 간격 (km)
    val SLAT1 = 30.0          // 표준 위도1 (degree)
    val SLAT2 = 60.0          // 표준 위도2 (degree)
    val OLON = 126.0          // 기준 경도 (degree)
    val OLAT = 38.0           // 기준 위도 (degree)
    val XO = 43               // 기준 격자 X좌표
    val YO = 136              // 기준 격자 Y좌표

    // 각도 변환: degree -> radian
    val DEGRAD = PI / 180.0

    // 투영을 위한 파라미터 준비
    val re = RE / GRID
    val slat1Rad = SLAT1 * DEGRAD
    val slat2Rad = SLAT2 * DEGRAD
    val olonRad = OLON * DEGRAD
    val olatRad = OLAT * DEGRAD

    // 경사각(sn) 계산
    val temp = tan(PI * 0.25 + slat2Rad * 0.5) / tan(PI * 0.25 + slat1Rad * 0.5)
    val sn = ln(cos(slat1Rad) / cos(slat2Rad)) / ln(temp)

    // 스케일 팩터(sf) 계산
    val sf = (tan(PI * 0.25 + slat1Rad * 0.5).pow(sn) * cos(slat1Rad)) / sn

    // 기준점에서의 반경(ro) 계산
    val ro = re * sf / (tan(PI * 0.25 + olatRad * 0.5).pow(sn))

    // 입력 위경도를 radian으로 변환 후 반경(ra) 계산
    val latRad = lat * DEGRAD
    val ra = re * sf / (tan(PI * 0.25 + latRad * 0.5).pow(sn))

    // 경도 차이 및 보정
    var theta = lon * DEGRAD - olonRad
    if (theta > PI) theta -= 2.0 * PI
    if (theta < -PI) theta += 2.0 * PI
    theta *= sn

    // 격자 좌표 (x, y) 계산
    var x = ra * sin(theta) + XO
    var y = ro - ra * cos(theta) + YO

    // 정수형으로 반올림
    val gridX = x.roundToInt()
    val gridY = y.roundToInt()

    // 격자 범위 체크 (가로: 1 ~ 149, 세로: 1 ~ 253)
    if (gridX !in 1..149 || gridY !in 1..253) {
        throw IllegalArgumentException("입력한 위경도가 격자 범위를 벗어났습니다.")
    }

    return Pair(gridX, gridY)
}

fun main() {
    try {
        val (x, y) = latLonToGrid(37.5, 127.0) // 테스트용 위경도
        println("격자 좌표 (x, y): ($x, $y)")
    } catch (e: IllegalArgumentException) {
        println("오류: ${e.message}")
    }
    val (x1 , y1) = latLonToGrid(36.6256271, 127.4541687)
    println("격자 좌표 (x , y): ($x1, $y1)")

    val (x2 , y2 ) = latLonToGrid(37.56844167320551, 126.98164676467968)
    println("격자 좌표 (x , y): ($x2, $y2)")
}
