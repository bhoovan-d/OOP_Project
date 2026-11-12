package edu.oop.itsm.notification;

import edu.oop.itsm.domain.ChannelType;
import edu.oop.itsm.domain.Notification;
import edu.oop.itsm.domain.User;

/**
 * Delivery mechanism contract. Adding push / Slack / Teams later = one new implementation;
 * the router discovers it automatically.
 */
public interface NotificationChannel {

    ChannelType type();

    /** Whether this recipient can be reached on this channel (e.g. SMS needs a phone number). */
    default boolean canReach(User recipient) { return true; }

    void deliver(Notification notification, User recipient);
}
