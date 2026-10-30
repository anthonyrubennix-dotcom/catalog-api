package dev.anix.catalog;

import dev.anix.catalog.auth.Tokens;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokensTest {
    @Test
    void tokensAreRandomAndUrlSafe() {
        String a = Tokens.newToken();
        String b = Tokens.newToken();
        assertNotEquals(a, b);
        assertTrue(a.matches("[A-Za-z0-9_-]{43}"));
    }

    @Test
    void hashIsStableSha256Hex() {
        assertEquals(64, Tokens.hash("x").length());
        assertEquals(Tokens.hash("abc"), Tokens.hash("abc"));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Tokens.hash("abc"));
    }

    @Test
    void parsesBearerHeaderOnly() {
        assertEquals("tok", Tokens.fromHeader("Bearer tok"));
        assertEquals("tok", Tokens.fromHeader("bearer   tok "));
        assertNull(Tokens.fromHeader("Basic dXNlcjpwdw=="));
        assertNull(Tokens.fromHeader("Bearer "));
        assertNull(Tokens.fromHeader(null));
    }
}
