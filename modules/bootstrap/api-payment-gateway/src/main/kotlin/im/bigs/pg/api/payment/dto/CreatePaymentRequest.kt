package im.bigs.pg.api.payment.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import java.math.BigDecimal

data class CreatePaymentRequest(
    @field:Schema(description = "제휴사 ID", example = "1")
    val partnerId: Long,

    @field:Min(1)
    @field:Schema(description = "결제 금액", example = "10000")
    val amount: BigDecimal,

    @field:Schema(description = "카드 BIN", example = "123456")
    val cardBin: String? = null,

    @field:Schema(description = "카드 마지막 4자리", example = "1234")
    val cardLast4: String? = null,

    @field:Schema(description = "상품명", example = "테스트 상품")
    val productName: String? = null,
)