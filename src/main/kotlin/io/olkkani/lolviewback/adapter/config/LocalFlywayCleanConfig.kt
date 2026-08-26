package io.olkkani.lolviewback.adapter.config

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * 로컬 개발용: 앱을 띄울 때마다 스키마를 비우고 마이그레이션 + 시드를 전부 재실행한다.
 *
 * docker compose 의 postgres 컨테이너는 정지만 되고 제거되지 않으므로(compose.yaml 참고)
 * 데이터와 flyway_schema_history 가 재시작에도 남는다. 시드(V9.x)는 CURRENT_DATE 기준
 * 상대 날짜를 쓰기 때문에 1회만 실행되면 하루 뒤부터 "오늘/어제" 구간에서 벗어나 무의미해진다.
 * 또 시드는 PK 를 하드코딩한 단순 INSERT 라 비어있지 않은 DB 에 재실행하면 duplicate key 로 깨진다.
 * 따라서 부분 재실행이 아니라 clean 후 전체 migrate 가 맞다.
 *
 * persistence-local 프로필에서만 등록된다. persistence-prod 는 이 빈도 없고
 * spring.flyway.clean-disabled 기본값(true)도 유지되므로 이중으로 막혀 있다.
 */
@Configuration
@Profile("persistence-local")
class LocalFlywayCleanConfig {

    @Bean
    fun cleanMigrateStrategy(): FlywayMigrationStrategy =
        FlywayMigrationStrategy { flyway ->
            flyway.clean()
            flyway.migrate()
        }
}
