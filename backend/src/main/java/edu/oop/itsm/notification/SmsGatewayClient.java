package edu.oop.itsm.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Stand-in for a third-party SMS provider SDK (Twilio-like). Its API deliberately does not match
 * our NotificationChannel contract — {@link SmsChannel} adapts it.
 */
@Component
public class SmsGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(SmsGatewayClient.class);
    private final AtomicLong sequence = new AtomicLong(10_000);

    public record SendResult(boolean accepted, String messageSid) {}

    public SendResult sendText(String toMsisdn, String body) {
        if (toMsisdn == null || !toMsisdn.matches("\\+?[0-9 ()-]{7,20}")) return new SendResult(false, null);
        String sid = "SM" + sequence.incrementAndGet();
        log.info("[SMS] to={} sid={} body=\"{}\"", toMsisdn, sid, body);
        return new SendResult(true, sid);
    }
}
