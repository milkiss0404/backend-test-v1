package im.bigs.pg.external.pg

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Test PG API 전용 AES-256-GCM 암호화 컴포넌트
 *
 * API 문서 암호화 규격:
 * - 알고리즘: AES-256-GCM (AES/GCM/NoPadding)
 * - 태그 길이: 128비트 (16바이트)
 * - Key: SHA-256(API-KEY) → 32바이트
 * - IV: 12바이트 (96비트), Base64URL로 제공
 * - 출력: Base64URL(ciphertext||tag), 패딩 없음
 */
@Component
class AesGcmEncryptor {

    companion object {
        private const val ALGORITHM = "AES"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128 // 128비트 = 16바이트
        private const val IV_LENGTH = 12 // 12바이트 (96비트)
    }

    /**
     * Test PG API 암호화
     *
     * @param plainText 암호화할 평문 JSON 문자열
     * @param apiKey API-KEY (UUID 형식)
     * @param ivBase64Url Base64URL로 인코딩된 IV (12바이트)
     * @return Base64URL(ciphertext||tag) 패딩 없음
     */
    fun encrypt(plainText: String, apiKey: String, ivBase64Url: String): String {
        // 1. API-KEY를 SHA-256으로 해싱하여 32바이트 키 생성
        val key = generateKeyFromApiKey(apiKey)

        // 2. IV를 Base64URL 디코딩
        val iv = decodeBase64Url(ivBase64Url)
        validateIvLength(iv)

        // 3. AES-256-GCM 암호화
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val secretKey = SecretKeySpec(key, ALGORITHM)
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)

        // 4. 평문을 UTF-8 바이트로 변환하여 암호화
        val plainBytes = plainText.toByteArray(StandardCharsets.UTF_8)
        val encryptedBytes = cipher.doFinal(plainBytes)

        // 5. ciphertext||tag를 Base64URL(패딩 없음)로 인코딩
        return encodeBase64UrlNoPadding(encryptedBytes)
    }

    /**
     * Test PG API 복호화
     *
     * @param encryptedBase64Url Base64URL로 인코딩된 암호문(ciphertext||tag)
     * @param apiKey API-KEY (UUID 형식)
     * @param ivBase64Url Base64URL로 인코딩된 IV (12바이트)
     * @return 복호화된 평문
     */
    fun decrypt(encryptedBase64Url: String, apiKey: String, ivBase64Url: String): String {
        // 1. API-KEY를 SHA-256으로 해싱하여 32바이트 키 생성
        val key = generateKeyFromApiKey(apiKey)

        // 2. IV를 Base64URL 디코딩
        val iv = decodeBase64Url(ivBase64Url)
        validateIvLength(iv)

        // 3. 암호문을 Base64URL 디코딩
        val encryptedBytes = decodeBase64Url(encryptedBase64Url)

        // 4. AES-256-GCM 복호화
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val secretKey = SecretKeySpec(key, ALGORITHM)
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

        val decryptedBytes = cipher.doFinal(encryptedBytes)

        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    /**
     * API-KEY를 SHA-256으로 해싱하여 32바이트 키 생성
     *
     * API 문서 규격: Key = SHA-256(API-KEY)
     */
    private fun generateKeyFromApiKey(apiKey: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(apiKey.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Base64URL 인코딩 (패딩 없음)
     *
     * Base64URL은 URL-safe한 Base64로, +를 -, /를 _로 치환하고 패딩(=)을 제거
     */
    private fun encodeBase64UrlNoPadding(bytes: ByteArray): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    /**
     * Base64URL 디코딩
     */
    private fun decodeBase64Url(encoded: String): ByteArray {
        return Base64.getUrlDecoder().decode(encoded)
    }

    /**
     * IV 길이 검증
     */
    private fun validateIvLength(iv: ByteArray) {
        require(iv.size == IV_LENGTH) {
            "IV는 ${IV_LENGTH}바이트(96비트)여야 합니다. 현재: ${iv.size}바이트"
        }
    }
}