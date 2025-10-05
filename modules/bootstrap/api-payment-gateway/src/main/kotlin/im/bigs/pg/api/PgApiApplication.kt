package im.bigs.pg.api

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.servers.Server

/**
 * API 실행 진입점. bootstrap 모듈은 실행/환경설정만을 담당합니다.
 */



@OpenAPIDefinition(
    info = Info(
        title = "빅스페이먼츠 API",
        version = "1.0",
        description = "백엔드 사전 과제 – API 문서"
    ),
    servers = [
        Server(url = "http://localhost:8080", description = "local server")
    ]
)
@SpringBootApplication(scanBasePackages = ["im.bigs.pg"])
class PgApiApplication

fun main(args: Array<String>) {
    runApplication<PgApiApplication>(*args)
}