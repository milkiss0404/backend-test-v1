package im.bigs.pg.application.partner.port.out

import im.bigs.pg.domain.partner.FeePolicy
import java.time.LocalDateTime

/**
 * 지정 시점(at)에 유효한 수수료 정책 조회용 출력 포트.
 * - 동일 파트너의 여러 정책 중 가장 최근(effectiveFrom DESC) 항목을 선택합니다.
 */
interface FeePolicyOutPort {
    fun findEffectivePolicy(partnerId: Long, at: LocalDateTime = LocalDateTime.now()): FeePolicy?

    fun findByPartnerId(partnerId: Long): FeePolicy ?
}
