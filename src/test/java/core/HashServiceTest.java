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
        String hash1 = HashService.md5("Hello World");
        String hash2 = HashService.md5("Hello World");
        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("不同内容应产生不同哈希")
    void differentContent_shouldProduceDifferentHash() {
        String hash1 = HashService.md5("Hello");
        String hash2 = HashService.md5("World");
        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("空字符串应产生有效哈希")
    void emptyString_shouldProduceValidHash() {
        String hash = HashService.md5("");
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        assertEquals(32, hash.length()); // MD5 hex 长度为 32
    }

    @Test
    @DisplayName("null 字符串输入应返回空字符串")
    void nullStringInput_shouldReturnEmptyString() {
        String hash = HashService.md5((String) null);
        assertEquals("", hash);
    }

    @Test
    @DisplayName("null 字节数组输入应返回空字符串")
    void nullByteArrayInput_shouldReturnEmptyString() {
        String hash = HashService.md5((byte[]) null);
        assertEquals("", hash);
    }

    @Test
    @DisplayName("哈希结果应为小写十六进制字符串")
    void hash_shouldBeLowercaseHex() {
        String hash = HashService.md5("test");
        assertTrue(hash.matches("[0-9a-f]{32}"));
    }

    @Test
    @DisplayName("字节数组输入应正确计算哈希")
    void byteArrayInput_shouldComputeHash() {
        byte[] data = "Hello World".getBytes();
        String hash = HashService.md5(data);
        assertEquals(HashService.md5("Hello World"), hash);
    }
}

