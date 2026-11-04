package com.project.byeoldori.community.common.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * StorageService 공통 규칙: 우리 스토리지가 발급한 URL 인지(경로 탈출 차단), 썸네일 경로 유도.
 */
class StorageServiceUrlRulesTest {

    private val prefix = "https://api.byeoldori.com/files/"

    @Test
    fun `prefix 아래의 정상 경로는 허용`() {
        assertThat(StorageService.isUnderPrefix("${prefix}images/2026/10/20/abc-123.jpeg", prefix)).isTrue()
        assertThat(StorageService.isUnderPrefix("${prefix}json/2026/10/20/a_b.json", prefix)).isTrue()
        assertThat(StorageService.isUnderPrefix("${prefix}a", prefix)).isTrue()
    }

    @Test
    fun `prefix 가 다르거나 대소문자가 다르면 거부`() {
        assertThat(StorageService.isUnderPrefix("https://evil.example/files/images/a.jpeg", prefix)).isFalse()
        assertThat(StorageService.isUnderPrefix("https://API.byeoldori.com/files/images/a.jpeg", prefix)).isFalse()
        assertThat(StorageService.isUnderPrefix("http://api.byeoldori.com/files/images/a.jpeg", prefix)).isFalse()
        assertThat(StorageService.isUnderPrefix("", prefix)).isFalse()
    }

    @Test
    fun `prefix 만 있고 경로가 비면 거부`() {
        assertThat(StorageService.isUnderPrefix(prefix, prefix)).isFalse()
    }

    @Test
    fun `경로 탈출 문자는 거부`() {
        assertThat(StorageService.isUnderPrefix("${prefix}../other-bucket/a.jpeg", prefix)).isFalse()
        assertThat(StorageService.isUnderPrefix("${prefix}images/../../a.jpeg", prefix)).isFalse()
        assertThat(StorageService.isUnderPrefix("${prefix}./a.jpeg", prefix)).isFalse()
        assertThat(StorageService.isUnderPrefix("${prefix}images//a.jpeg", prefix)).isFalse()
        assertThat(StorageService.isUnderPrefix("${prefix}images/a.jpeg/", prefix)).isFalse()
    }

    @Test
    fun `퍼센트 인코딩·쿼리·프래그먼트·공백·백슬래시는 거부`() {
        assertThat(StorageService.isUnderPrefix("${prefix}%2e%2e/a.jpeg", prefix)).isFalse()
        assertThat(StorageService.isUnderPrefix("${prefix}images/a.jpeg?x=1", prefix)).isFalse()
        assertThat(StorageService.isUnderPrefix("${prefix}images/a.jpeg#frag", prefix)).isFalse()
        assertThat(StorageService.isUnderPrefix("${prefix}images/a b.jpeg", prefix)).isFalse()
        assertThat(StorageService.isUnderPrefix("${prefix}images\\a.jpeg", prefix)).isFalse()
        assertThat(StorageService.isUnderPrefix("${prefix}images/한글.jpeg", prefix)).isFalse()
    }

    @Test
    fun `점이 들어간 파일명은 허용하되 점만으로 된 세그먼트는 거부`() {
        assertThat(StorageService.isUnderPrefix("${prefix}images/a.b.c.jpeg", prefix)).isTrue()
        assertThat(StorageService.isUnderPrefix("${prefix}images/.hidden", prefix)).isTrue()
        assertThat(StorageService.isUnderPrefix("${prefix}images/...", prefix)).isTrue() // "..." 은 상위 디렉터리가 아니다
        assertThat(StorageService.isUnderPrefix("${prefix}images/..", prefix)).isFalse()
    }

    @Test
    fun `썸네일 경로는 같은 날짜·같은 uuid 의 jpg`() {
        assertThat(StorageService.thumbnailPathOf("2026/10/20/0f1e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b.jpeg"))
            .isEqualTo("thumbnails/2026/10/20/0f1e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b.jpg")
        assertThat(StorageService.thumbnailPathOf("2026/10/20/0F1E2D3C-4B5A-6978-8A9B-0C1D2E3F4A5B.png"))
            .isEqualTo("thumbnails/2026/10/20/0F1E2D3C-4B5A-6978-8A9B-0C1D2E3F4A5B.jpg")
        assertThat(StorageService.thumbnailPathOf("2026/01/02/0f1e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b.webp")).isNotNull()
        assertThat(StorageService.thumbnailPathOf("2026/01/02/0f1e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b.gif")).isNotNull()
    }

    @Test
    fun `이미지 형식이 아니면 썸네일 경로 없음`() {
        val uuid = "0f1e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b"
        assertThat(StorageService.thumbnailPathOf("2026/10/20/$uuid.json")).isNull()
        assertThat(StorageService.thumbnailPathOf("2026/10/20/$uuid.pdf")).isNull()
        assertThat(StorageService.thumbnailPathOf("2026/10/20/$uuid.JPEG")).isNull()  // 확장자는 소문자(스니핑 결과)만
        assertThat(StorageService.thumbnailPathOf("2026/10/20/$uuid.jpg")).isNull()   // 원본은 jpeg 로 저장되므로 jpg 는 썸네일 자신
        assertThat(StorageService.thumbnailPathOf("thumbnails/2026/10/20/$uuid.jpg")).isNull()
        assertThat(StorageService.thumbnailPathOf("images/2026/10/20/$uuid.jpeg")).isNull() // images/ 접두어는 호출부가 떼야 한다
        assertThat(StorageService.thumbnailPathOf("2026/10/20/not-a-uuid.jpeg")).isNull()
        assertThat(StorageService.thumbnailPathOf("2026/10/2/$uuid.jpeg")).isNull()
    }
}
