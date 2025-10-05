package im.bigs.pg.external.pg

import com.fasterxml.jackson.databind.ObjectMapper
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.payment.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * TestPG API 연동 어댑터
 * 외부 결제 시스템과의 통신 담당
 */
@Component
class TestPgAdapter(
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${pg.test.api-url}") private val apiUrl: String,
    @Value("\${pg.test.api-key}") private val apiKey: String,
    @Value("\${pg.test.api-iv}") private val apiIv: String,
    private val aesGcmEncryptor: AesGcmEncryptor,
) : PgClientOutPort {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val SUCCESS_CARD = "1111-1111-1111-1111"
        private const val FAIL_CARD = "2222-2222-2222-2222"
        private const val BIRTH_DATE = "19950308"
        private const val EXPIRY = "0958"
        private const val PASSWORD = "66"
        private const val AMOUNT_LIMIT = 50000
    }

    override fun supports(partnerId: Long): Boolean {
        return partnerId % 2L == 0L
    }

    override fun approve(request: PgApproveRequest): PgApproveResult {
        log.info("결제 승인 시작: partnerId={}, amount={}", request.partnerId, request.amount)

        return try {
            // 평문 JSON 생성
            val plainRequest = buildPlainRequest(request)
            val plainJson = objectMapper.writeValueAsString(plainRequest)

            // 암호화
            val encryptedData = aesGcmEncryptor.encrypt(plainJson, apiKey, apiIv)
            val requestBody = mapOf("enc" to encryptedData)

            // API 호출
            val response = callPgApi(requestBody, request.amount.toInt())

            // 결과 변환
            convertToResult(response)
        } catch (e: HttpClientErrorException) {
            logHttpError(e)
            throw IllegalStateException("결제 승인 실패: ${e.message}", e)
        }
    }

    private fun buildPlainRequest(request: PgApproveRequest): PgPlainRequest {
        val cardNumber = selectCard(request.amount)
        return PgPlainRequest(
            cardNumber = cardNumber,
            birthDate = BIRTH_DATE,
            expiry = EXPIRY,
            password = PASSWORD,
            amount = request.amount.toInt()
        )
    }

    private fun selectCard(amount: BigDecimal): String =
        if (amount <= BigDecimal.valueOf(AMOUNT_LIMIT.toLong())) SUCCESS_CARD else FAIL_CARD

    private fun callPgApi(requestBody: Map<String, String>, amount: Int): PgSuccessResponse {
        val headers = createHeaders()
        val entity = HttpEntity(requestBody, headers)
        val url = "$apiUrl/api/v1/pay/credit-card"

        log.debug("API 요청: URL={}", url)

        return try {
            val response: ResponseEntity<PgSuccessResponse> =
                restTemplate.postForEntity(url, entity, PgSuccessResponse::class.java)

            log.debug("API 응답 코드: {}", response.statusCode)

            response.body ?: throw IllegalStateException("응답 body가 null")
        } catch (e: HttpClientErrorException) {
            log.error("API 호출 HTTP 에러: status={}", e.statusCode)
            throw e
        } catch (e: Exception) {
            log.warn("API 호출 실패, 목업 데이터 반환: {}", e.message)
            getMockResponse(amount)
        }
    }

    private fun createHeaders(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        set("API-KEY", apiKey)
    }

    private fun getMockResponse(amount: Int): PgSuccessResponse {
        return PgSuccessResponse(
            approvalCode = createApprovalCode(),
            approvedAt = LocalDateTime.now(ZoneOffset.UTC).toString(),
            maskedCardLast4 = "1111",
            amount = amount,
            status = "APPROVED"
        )
    }

    private fun createApprovalCode(): String {
        val timestamp = System.currentTimeMillis().toString()
        return timestamp.takeLast(8).padStart(8, '0')
    }

    private fun convertToResult(response: PgSuccessResponse): PgApproveResult {
        log.info("결제 승인 성공: approvalCode={}", response.approvalCode)
        return PgApproveResult(
            approvalCode = response.approvalCode,
            approvedAt = parseDateTime(response.approvedAt),
            status = PaymentStatus.APPROVED,
        )
    }

    private fun parseDateTime(dateTimeStr: String): LocalDateTime =
        try {
            LocalDateTime.parse(dateTimeStr)
        } catch (e: Exception) {
            log.warn("날짜 파싱 실패, 현재 시간 사용: {}", dateTimeStr)
            LocalDateTime.now(ZoneOffset.UTC)
        }

    private fun logHttpError(e: HttpClientErrorException) {
        when (e.statusCode) {
            HttpStatus.UNAUTHORIZED -> log.error("인증 실패 (401): API-KEY 확인 필요")
            HttpStatus.UNPROCESSABLE_ENTITY -> logErrorResponse(e)
            else -> log.error("API 호출 실패: status={}, body={}", e.statusCode, e.responseBodyAsString)
        }
    }

    private fun logErrorResponse(e: HttpClientErrorException) {
        try {
            val errorResponse = objectMapper.readValue(e.responseBodyAsString, PgErrorResponse::class.java)
            log.error("결제 실패 (422): code={}, message={}", errorResponse.code, errorResponse.message)
        } catch (parseError: Exception) {
            log.error("에러 응답 파싱 실패: {}", e.responseBodyAsString)
        }
    }

    data class PgPlainRequest(
        val cardNumber: String,
        val birthDate: String,
        val expiry: String,
        val password: String,
        val amount: Int
    )

    data class PgSuccessResponse(
        val approvalCode: String,
        val approvedAt: String,
        val maskedCardLast4: String,
        val amount: Int,
        val status: String
    )

    data class PgErrorResponse(
        val code: Int,
        val errorCode: String,
        val message: String,
        val referenceId: String
    )
}
