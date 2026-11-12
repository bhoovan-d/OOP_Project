package edu.oop.itsm.repository;

import edu.oop.itsm.domain.ChannelType;
import edu.oop.itsm.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByRecipientIdAndChannelOrderByCreatedAtDesc(Long recipientId, ChannelType channel);

    long countByRecipientIdAndChannelAndReadFalse(Long recipientId, ChannelType channel);

    Page<Notification> findByChannelInOrderByCreatedAtDesc(Collection<ChannelType> channels, Pageable pageable);

    @Modifying
    @Query("update Notification n set n.read = true where n.recipientId = :recipientId and n.channel = edu.oop.itsm.domain.ChannelType.IN_APP and n.read = false")
    int markAllRead(@Param("recipientId") Long recipientId);
}
