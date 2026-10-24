package cn.xinghuaerp.ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 工具令牌是「Python 回调时证明发起人」的唯一依据，必须可校验、可过期、不可伪造。 */
class ToolTokenTest {

    private final ToolToken tokens = new ToolToken("t".repeat(32));

    @Test
    void issuedTokenRoundTripsToUserId() {
        assertEquals(42L, tokens.verify(tokens.issue(42L)));
    }

    @Test
    void rejectsTamperedUserId() {
        String token = tokens.issue(42L);
        String forged = "43" + token.substring(2);
        assertThrows(IllegalArgumentException.class, () -> tokens.verify(forged));
    }

    @Test
    void rejectsTamperedSignature() {
        String token = tokens.issue(42L);
        assertThrows(IllegalArgumentException.class, () -> tokens.verify(token.substring(0, token.length() - 1) + "x"));
    }

    @Test
    void rejectsExpiredOrMalformedToken() {
        assertThrows(IllegalArgumentException.class, () -> tokens.verify(""));
        assertThrows(IllegalArgumentException.class, () -> tokens.verify("42.abc.sig"));
        assertThrows(IllegalArgumentException.class, () -> tokens.verify("42.1.sig"));
        assertThrows(IllegalArgumentException.class, () -> tokens.verify("0.99999999999999.sig"));
    }

    @Test
    void differentSecretCannotVerify() {
        String token = tokens.issue(7L);
        ToolToken other = new ToolToken("u".repeat(32));
        assertThrows(IllegalArgumentException.class, () -> other.verify(token));
    }
}
