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
    @Suppress("UNCHECKED_CAST")
    fun `알림 룰에 Cloudflare 엣지 5xx 비율 경보가 있다`() {
        val groups = yaml("monitoring/grafana/provisioning/alerting/alerting.yml")["groups"] as List<Map<String, Any?>>
        val rule = groups.flatMap { it["rules"] as List<Map<String, Any?>> }.first { it["uid"] == "cf-edge-5xx" }
        val data = rule["data"] as List<Map<String, Any?>>
        val expr = data.first { it["refId"] == "A" }.obj("model")["expr"] as String
        assertThat(expr).contains("cloudflare_zone_requests_status{zone=\"byeoldori.com\",status=~\"5..\"}")
        // exporter 는 계정의 모든 존을 수집하므로 분자·분모·최소 요청 조건 모두 byeoldori.com 존으로 한정해야 한다
        val selectors = Regex("cloudflare_zone_requests_status(\\{[^}]*\\})?").findAll(expr).map { it.value }.toList()
        assertThat(selectors).hasSize(3).allSatisfy { assertThat(it).contains("zone=\"byeoldori.com\"") }
        val dashboard = File("monitoring/grafana/dashboards/frontend-cf.json").readText()
        assertThat(dashboard).doesNotContain("cloudflare_zone_requests_status[")
        val evaluator = (data.first { it["refId"] == "C" }.obj("model")["conditions"] as List<Map<String, Any?>>)
            .first().obj("evaluator")
        assertThat(evaluator["type"]).isEqualTo("gt")
        assertThat((evaluator["params"] as List<Number>).first().toDouble()).isEqualTo(0.05)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `Cloudflare exporter 수집이 멈추면 알린다(엣지 5xx 룰은 데이터가 없으면 OK 라 감시가 꺼진 걸 모른다)`() {
        val groups = yaml("monitoring/grafana/provisioning/alerting/alerting.yml")["groups"] as List<Map<String, Any?>>
        val rule = groups.flatMap { it["rules"] as List<Map<String, Any?>> }.first { it["uid"] == "cf-exporter-down" }
        // exporter 컨테이너가 죽으면 시계열이 사라지므로 NoData 도 알림이어야 한다
        assertThat(rule["noDataState"]).isEqualTo("Alerting")
        val data = rule["data"] as List<Map<String, Any?>>
        val expr = data.first { it["refId"] == "A" }.obj("model")["expr"] as String
        assertThat(expr).contains("max_over_time(cloudflare_worker_exporter_up[15m])")
        val evaluator = (data.first { it["refId"] == "C" }.obj("model")["conditions"] as List<Map<String, Any?>>)
            .first().obj("evaluator")
        assertThat(evaluator["type"]).isEqualTo("lt")
        assertThat((evaluator["params"] as List<Number>).first().toDouble()).isEqualTo(1.0)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `모니터링 반영 스크립트는 단일 파일로 마운트한 설정을 모두 해시해 바뀐 서비스만 재생성한다`() {
        val script = File("scripts/reload-monitoring.sh").readText()
        assertThat(script).contains("--force-recreate", "timeout \"\$LIMIT\"")
        val services = yaml("docker-compose.monitoring.yml").obj("services")
        // ./monitoring/ 아래 파일·폴더 바인드 마운트(Grafana 대시보드 JSON 은 provider 가 주기적으로 다시 읽어 제외)
        val mounts = services.values.flatMap { svc -> ((svc as Map<String, Any?>)["volumes"] as List<String>?).orEmpty() }
            .map { it.substringBefore(":") }
            .filter { it.startsWith("./monitoring/") && it != "./monitoring/grafana/dashboards" }
            .map { it.removePrefix("./") }
        assertThat(mounts).isNotEmpty.allSatisfy { assertThat(script).contains(it) }

        val deploy = yaml(".github/workflows/deploy.yml").obj("jobs").obj("deploy")["steps"] as List<Map<String, Any?>>
        val remote = deploy.mapNotNull { (it["with"] as Map<String, Any?>?)?.get("script") as String? }.joinToString("\n")
        // 실패를 삼키지 않고 배포 경고로 남긴다(배포 자체는 계속)
        assertThat(remote).contains("bash scripts/reload-monitoring.sh || DEPLOY_WARN=")
        assertThat(remote).doesNotContain("docker restart grafana")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `배포는 백업 cron 설치 실패와 백업 신선도 문제를 경고로 알리고 매일 외부에서도 백업을 확인한다`() {
        val deploy = yaml(".github/workflows/deploy.yml").obj("jobs").obj("deploy")["steps"] as List<Map<String, Any?>>
        val remote = deploy.mapNotNull { (it["with"] as Map<String, Any?>?)?.get("script") as String? }.joinToString("\n")
        assertThat(remote).doesNotContain("install-backup-cron.sh || true", "reload-monitoring.sh || true")
        assertThat(remote).contains(
            "bash scripts/install-backup-cron.sh || DEPLOY_WARN=",
            "bash scripts/check-backup-freshness.sh",
            "if [ -n \"\$DEPLOY_WARN\" ]",
        )

        val check = yaml(".github/workflows/backup-check.yml")
        // YAML 1.1(SnakeYAML)은 on: 키를 Boolean true 로 읽는다
        val on = ((check as Map<Any?, Any?>)[true] ?: check["on"]) as Map<String, Any?>
        assertThat(on).containsKey("schedule")
        val steps = check.obj("jobs").obj("check")["steps"] as List<Map<String, Any?>>
        assertThat(steps.mapNotNull { (it["with"] as Map<String, Any?>?)?.get("script") as String? })
            .anySatisfy { assertThat(it).contains("scripts/check-backup-freshness.sh") }
        assertThat(steps.mapNotNull { it["if"] as String? }).anySatisfy { assertThat(it).contains("failure()") }
    }

    @Test
    fun `Cloudflare exporter 는 재시작 시 과거 구간을 다시 더하지 않고 감시 존이 없으면 실패로 본다`() {
        val env = yaml("docker-compose.monitoring.yml").obj("services").obj("cloudflare-exporter").obj("environment")
        assertThat(env["BACKFILL_MINUTES"]).isEqualTo("0")
        assertThat(env["WATCH_ZONES"] as String).contains("byeoldori.com")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `진행 중인 배포는 취소하지 않고 VM 스크립트는 잠금으로 겹치지 않으며 블루그린은 SIGPIPE 로 trap 을 건너뛰지 않는다`() {
        val workflow = yaml(".github/workflows/deploy.yml")
        assertThat(workflow.obj("concurrency")["cancel-in-progress"]).isEqualTo(false)
        val steps = workflow.obj("jobs").obj("deploy")["steps"] as List<Map<String, Any?>>
        val remote = steps.mapNotNull { (it["with"] as Map<String, Any?>?)?.get("script") as String? }.joinToString("\n")
        assertThat(remote).contains("flock -w 1200 9")
        assertThat(remote.indexOf("flock -w")).isLessThan(remote.indexOf("tar -xzf"))

        val script = File("deploy-bluegreen.sh").readText()
        assertThat(script).contains("trap '' PIPE")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `외부 업타임 체크는 API 와 웹(byeoldori_com)을 모두 보고 각각 Discord 로 알린다`() {
        val steps = yaml(".github/workflows/uptime.yml").obj("jobs").obj("check")["steps"] as List<Map<String, Any?>>
        val web = steps.first { it["id"] == "web" }
        assertThat(web["if"] as String).contains("!cancelled()")
        assertThat(web.obj("env")["WEB_URLS"] as String).contains("https://byeoldori.com/")
        assertThat(steps.mapNotNull { it["if"] as String? })
            .anySatisfy { assertThat(it).contains("steps.api.outcome == 'failure'") }
            .anySatisfy { assertThat(it).contains("steps.web.outcome == 'failure'") }
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
    @Suppress("UNCHECKED_CAST")
    fun `서버 배포는 CI 와 같은 테스트가 통과해야 진행된다`() {
        val jobs = yaml(".github/workflows/deploy.yml").obj("jobs")
        assertThat(jobs.obj("deploy")["needs"]).isEqualTo("test")
        val runs = (jobs.obj("test")["steps"] as List<Map<String, Any?>>).mapNotNull { it["run"] as String? }.joinToString("\n")
        assertThat(runs).contains("./gradlew test --no-daemon", "scripts/tests/bluegreen_test.sh",
            "scripts/tests/backup_test.sh", "scripts/tests/cf_exporter_test.py", "scripts/tests/monitoring_reload_test.sh")
    }

    @Test
    fun `블루그린 스크립트는 예보 캐시 적재(warm 그룹)를 상한을 두고 기다리고 그 경로는 인증 없이 열려 있다`() {
        val script = File("deploy-bluegreen.sh").readText()
        assertThat(script).contains("127.0.0.1:\$PORT/actuator/health/warm")
        assertThat(script).contains("seq 1 \"\${BG_WARM_TRIES:-60}\"")
        // 경로가 인증 뒤에 있으면 매 배포가 401 로 상한(5분)까지 기다린 뒤에야 전환된다
        val security = File("src/main/kotlin/com/project/byeoldori/config/SecurityConfig.kt").readText()
        assertThat(security).contains("\"/actuator/health/warm\",")
    }

    @Test
    fun `구 색 정지는 앱 graceful shutdown(30초)보다 길게 기다린다`() {
        val app = yaml("docker-compose.oci.yml").obj("services").obj("app")
        assertThat(app["stop_grace_period"]).isEqualTo("35s")

        val props = Properties().apply {
            File("src/main/resources/application.properties").inputStream().use { load(it) }
        }
        assertThat(props.getProperty("server.shutdown")).isEqualTo("graceful")
        assertThat(props.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("30s")

        val script = File("deploy-bluegreen.sh").readText()
        assertThat(script).contains("docker stop -t 35 app-\$OLD")
        assertThat(script).doesNotContain("docker stop app-\$OLD")
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
