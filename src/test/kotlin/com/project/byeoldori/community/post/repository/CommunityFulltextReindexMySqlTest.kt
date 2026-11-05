package com.project.byeoldori.community.post.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * V8 의 FULLTEXT 재색인이 실제 MySQL 8.0 에서 불용어를 끈 상태로 다시 만들어지는지 확인한다.
 * 불용어 상태는 테이블 단위로 남아 FULLTEXT 인덱스를 모두 지워야 초기화되므로, V8 의 문장 순서가 중요하다.
 * H2 로는 재현할 수 없어 Docker(Testcontainers)가 있는 CI 에서만 돈다.
 */
@Testcontainers(disabledWithoutDocker = true)
class CommunityFulltextReindexMySqlTest {

    companion object {
        @Container
        @JvmStatic
        val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withDatabaseName("byeoldori")
            .withUsername("root")
            .withPassword("test")
    }

    private fun connect(): Connection = DriverManager.getConnection(mysql.jdbcUrl, mysql.username, mysql.password)

    private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }

    private fun Connection.count(sql: String): Int =
        createStatement().use { st -> st.executeQuery(sql).use { rs -> rs.next(); rs.getInt(1) } }

    @Test
    fun `불용어를 끄고 V8 을 돌리면 영문 제목·본문이 검색되고 테이블 FTS 설정도 use_stopword=0 이 된다`() {
        // 운영과 같은 출발점: 불용어 켜진 채 V3 로 만든 인덱스 + 이미 MATCH 질의로 FTS 캐시가 초기화된 상태
        connect().use { c ->
            c.exec("CREATE TABLE community (id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255) NOT NULL, content TEXT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4")
            c.exec("ALTER TABLE `community` ADD FULLTEXT INDEX `ft_community_title` (`title`) WITH PARSER ngram")
            c.exec("ALTER TABLE `community` ADD FULLTEXT INDEX `ft_community_content` (`content`) WITH PARSER ngram")
            c.exec("INSERT INTO community (title, content) VALUES ('Orion', 'Orion nebula')")
            c.count("SELECT COUNT(*) FROM community WHERE MATCH(title) AGAINST ('\"orion\"' IN BOOLEAN MODE)")
            c.exec("SET GLOBAL innodb_ft_enable_stopword = OFF")
        }

        // V8 파일의 FULLTEXT 문장을 파일 순서 그대로 실행(새 연결 = 세션 값이 GLOBAL OFF 를 따른다)
        val statements = File("src/main/resources/db/migration/V8__community_mid_forecast_indexes_ngram_reindex.sql").readLines()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n").split(";").map { it.trim() }
            .filter { it.contains("ft_community_") }
        assertThat(statements).hasSize(4)
        connect().use { c -> statements.forEach { c.exec(it) } }

        connect().use { c ->
            assertThat(c.count("SELECT COUNT(*) FROM community WHERE MATCH(title) AGAINST ('\"orion\"' IN BOOLEAN MODE)")).isEqualTo(1)
            assertThat(c.count("SELECT COUNT(*) FROM community WHERE MATCH(content) AGAINST ('\"orion\"' IN BOOLEAN MODE)")).isEqualTo(1)

            c.exec("SET GLOBAL innodb_ft_aux_table = 'byeoldori/community'")
            val useStopword = c.createStatement().use { st ->
                st.executeQuery("SELECT VALUE FROM INFORMATION_SCHEMA.INNODB_FT_CONFIG WHERE `KEY` = 'use_stopword'")
                    .use { rs -> if (rs.next()) rs.getString(1) else null }
            }
            assertThat(useStopword).isEqualTo("0")
        }
    }
}
