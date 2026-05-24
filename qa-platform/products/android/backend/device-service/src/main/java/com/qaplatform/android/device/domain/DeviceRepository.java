package com.qaplatform.android.device.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    List<Device> findAllByCompanyId(Long companyId);
    Optional<Device> findByCompanyIdAndSerial(Long companyId, String serial);
}
