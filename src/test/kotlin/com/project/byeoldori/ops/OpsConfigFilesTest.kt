package com.project.byeoldori.ops

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.Properties

/**
 * 운영 배포 설정 파일 회귀 검증 (순수, Spring 컨텍스트 불필요).
 * Gradle 테스트의 작업 디렉터리는 프로젝트 루트이므로 레포 파일을 상대경로로 읽는다.
 */
class OpsConfigFilesTest {

    @Suppress("UNCHECKED_CAST")
    private fun yaml(path: String): Map<String, Any?> =
        File(path).inputStream().use { Yaml().load<Map<String, Any?>>(it) }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.obj(key: String): Map<String, Any?> = this[key] as Map<String, Any?>

    @Test
    fun `운영 app 은 수동 정지한 구 색이 재부팅 후 되살아나지 않도록 unless-stopped 정책을 쓴다`() {
        val app = yaml("docker-compose.oci.yml").obj("services").obj("app")
        assertThat(app["restart"]).isEqualTo("unless-stopped")
    }

    @Test
    fun `운영 compose 는 health 상세 노출 설정을 덮어쓰지 않고 prod 설정은 never 다`() {
        val env = yaml("docker-compose.oci.yml").obj("services").obj("app").obj("environment")
        assertThat(env.keys).doesNotContain("MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS")

        val prod = Properties().apply {
            File("src/main/resources/application-prod.properties").inputStream().use { load(it) }
        }
        assertThat(prod.getProperty("management.endpoint.health.show-details")).isEqualTo("never")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `알림 룰에 디스크 사용률 경보가 있다`() {
        val groups = yaml("monitoring/grafana/provisioning/alerting/alerting.yml")["groups"] as List<Map<String, Any?>>
        val rules = groups.flatMap { it["rules"] as List<Map<String, Any?>> }
        assertThat(rules.map { it["uid"] }).contains("backend-down", "high-error-rate", "disk-high")

        val disk = rules.first { it["uid"] == "disk-high" }
        val data = disk["data"] as List<Map<String, Any?>>
        val expr = data.first { it["refId"] == "A" }.obj("model")["expr"] as String
        assertThat(expr).contains("disk_total_bytes", "disk_free_bytes")
        val conditions = data.first { it["refId"] == "C" }.obj("model")["conditions"] as List<Map<String, Any?>>
        val evaluator = conditions.first().obj("evaluator")
        assertThat(evaluator["type"]).isEqualTo("gt")
        assertThat((evaluator["params"] as List<Number>).first().toDouble()).isEqualTo(0.85)
    }

    @Test
    fun `블루그린 스크립트는 전환 전 실패 시 새 색을 내리고 nginx 검사 실패를 무시하지 않는다`() {
        val script = File("deploy-bluegreen.sh").readText()
        assertThat(script).contains("trap cleanup_new EXIT")
        assertThat(script).contains("docker stop app-\$NEW")
        assertThat(script).contains("docker update --restart=unless-stopped app-\$OLD")
        assertThat(script).doesNotContain("nginx -t &&")
    }

    @Test
    fun `블루그린 스크립트는 서비스 중인 색을 upstream 에서 읽고 전환 실패 시 upstream 을 되돌린다`() {
        val script = File("deploy-bluegreen.sh").readText()
        assertThat(script).contains("grep -oE '127\\.0\\.0\\.1:808[01]' \"\$UPSTREAM_CONF\"")
        assertThat(script).contains("sudo cp -p \"\$UPSTREAM_CONF.bak\" \"\$UPSTREAM_CONF\"")
    }

    @Test
    fun `모니터링 이미지는 latest 가 아니라 운영 검증 버전으로 고정한다`() {
        val services = yaml("docker-compose.monitoring.yml").obj("services")
        val images = services.keys.associateWith { services.obj(it)["image"] as String }
        assertThat(images.values).allSatisfy { image ->
            assertThat(image).doesNotEndWith(":latest").contains(":")
        }
        assertThat(images).containsEntry("grafana", "grafana/grafana-oss:13.0.2")
            .containsEntry("loki", "grafana/loki:3.7.3")
            .containsEntry("alloy", "grafana/alloy:v1.17.1")
            .containsEntry("prometheus", "prom/prometheus:v3.13.1")
    }
}
