package edu.oop.itsm.notification;

import edu.oop.itsm.domain.*;
import edu.oop.itsm.event.IncidentEvent;
import edu.oop.itsm.exception.NotFoundException;
import edu.oop.itsm.exception.UnauthorizedOperationException;
import edu.oop.itsm.repository.AdministratorRepository;
import edu.oop.itsm.repository.NotificationRepository;
import edu.oop.itsm.repository.TechnicianRepository;
import edu.oop.itsm.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.*;

/**
 * Observer #2: decides who hears about each event and on which channels, then hands off to the channels.
 * Runs asynchronously after commit, so slow email/SMS never delay the user's request.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final Map<ChannelType, NotificationChannel> channels = new EnumMap<>(ChannelType.class);
    private final NotificationFactory factory;
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final AdministratorRepository admins;
    private final TechnicianRepository technicians;

    public NotificationService(List<NotificationChannel> channelBeans, NotificationFactory factory, NotificationRepository notifications,
                               UserRepository users, AdministratorRepository admins, TechnicianRepository technicians) {
        channelBeans.forEach(c -> channels.put(c.type(), c));
        this.factory = factory;
        this.notifications = notifications;
        this.users = users;
        this.admins = admins;
        this.technicians = technicians;
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onIncidentEvent(IncidentEvent e) {
        try {
            Map<Long, EnumSet<ChannelType>> routes = route(e);
            routes.remove(e.actorId()); // nobody is notified about their own action
            if (routes.isEmpty()) return;
            for (User recipient : users.findAllById(routes.keySet())) {
                if (!recipient.isActive()) continue;
                for (ChannelType ct : routes.get(recipient.getId())) {
                    NotificationChannel ch = channels.get(ct);
                    if (ch != null && ch.canReach(recipient)) ch.deliver(factory.create(e, recipient, ct), recipient);
                }
            }
        } catch (RuntimeException ex) {
            // a failed notification must never break the business flow; it is logged for ops
            log.error("Notification dispatch failed for {} {}", e.type(), e.number(), ex);
        }
    }

    /** Routing table: event -> recipients -> channels. */
    Map<Long, EnumSet<ChannelType>> route(IncidentEvent e) {
        Map<Long, EnumSet<ChannelType>> r = new HashMap<>();
        boolean urgent = e.priority() != null && e.priority().isUrgent();
        switch (e.type()) {
            case CREATED -> add(r, e.reporterId(), ChannelType.IN_APP, ChannelType.EMAIL);
            case ASSIGNED -> {
                add(r, e.assigneeId(), ChannelType.IN_APP, ChannelType.EMAIL);
                if (urgent) add(r, e.assigneeId(), ChannelType.SMS);
                add(r, e.reporterId(), ChannelType.IN_APP);
            }
            case UNASSIGNED_QUEUED -> admins.findByActiveTrue().forEach(a -> add(r, a.getId(), ChannelType.IN_APP, ChannelType.EMAIL));
            case PRIORITY_CHANGED -> { add(r, e.assigneeId(), ChannelType.IN_APP); add(r, e.reporterId(), ChannelType.IN_APP); }
            case INFO_REQUESTED, RESOLVED -> add(r, e.reporterId(), ChannelType.IN_APP, ChannelType.EMAIL);
            case INFO_PROVIDED, DUPLICATE_DETECTED, FEEDBACK, NOTE_ADDED -> add(r, e.assigneeId(), ChannelType.IN_APP);
            case COMMENTED -> { add(r, e.assigneeId(), ChannelType.IN_APP); add(r, e.reporterId(), ChannelType.IN_APP); }
            case REOPENED -> add(r, e.assigneeId(), ChannelType.IN_APP, ChannelType.EMAIL);
            case CLOSED -> { add(r, e.reporterId(), ChannelType.IN_APP); add(r, e.assigneeId(), ChannelType.IN_APP); }
            case CANCELLED -> { add(r, e.reporterId(), ChannelType.IN_APP, ChannelType.EMAIL); add(r, e.assigneeId(), ChannelType.IN_APP); }
            case SLA_WARNING -> {
                if (e.assigneeId() == null) admins.findByActiveTrue().forEach(a -> add(r, a.getId(), ChannelType.IN_APP));
                else { add(r, e.assigneeId(), ChannelType.IN_APP); if (urgent) add(r, e.assigneeId(), ChannelType.SMS); }
            }
            case SLA_BREACHED -> {
                add(r, e.assigneeId(), ChannelType.IN_APP, ChannelType.EMAIL);
                admins.findByActiveTrue().forEach(a -> add(r, a.getId(), ChannelType.IN_APP, ChannelType.EMAIL));
            }
            case ESCALATED -> {
                switch (e.escalation()) {
                    case TEAM_LEAD -> technicians.findByTeamLeadTrueAndActiveTrue().forEach(t -> add(r, t.getId(), ChannelType.IN_APP, ChannelType.SMS));
                    case SYSTEM_ADMIN -> admins.findByItManagerFalseAndActiveTrue().forEach(a -> add(r, a.getId(), ChannelType.IN_APP, ChannelType.EMAIL, ChannelType.SMS));
                    case IT_MANAGER -> admins.findByItManagerTrueAndActiveTrue().forEach(a -> add(r, a.getId(), ChannelType.IN_APP, ChannelType.EMAIL, ChannelType.SMS));
                    default -> { }
                }
                add(r, e.assigneeId(), ChannelType.IN_APP);
            }
            case MAJOR_INCIDENT -> {
                admins.findByActiveTrue().forEach(a -> add(r, a.getId(), ChannelType.IN_APP, ChannelType.EMAIL, ChannelType.SMS));
                technicians.findByTeamLeadTrueAndActiveTrue().forEach(t -> add(r, t.getId(), ChannelType.IN_APP));
            }
            default -> { }
        }
        return r;
    }

    private static void add(Map<Long, EnumSet<ChannelType>> r, Long userId, ChannelType... types) {
        if (userId == null) return;
        r.computeIfAbsent(userId, k -> EnumSet.noneOf(ChannelType.class)).addAll(Arrays.asList(types));
    }

    // ---------------- read side ----------------

    @Transactional(readOnly = true)
    public List<Notification> inbox(Long userId) {
        return notifications.findTop50ByRecipientIdAndChannelOrderByCreatedAtDesc(userId, ChannelType.IN_APP);
    }

    @Transactional(readOnly = true)
    public long unread(Long userId) {
        return notifications.countByRecipientIdAndChannelAndReadFalse(userId, ChannelType.IN_APP);
    }

    @Transactional
    public void markRead(Long notificationId, Long userId) {
        Notification n = notifications.findById(notificationId).orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!n.getRecipientId().equals(userId)) throw new UnauthorizedOperationException("Not your notification");
        n.markRead();
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notifications.markAllRead(userId);
    }

    @Transactional(readOnly = true)
    public Page<Notification> outbox(Pageable pageable) {
        return notifications.findByChannelInOrderByCreatedAtDesc(EnumSet.of(ChannelType.EMAIL, ChannelType.SMS), pageable);
    }
}
