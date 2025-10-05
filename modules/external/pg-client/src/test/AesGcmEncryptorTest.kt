package im.bigs.pg.infrastructure.crypto

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import java.security.SecureRandom
import java.util.Base64

@DisplayName("AesGcmEncryptor 테스트")
class AesGcmEncryptorTest {

    private lateinit var encryptor: AesGcmEncryptor

    @BeforeEach
    fun setUp() {
        encryptor = AesGcmEncryptor()
    }

    @Nested
    @DisplayName("암호화 테스트")
    inner class EncryptTest {

        @Test
        @DisplayName("평문 JSON을 성공적으로 암호화한다")
        fun encryptSuccess() {
            // given
            val apiKey = "11111111-1111-4111-8111-111111111111"
            val iv = generateRandomIv()
            val ivBase64Url = encodeBase64UrlNoPadding(iv)

            val plainJson = """
                {
                  "cardNumber": "1111-1111-1111-1111",
                  "birthDate": "19950308",
                  "expiry": "3435",
                  "password": "66",
                  "amount": 10000
                }
            """.trimIndent()

            // when
            val encrypted = encryptor.encrypt(plainJson, apiKey, ivBase64Url)

            // then
            assertThat(encrypted).isNotEmpty()
            assertThat(encrypted).doesNotContain("=") // Base64URL은 패딩 없음
            assertThat(encrypted).doesNotContain("+") // Base64URL은 + 대신 - 사용
            assertThat(encrypted).doesNotContain("/") // Base64URL은 / 대신 _ 사용
        }

        @Test
        @DisplayName("동일한 평문과 키, IV로 암호화하면 동일한 결과가 나온다")
        fun encryptDeterministic() {
            // given
            val apiKey = "11111111-1111-4111-8111-111111111111"
            val iv = generateRandomIv()
            val ivBase64Url = encodeBase64UrlNoPadding(iv)
            val plainText = """{"amount": 10000}"""

            // when
            val encrypted1 = encryptor.encrypt(plainText, apiKey, ivBase64Url)
            val encrypted2 = encryptor.encrypt(plainText, apiKey, ivBase64Url)

            // then
            assertThat(encrypted1).isEqualTo(encrypted2)
        }

        @Test
        @DisplayName("다른 IV로 암호화하면 다른 결과가 나온다")
        fun encryptWithDifferentIv() {
            // given
            val apiKey = "11111111-1111-4111-8111-111111111111"
            val iv1 = generateRandomIv()
            val iv2 = generateRandomIv()
            val ivBase64Url1 = encodeBase64UrlNoPadding(iv1)
            val ivBase64Url2 = encodeBase64UrlNoPadding(iv2)
            val plainText = """{"amount": 10000}"""

            // when
            val encrypted1 = encryptor.encrypt(plainText, apiKey, ivBase64Url1)
            val encrypted2 = encryptor.encrypt(plainText, apiKey, ivBase64Url2)

            // then
            assertThat(encrypted1).isNotEqualTo(encrypted2)
        }

        @Test
        @DisplayName("잘못된 IV 길이로 암호화하면 예외가 발생한다")
        fun encryptWithInvalidIvLength() {
            // given
            val apiKey = "11111111-1111-4111-8111-111111111111"
            val invalidIv = ByteArray(16) // 12바이트가 아닌 16바이트
            val ivBase64Url = encodeBase64UrlNoPadding(invalidIv)
            val plainText = """{"amount": 10000}"""

            // when & then
            assertThatThrownBy {
                encryptor.encrypt(plainText, apiKey, ivBase64Url)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("12바이트")
        }
    }

    @Nested
    @DisplayName("복호화 테스트")
    inner class DecryptTest {

        @Test
        @DisplayName("암호화된 데이터를 성공적으로 복호화한다")
        fun decryptSuccess() {
            // given
            val apiKey = "11111111-1111-4111-8111-111111111111"
            val iv = generateRandomIv()
            val ivBase64Url = encodeBase64UrlNoPadding(iv)

            val plainJson = """
                {
                  "cardNumber": "1111-1111-1111-1111",
                  "birthDate": "19900101",
                  "expiry": "1227",
                  "password": "12",
                  "amount": 10000
                }
            """.trimIndent()

            val encrypted = encryptor.encrypt(plainJson, apiKey, ivBase64Url)

            // when
            val decrypted = encryptor.decrypt(encrypted, apiKey, ivBase64Url)

            // then
            assertThat(decrypted).isEqualTo(plainJson)
        }

        @Test
        @DisplayName("잘못된 API-KEY로 복호화하면 예외가 발생한다")
        fun decryptWithWrongApiKey() {
            // given
            val correctApiKey = "11111111-1111-4111-8111-111111111111"
            val wrongApiKey = "22222222-2222-4222-8222-222222222222"
            val iv = generateRandomIv()
            val ivBase64Url = encodeBase64UrlNoPadding(iv)
            val plainText = """{"amount": 10000}"""

            val encrypted = encryptor.encrypt(plainText, correctApiKey, ivBase64Url)

            // when & then
            assertThatThrownBy {
                encryptor.decrypt(encrypted, wrongApiKey, ivBase64Url)
            }.isInstanceOf(Exception::class.java) // AEADBadTagException 등
        }

        @Test
        @DisplayName("잘못된 IV로 복호화하면 예외가 발생한다")
        fun decryptWithWrongIv() {
            // given
            val apiKey = "11111111-1111-4111-8111-111111111111"
            val correctIv = generateRandomIv()
            val wrongIv = generateRandomIv()
            val correctIvBase64Url = encodeBase64UrlNoPadding(correctIv)
            val wrongIvBase64Url = encodeBase64UrlNoPadding(wrongIv)
            val plainText = """{"amount": 10000}"""

            val encrypted = encryptor.encrypt(plainText, apiKey, correctIvBase64Url)

            // when & then
            assertThatThrownBy {
                encryptor.decrypt(encrypted, apiKey, wrongIvBase64Url)
            }.isInstanceOf(Exception::class.java) // AEADBadTagException 등
        }
    }

    @Nested
    @DisplayName("Test PG API 실제 케이스 테스트")
    inner class TestPgApiCaseTest {

        @Test
        @DisplayName("성공 카드로 결제 요청 JSON을 암호화한다")
        fun encryptSuccessCardPayment() {
            // given
            val apiKey = "11111111-1111-4111-8111-111111111111"
            val iv = generateRandomIv()
            val ivBase64Url = encodeBase64UrlNoPadding(iv)

            val plainJson = """{"cardNumber":"1111-1111-1111-1111","birthDate":"19900101","expiry":"1227","password":"12","amount":10000}"""

            // when
            val encrypted = encryptor.encrypt(plainJson, apiKey, ivBase64Url)

            // then
            assertThat(encrypted).isNotEmpty()

            // 복호화하여 검증
            val decrypted = encryptor.decrypt(encrypted, apiKey, ivBase64Url)
            assertThat(decrypted).isEqualTo(plainJson)
        }

        @Test
        @DisplayName("실패 카드로 결제 요청 JSON을 암호화한다")
        fun encryptFailCardPayment() {
            // given
            val apiKey = "22222222-2222-4222-8222-222222222222"
            val iv = generateRandomIv()
            val ivBase64Url = encodeBase64UrlNoPadding(iv)

            val plainJson = """{"cardNumber":"2222-2222-2222-2222","birthDate":"19900101","expiry":"1227","password":"12","amount":60000}"""

            // when
            val encrypted = encryptor.encrypt(plainJson, apiKey, ivBase64Url)

            // then
            assertThat(encrypted).isNotEmpty()

            // 복호화하여 검증
            val decrypted = encryptor.decrypt(encrypted, apiKey, ivBase64Url)
            assertThat(decrypted).isEqualTo(plainJson)
        }

        @Test
        @DisplayName("다양한 금액으로 암호화 테스트")
        fun encryptWithVariousAmounts() {
            // given
            val apiKey = "11111111-1111-4111-8111-111111111111"
            val iv = generateRandomIv()
            val ivBase64Url = encodeBase64UrlNoPadding(iv)

            val amounts = listOf(1000, 10000, 50000, 100000, 1000000)

            amounts.forEach { amount ->
                // when
                val plainJson = """{"cardNumber":"1111-1111-1111-1111","birthDate":"19900101","expiry":"1227","password":"12","amount":$amount}"""
                val encrypted = encryptor.encrypt(plainJson, apiKey, ivBase64Url)
                val decrypted = encryptor.decrypt(encrypted, apiKey, ivBase64Url)

                // then
                assertThat(decrypted).isEqualTo(plainJson)
            }
        }
    }

    @Nested
    @DisplayName("한글 데이터 암호화 테스트")
    inner class KoreanDataTest {

        @Test
        @DisplayName("한글이 포함된 데이터를 암호화/복호화한다")
        fun encryptKoreanText() {
            // given
            val apiKey = "11111111-1111-4111-8111-111111111111"
            val iv = generateRandomIv()
            val ivBase64Url = encodeBase64UrlNoPadding(iv)

            val plainJson = """{"name":"홍길동","message":"결제 성공"}"""

            // when
            val encrypted = encryptor.encrypt(plainJson, apiKey, ivBase64Url)
            val decrypted = encryptor.decrypt(encrypted, apiKey, ivBase64Url)

            // then
            assertThat(decrypted).isEqualTo(plainJson)
        }
    }

    // 헬퍼 메서드
    private fun generateRandomIv(): ByteArray {
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        return iv
    }

    private fun encodeBase64UrlNoPadding(bytes: ByteArray): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }
}

