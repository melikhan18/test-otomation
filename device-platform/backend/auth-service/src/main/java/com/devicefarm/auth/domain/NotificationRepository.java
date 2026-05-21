package com.devicefarm.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndStatus(Long userId, String status);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.status = 'READ' " +
           "WHERE n.userId = :userId AND n.status = 'UNREAD'")
    int markAllRead(@Param("userId") Long userId);
}
