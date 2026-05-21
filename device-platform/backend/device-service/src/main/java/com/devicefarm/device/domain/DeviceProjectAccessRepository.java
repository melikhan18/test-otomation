package com.devicefarm.device.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DeviceProjectAccessRepository
        extends JpaRepository<DeviceProjectAccess, DeviceProjectAccess.Key> {

    List<DeviceProjectAccess> findAllByDeviceId(Long deviceId);
    List<DeviceProjectAccess> findAllByProjectId(Long projectId);
    boolean existsByDeviceIdAndProjectId(Long deviceId, Long projectId);
    long countByDeviceId(Long deviceId);

    @Transactional
    void deleteAllByDeviceId(Long deviceId);

    @Transactional
    void deleteByDeviceIdAndProjectId(Long deviceId, Long projectId);
}
