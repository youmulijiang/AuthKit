package core;

import burp.api.montoya.intruder.AttackConfiguration;
import burp.api.montoya.intruder.GeneratedPayload;
import burp.api.montoya.intruder.IntruderInsertionPoint;
import burp.api.montoya.intruder.PayloadGenerator;
import burp.api.montoya.intruder.PayloadGeneratorProvider;
import utils.I18n;

/**
 * Intruder 自定义 Payload 生成器。
 * 可生成随机 IP，或完整 {@code X-Forwarded-For: <ip>} 请求头。
 */
public class FakeIpPayloadGeneratorProvider implements PayloadGeneratorProvider {

    public enum Mode {
        IP,
        XFF_HEADER
    }

    private final FakeIpService fakeIpService;
    private final Mode mode;

    public FakeIpPayloadGeneratorProvider(FakeIpService fakeIpService) {
        this(fakeIpService, Mode.IP);
    }

    public FakeIpPayloadGeneratorProvider(FakeIpService fakeIpService, Mode mode) {
        this.fakeIpService = fakeIpService;
        this.mode = mode != null ? mode : Mode.IP;
    }

    @Override
    public String displayName() {
        I18n i18n = I18n.getInstance();
        return mode == Mode.XFF_HEADER
                ? i18n.text("auth_context_menu", "intruder.payload.xff")
                : i18n.text("auth_context_menu", "intruder.payload.fakeIp");
    }

    @Override
    public PayloadGenerator providePayloadGenerator(AttackConfiguration attackConfiguration) {
        return new RandomIpPayloadGenerator(fakeIpService, FakeIpService.INTRUDER_PAYLOAD_COUNT, mode);
    }

    Mode mode() {
        return mode;
    }

    static final class RandomIpPayloadGenerator implements PayloadGenerator {

        private final FakeIpService fakeIpService;
        private final Mode mode;
        private int remaining;

        RandomIpPayloadGenerator(FakeIpService fakeIpService, int payloadCount, Mode mode) {
            this.fakeIpService = fakeIpService;
            this.mode = mode != null ? mode : Mode.IP;
            this.remaining = payloadCount;
        }

        @Override
        public GeneratedPayload generatePayloadFor(IntruderInsertionPoint insertionPoint) {
            if (remaining <= 0) {
                return GeneratedPayload.end();
            }
            remaining--;
            String payload = mode == Mode.XFF_HEADER
                    ? fakeIpService.buildXffHeaderPayload()
                    : fakeIpService.randomPublicIp();
            return GeneratedPayload.payload(payload);
        }

        String nextPayloadValue() {
            return mode == Mode.XFF_HEADER
                    ? fakeIpService.buildXffHeaderPayload()
                    : fakeIpService.randomPublicIp();
        }

        int remaining() {
            return remaining;
        }
    }
}
