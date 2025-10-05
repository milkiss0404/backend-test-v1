package im.bigs.pg.external.pg

import im.bigs.pg.external.pg.SimulatedPgClient
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 여러 PG 클라이언트를 순차적으로 시도하는 대체용 클라이언트
 */
@Component
class MultiAttemptPgClient(
    private val primaryClient: TestPgAdapter,
    private val secondaryClient: SimulatedPgClient
) : PgClientOutPort {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun supports(partnerId: Long): Boolean = partnerId % 2L == 0L

    override fun approve(request: PgApproveRequest): PgApproveResult {
        logger.info("[MultiAttempt] 승인 시작: partnerId={}, amount={}", request.partnerId, request.amount)

        return attempt(primaryClient, request) ?: attempt(secondaryClient, request)
        ?: throw IllegalStateException("모든 PG 클라이언트 실패: partnerId=${request.partnerId}")
    }

    /**
     * 단일 클라이언트 시도
     * 성공 시 결과 반환, 실패 시 null
     */
    private fun attempt(client: PgClientOutPort, req: PgApproveRequest): PgApproveResult? {
        return try {
            val res = client.approve(req)
            logger.info("[MultiAttempt] {} 성공: approvalCode={}", client.javaClass.simpleName, res.approvalCode)
            res
        } catch (ex: Throwable) { // Exception 대신 Throwable
            logger.warn("[MultiAttempt] {} 실패: {}", client.javaClass.simpleName, ex.message)
            null
        }
    }
}
