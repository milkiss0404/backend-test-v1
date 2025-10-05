package im.bigs.pg.external.error

/** 시뮬레이터에서 발생시키는 PG 에러 정의 */
enum class SimulatedPgErrors(
    override val code: Int,
    override val errorCode: String,
    override val message: String
) : PgErrorSpec {

    STOLEN_CARD(1001, "STOLEN_CARD", "분실 카드"),
    LIMIT_EXCEEDED(1002, "LIMIT_EXCEEDED", "승인 한도를 초과했습니다."),
    EXPIRED(1003, "EXPIRED", "만료된 카드"),
    TAMPERED(1004, "TAMPERED", "변조된 카드");
}