package com.devicefarm.session.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findFirstByDeviceIdAndStatus(Long deviceId, String status);
    List<Session> findAllByUserIdAndStatus(Long userId, String status);
    List<Session> findAllByProductIdAndStatus(Long productId, String status);
    List<Session> findAllByStatus(String status);
}
