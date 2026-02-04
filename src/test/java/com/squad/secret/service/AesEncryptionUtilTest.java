package com.squad.secret.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesEncryptionUtilTest {

    private static final String TEST_KEY = "test-encryption-key-32-chars-long";
    private final AesEncryptionUtil util = new AesEncryptionUtil(TEST_KEY);

    @Test
    void 암호화_후_복호화_시_원본_값_반환() {
        String original = "my-secret-password-12345";

        String encrypted = util.encrypt(original);
        String decrypted = util.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void 동일_입력_암호화_시_다른_암호문_생성() {
        String original = "same-input-value";

        String encrypted1 = util.encrypt(original);
        String encrypted2 = util.encrypt(original);

        // IV가 매번 랜덤이므로 암호문은 달라야 함
        assertThat(encrypted1).isNotEqualTo(encrypted2);
        // 복호화하면 둘 다 원본과 동일
        assertThat(util.decrypt(encrypted1)).isEqualTo(original);
        assertThat(util.decrypt(encrypted2)).isEqualTo(original);
    }

    @Test
    void 빈_문자열_암호화_후_복호화_시_빈_문자열_반환() {
        String original = "";

        String encrypted = util.encrypt(original);
        String decrypted = util.decrypt(encrypted);

        assertThat(encrypted).isNotEmpty();
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void 유니코드_문자열_암호화_후_복호화_시_원본_반환() {
        String original = "비밀번호: P@ssw0rd! 한글테스트";

        String encrypted = util.encrypt(original);
        String decrypted = util.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void 잘못된_암호문_복호화_시_예외_발생() {
        assertThatThrownBy(() -> util.decrypt("not-a-valid-base64-ciphertext!@#"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("복호화 실패");
    }

    @Test
    void 잘못된_키로_복호화_시_예외_발생() {
        AesEncryptionUtil otherUtil = new AesEncryptionUtil("completely-different-key-value!!");

        String encrypted = util.encrypt("secret-data");

        assertThatThrownBy(() -> otherUtil.decrypt(encrypted))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("복호화 실패");
    }
}
