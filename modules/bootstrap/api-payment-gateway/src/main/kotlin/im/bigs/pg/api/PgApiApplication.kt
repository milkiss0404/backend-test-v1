package im.bigs.pg.api

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

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
class PgApiApplication {

    fun main(args: Array<String>) {
        runApplication<PgApiApplication>(*args)
    }

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate().apply {
            requestFactory = HttpComponentsClientHttpRequestFactory().apply {
                setConnectTimeout(60000) // 연결 타임아웃 60초
                setReadTimeout(60000)    // 읽기 타임아웃 60초
            }
        }
    }
}
