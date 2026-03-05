package core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HashService 单元测试
 */
class HashServiceTest {

    @Test
    @DisplayName("相同内容应产生相同哈希")
    void sameContent_shouldProduceSameHash() {
        int hash1 = HashService.hash("Hello World");
        int hash2 = HashService.hash("Hello World");
        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("不同内容应产生不同哈希")
    void differentContent_shouldProduceDifferentHash() {
        int hash1 = HashService.hash("Hello");
        int hash2 = HashService.hash("World");
        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("空字符串应产生有效哈希")
    void emptyString_shouldProduceValidHash() {
        int hash = HashService.hash("");
        assertEquals("".hashCode(), hash);
    }

    @Test
    @DisplayName("null 输入应返回 0")
    void nullInput_shouldReturnZero() {
        int hash = HashService.hash(null);
        assertEquals(0, hash);
    }

    @Test
    @DisplayName("哈希值应等于 String.hashCode()")
    void hash_shouldEqualStringHashCode() {
        String content = "test response body";
        assertEquals(content.hashCode(), HashService.hash(content));
    }
}

