package model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompareSampleModel 单元测试（重构后支持动态用户）
 */
class CompareSampleModelTest {

    @Test
    @DisplayName("构造函数应正确设置基本属性")
    void constructor_shouldSetBasicProperties() {
        CompareSampleModel sample = new CompareSampleModel(1, "GET", "/api/test");
        assertEquals(1, sample.getId());
        assertEquals("GET", sample.getMethod());
        assertEquals("/api/test", sample.getUrl());
    }

    @Test
    @DisplayName("putMessageData 应正确存储和获取鉴权对象数据")
    void putMessageData_shouldStoreAndRetrieve() {
        CompareSampleModel sample = new CompareSampleModel(1, "GET", "/api/test");
        MessageDataModel data = new MessageDataModel("req", "resp", 200, 100, 12345);

        sample.putMessageData("Original", data);
        assertEquals(data, sample.getMessageData("Original"));
    }

    @Test
    @DisplayName("getMessageData 不存在的 key 应返回 null")
    void getMessageData_nonExistentKey_shouldReturnNull() {
        CompareSampleModel sample = new CompareSampleModel(1, "GET", "/api/test");
        assertNull(sample.getMessageData("NonExistent"));
    }

    @Test
    @DisplayName("支持多个动态鉴权对象")
    void shouldSupportMultipleDynamicAuthObjects() {
        CompareSampleModel sample = new CompareSampleModel(1, "POST", "/api/data");

        MessageDataModel original = new MessageDataModel("req1", "resp1", 200, 500, 1001);
        MessageDataModel unauth = new MessageDataModel("req2", "resp2", 403, 50, 1002);
        MessageDataModel user1 = new MessageDataModel("req3", "resp3", 200, 480, 1003);
        MessageDataModel user2 = new MessageDataModel("req4", "resp4", 200, 490, 1004);

        sample.putMessageData("Original", original);
        sample.putMessageData("Unauthorized", unauth);
        sample.putMessageData("User1", user1);
        sample.putMessageData("User2", user2);

        assertEquals(4, sample.getAuthNames().size());
        assertEquals(original, sample.getMessageData("Original"));
        assertEquals(user2, sample.getMessageData("User2"));
    }

    @Test
    @DisplayName("removeMessageData 应正确移除鉴权对象数据")
    void removeMessageData_shouldRemoveData() {
        CompareSampleModel sample = new CompareSampleModel(1, "GET", "/api/test");
        MessageDataModel data = new MessageDataModel("req", "resp", 200, 100, 9999);
        sample.putMessageData("User1", data);

        sample.removeMessageData("User1");
        assertNull(sample.getMessageData("User1"));
        assertFalse(sample.getAuthNames().contains("User1"));
    }

    @Test
    @DisplayName("getAuthNames 应返回所有鉴权对象名称")
    void getAuthNames_shouldReturnAllNames() {
        CompareSampleModel sample = new CompareSampleModel(1, "GET", "/api/test");
        sample.putMessageData("Original", new MessageDataModel());
        sample.putMessageData("Unauthorized", new MessageDataModel());

        var names = sample.getAuthNames();
        assertTrue(names.contains("Original"));
        assertTrue(names.contains("Unauthorized"));
    }

    @Test
    @DisplayName("getValueByAuthName 应根据 Length 指标返回包长度")
    void getValueByAuthName_length_shouldReturnLength() {
        CompareSampleModel sample = new CompareSampleModel(1, "GET", "/api/test");
        sample.putMessageData("Original", new MessageDataModel("req", "resp", 200, 500, 12345));

        assertEquals(500, sample.getValueByAuthName("Original", "Length"));
    }

    @Test
    @DisplayName("getValueByAuthName 应根据 Status Code 指标返回状态码")
    void getValueByAuthName_statusCode_shouldReturnStatusCode() {
        CompareSampleModel sample = new CompareSampleModel(1, "GET", "/api/test");
        sample.putMessageData("Original", new MessageDataModel("req", "resp", 200, 500, 12345));

        assertEquals(200, sample.getValueByAuthName("Original", "Status Code"));
    }

    @Test
    @DisplayName("getValueByAuthName 应根据 Hash 指标返回哈希值")
    void getValueByAuthName_hash_shouldReturnHash() {
        CompareSampleModel sample = new CompareSampleModel(1, "GET", "/api/test");
        sample.putMessageData("Original", new MessageDataModel("req", "resp", 200, 500, 12345));

        assertEquals(12345, sample.getValueByAuthName("Original", "Hash"));
    }

    @Test
    @DisplayName("getValueByAuthName 不存在的鉴权对象应返回 0")
    void getValueByAuthName_nonExistent_shouldReturnZero() {
        CompareSampleModel sample = new CompareSampleModel(1, "GET", "/api/test");

        assertEquals(0, sample.getValueByAuthName("NonExistent", "Length"));
    }

    @Test
    @DisplayName("getValueByAuthName 未知指标应默认返回包长度")
    void getValueByAuthName_unknownMetric_shouldReturnLength() {
        CompareSampleModel sample = new CompareSampleModel(1, "GET", "/api/test");
        sample.putMessageData("Original", new MessageDataModel("req", "resp", 200, 500, 12345));

        assertEquals(500, sample.getValueByAuthName("Original", "UnknownMetric"));
    }
}

