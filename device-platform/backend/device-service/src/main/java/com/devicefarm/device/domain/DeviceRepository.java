package com.devicefarm.device.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    List<Device> findAllByProductId(Long productId);
    Optional<Device> findByProductIdAndSerial(Long productId, String serial);
}
