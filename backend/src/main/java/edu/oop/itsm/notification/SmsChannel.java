package edu.oop.itsm.notification;

import edu.oop.itsm.domain.ChannelType;
import edu.oop.itsm.domain.Notification;
import edu.oop.itsm.domain.User;
import edu.oop.itsm.repository.NotificationRepository;
import org.springframework.stereotype.Component;

/** Adapter pattern: exposes the vendor-specific {@link SmsGatewayClient} through our {@link NotificationChannel} interface. */
@Component
public class SmsChannel implements NotificationChannel {

    private final SmsGatewayClient gateway;
    private final NotificationRepository repository;

    public SmsChannel(SmsGatewayClient gateway, NotificationRepository repository) {
        this.gateway = gateway;
        this.repository = repository;
    }

    @Override public ChannelType type() { return ChannelType.SMS; }

    @Override
    public boolean canReach(User recipient) {
        return recipient.getPhone() != null && !recipient.getPhone().isBlank();
    }

    @Override
    public void deliver(Notification n, User recipient) {
        SmsGatewayClient.SendResult result = gateway.sendText(recipient.getPhone(), n.getMessage());
        if (result.accepted()) {
            n.setExternalId(result.messageSid());
            repository.save(n);
        }
    }
}
