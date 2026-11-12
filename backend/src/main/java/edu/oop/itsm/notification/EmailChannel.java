package edu.oop.itsm.notification;

import edu.oop.itsm.domain.ChannelType;
import edu.oop.itsm.domain.Notification;
import edu.oop.itsm.domain.User;
import edu.oop.itsm.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Simulated SMTP delivery: the message is logged and kept in the outbox table (visible to admins).
 * Swap the body of deliver() for JavaMailSender to send real mail.
 */
@Component
public class EmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);
    private final NotificationRepository repository;

    public EmailChannel(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override public ChannelType type() { return ChannelType.EMAIL; }

    @Override
    public void deliver(Notification n, User recipient) {
        n.setExternalId("smtp-" + UUID.randomUUID().toString().substring(0, 8));
        repository.save(n);
        log.info("[EMAIL] to={} subject=\"{}\"", recipient.getEmail(), n.getTitle());
    }
}
