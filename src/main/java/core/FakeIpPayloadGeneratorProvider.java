package core;

import burp.api.montoya.intruder.AttackConfiguration;
import burp.api.montoya.intruder.GeneratedPayload;
import burp.api.montoya.intruder.IntruderInsertionPoint;
import burp.api.montoya.intruder.PayloadGenerator;
import burp.api.montoya.intruder.PayloadGeneratorProvider;

/** Intruder 随机 IP payload 生成器。 */
public class FakeIpPayloadGeneratorProvider implements PayloadGeneratorProvider {

    private final FakeIpService fakeIpService;

    public FakeIpPayloadGeneratorProvider(FakeIpService fakeIpService) {
        this.fakeIpService = fakeIpService;
    }

    @Override
    public String displayName() {
        return FakeIpService.INTRUDER_PAYLOAD_NAME;
    }

    @Override
    public PayloadGenerator providePayloadGenerator(AttackConfiguration attackConfiguration) {
        return new RandomIpPayloadGenerator(fakeIpService);
    }

    private static class RandomIpPayloadGenerator implements PayloadGenerator {

        private final FakeIpService fakeIpService;

        private RandomIpPayloadGenerator(FakeIpService fakeIpService) {
            this.fakeIpService = fakeIpService;
        }

        @Override
        public GeneratedPayload generatePayloadFor(IntruderInsertionPoint insertionPoint) {
            return GeneratedPayload.payload(fakeIpService.randomPublicIp());
        }
    }
}