/**
 * 통합 테스트용 예제
 *
 * 실제 API 호출 시나리오를 시뮬레이션하는 테스트
 */
@DisplayName("AesGcmEncryptor 통합 테스트")
class AesGcmEncryptorIntegrationTest {

    private lateinit var encryptor: AesGcmEncryptor

    @BeforeEach
    fun setUp() {
        encryptor = AesGcmEncryptor()
    }

    @Test
    @DisplayName("전체 결제 플로우 암호화/복호화 시나리오")
    fun fullPaymentFlowScenario() {
        // given: API-KEY와 IV가 발급되었다고 가정
        val apiKey = "11111111-1111-4111-8111-111111111111"
        val iv = ByteArray(12).apply {
            SecureRandom().nextBytes(this)
        }
        val ivBase64Url = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(iv)

        // when: 결제 요청 데이터 생성
        val paymentRequest = """
            {
              "cardNumber": "1111-1111-1111-1111",
              "birthDate": "19900101",
              "expiry": "1227",
              "password": "12",
              "amount": 10000
            }
        """.trimIndent()

        // then: 암호화
        val encrypted = encryptor.encrypt(paymentRequest, apiKey, ivBase64Url)

        println("=== 결제 요청 ===")
        println("평문: $paymentRequest")
        println("암호화: $encrypted")
        println("암호문 길이: ${encrypted.length}")

        // and: API 요청 바디 구성
        val requestBody = mapOf("enc" to encrypted)
        println("\nAPI 요청 바디: $requestBody")

        // and: 서버에서 복호화 (검증용)
        val decrypted = encryptor.decrypt(encrypted, apiKey, ivBase64Url)
        println("\n복호화: $decrypted")

        // verify
        assertThat(decrypted).isEqualTo(paymentRequest)
    }
}