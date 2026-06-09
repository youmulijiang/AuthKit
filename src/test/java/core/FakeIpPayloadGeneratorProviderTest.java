package core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FakeIpPayloadGeneratorProviderTest {

    @Test
    @DisplayName("Intruder payload 生成器名称应为 fakeIpPayloads")
    void payloadGenerator_shouldUseExpectedDisplayName() {
        FakeIpService service = new FakeIpService();
        FakeIpPayloadGeneratorProvider provider = new FakeIpPayloadGeneratorProvider(service);

        assertEquals(FakeIpService.INTRUDER_PAYLOAD_NAME, provider.displayName());
        assertNotNull(provider.providePayloadGenerator(null));
    }
}
