package im.bigs.pg.external.error

/** PG 연동 오류 스펙 */
interface PgErrorSpec {
    val code: Int           // 내부 에러 코드
    val errorCode: String   // PG 오류코드 문자열
    val message: String     // 사용자 친화적 메시지
}