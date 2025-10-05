package im.bigs.pg.external.pg


import com.fasterxml.jackson.databind.ObjectMapper
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.payment.PaymentStatus
import im.bigs.pg.external.error.PgErrorSpec
import im.bigs.pg.external.error.SimulatedPgErrors
import im.bigs.pg.external.pg.AesGcmEncryptor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import java.util.UUID
import org.apache.hc.core5.http.HttpStatus
import java.time.LocalDateTime
import java.time.ZoneOffset


@Component
class SimulatedPgClient(
    private val json: ObjectMapper,
    private val crypto: AesGcmEncryptor
) : PgClientOutPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(partnerId: Long): Boolean = partnerId % 2L == 0L

    override fun approve(request: PgApproveRequest): PgApproveResult {
        log.info("[PG-SIM] partnerId={}, amount={}", request.partnerId, request.amount)

        val apiKey = apiKeyFor(request.partnerId)
        val iv = ivFor(request.partnerId)

        val cardNumber = buildCardNumber(request)
        validateCard(cardNumber, request.partnerId)

        // 실패 카드 처리
        if (cardNumber.startsWith("2222")) {
            val err = resolveErrorForAmount(request.amount.toLong())
            if (err != null) throwPgError(err)
        }

        // 성공 응답
        return successResponse(request, apiKey, iv)
    }

    private fun buildCardNumber(req: PgApproveRequest): String =
        "${req.cardBin}-${req.cardLast4}"

    private fun validateCard(card: String, partnerId: Long) {
        if (partnerId < 0) {
            throw HttpClientErrorException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Invalid partner id")
        }
        if (!(card == "1111-1111" || card == "2222-2222")) {
            throw IllegalArgumentException("지원하지 않는 카드번호: $card")
        }
    }

    private fun resolveErrorForAmount(amount: Long): PgErrorSpec? =
        when (amount) {
            in 0..50_000L -> null
            51_000L -> SimulatedPgErrors.STOLEN_CARD
            52_000L -> SimulatedPgErrors.EXPIRED
            53_000L -> SimulatedPgErrors.TAMPERED
            else -> SimulatedPgErrors.LIMIT_EXCEEDED
        }

    private fun throwPgError(error: PgErrorSpec): Nothing {
        val body = json.writeValueAsString(
            mapOf(
                "code" to error.code,
                "errorCode" to error.errorCode,
                "message" to error.message,
                "referenceId" to UUID.randomUUID().toString()
            )
        )
        throw HttpClientErrorException.create(
            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
            "PG Simulation Error",
            org.springframework.http.HttpHeaders(),
            body.toByteArray(),
            Charsets.UTF_8
        )
    }

    private fun successResponse(req: PgApproveRequest, apiKey: String, iv: String): PgApproveResult {
        val approvalCode = generateApprovalCode()
        val now = LocalDateTime.now(ZoneOffset.UTC)

        val response = mapOf(
            "approvalCode" to approvalCode,
            "approvedAt" to now.toString(),
            "maskedCardLast4" to (req.cardLast4 ?: "1111"),
            "amount" to req.amount,
            "status" to "APPROVED"
        )

        val encrypted = crypto.encrypt(json.writeValueAsString(response), apiKey, iv)
        log.debug("[PG-SIM] encrypted response size={}", encrypted.length)

        return PgApproveResult(
            approvalCode = approvalCode,
            approvedAt = now,
            status = PaymentStatus.APPROVED
        )
    }

    private fun apiKeyFor(partnerId: Long): String = when (partnerId) {
        -1L -> ""
        -2L -> "INVALID"
        -3L -> "00000000-0000-4000-8000-000000000000"
        else -> "SIMULATED-API-KEY"
    }

    private fun ivFor(partnerId: Long): String = if (partnerId < 0) "" else "c2ltdWxhdGVkLWl2"

    private fun generateApprovalCode(): String = System.nanoTime().toString().takeLast(8)
}
