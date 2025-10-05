package im.bigs.pg.infra.persistence.partner.repository

import im.bigs.pg.infra.persistence.partner.entity.FeePolicyEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/** 수수료 정책 조회용 JPA 리포지토리. */
interface FeePolicyJpaRepository : JpaRepository<FeePolicyEntity, Long> {
    /** 지정 시점 이전/동일한 정책 중 가장 최근 것을 반환합니다. */
    fun findTop1ByPartnerIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
        partnerId: Long,
        at: Instant,
    ): FeePolicyEntity?
    fun findByPartnerId(partnerId: Long): FeePolicyEntity?
}
