package core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.I18n;

import static org.junit.jupiter.api.Assertions.*;

class FakeIpPayloadGeneratorProviderTest {

    @Test
    @DisplayName("默认生成器应使用伪造 IP 显示名")
    void payloadGenerator_shouldUseExpectedDisplayName() {
        I18n i18n = I18n.getInstance();
        I18n.Language originalLanguage = i18n.getCurrentLanguage();
        i18n.setLanguage(I18n.Language.ENGLISH);
        try {
            FakeIpService service = new FakeIpService();
            FakeIpPayloadGeneratorProvider provider = new FakeIpPayloadGeneratorProvider(service);

            assertEquals("AuthKit Fake IP", provider.displayName());
            FakeIpPayloadGeneratorProvider.RandomIpPayloadGenerator generator =
                    (FakeIpPayloadGeneratorProvider.RandomIpPayloadGenerator) provider.providePayloadGenerator(null);
            assertNotNull(generator);
            assertEquals(FakeIpService.INTRUDER_PAYLOAD_COUNT, generator.remaining());
        } finally {
            i18n.setLanguage(originalLanguage);
        }
    }

    @Test
    @DisplayName("XFF 生成器显示名应为 AuthKit X-Forwarded-For")
    void xffPayloadGenerator_shouldUseXffDisplayName() {
        I18n i18n = I18n.getInstance();
        I18n.Language originalLanguage = i18n.getCurrentLanguage();
        i18n.setLanguage(I18n.Language.ENGLISH);
        try {
            FakeIpPayloadGeneratorProvider provider =
                    new FakeIpPayloadGeneratorProvider(new FakeIpService(), FakeIpPayloadGeneratorProvider.Mode.XFF_HEADER);

            assertEquals("AuthKit X-Forwarded-For", provider.displayName());
            assertEquals(FakeIpPayloadGeneratorProvider.Mode.XFF_HEADER, provider.mode());
        } finally {
            i18n.setLanguage(originalLanguage);
        }
    }

    @Test
    @DisplayName("XFF 生成器应产出 X-Forwarded-For 请求头 payload")
    void xffPayloadGenerator_shouldProduceXffHeaderPayload() {
        FakeIpService service = new FakeIpService();
        FakeIpPayloadGeneratorProvider.RandomIpPayloadGenerator generator =
                new FakeIpPayloadGeneratorProvider.RandomIpPayloadGenerator(
                        service, FakeIpService.INTRUDER_PAYLOAD_COUNT, FakeIpPayloadGeneratorProvider.Mode.XFF_HEADER);

        String payload = generator.nextPayloadValue();

        assertTrue(payload.startsWith(FakeIpService.XFF_HEADER_NAME + ": "));
        assertTrue(service.isValidIpv4(payload.substring((FakeIpService.XFF_HEADER_NAME + ": ").length())));
    }

    @Test
    @DisplayName("IP 生成器应产出合法 IPv4")
    void ipPayloadGenerator_shouldProduceValidIpv4() {
        FakeIpService service = new FakeIpService();
        FakeIpPayloadGeneratorProvider.RandomIpPayloadGenerator generator =
                new FakeIpPayloadGeneratorProvider.RandomIpPayloadGenerator(
                        service, FakeIpService.INTRUDER_PAYLOAD_COUNT, FakeIpPayloadGeneratorProvider.Mode.IP);

        assertTrue(service.isValidIpv4(generator.nextPayloadValue()));
    }
}
