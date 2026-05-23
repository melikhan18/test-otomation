package com.qaplatform.android.device.service;

import com.qaplatform.common.error.ApiException;
import com.qaplatform.common.jwt.JwtPrincipal;
import com.qaplatform.android.device.domain.Device;
import com.qaplatform.android.device.domain.DeviceProjectAccessRepository;
import com.qaplatform.android.device.domain.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-tenant device mutations that only the platform admin should be able to
 * perform — reassigning a device between companies, etc. Lives separately from
 * the company-scoped {@link DeviceAccessService} so the privilege boundary is
 * obvious at the type level.
 */
@Service
public class AdminDeviceService {

    private final DeviceRepository devices;
    private final DeviceProjectAccessRepository access;

    public AdminDeviceService(DeviceRepository devices, DeviceProjectAccessRepository access) {
        this.devices = devices;
        this.access = access;
    }

    /**
     * Move a device to a different company. The old company's project whitelist
     * is wiped and the device is flipped back to {@code restricted=false} since
     * those grants reference projects in the previous tenant that the device no
     * longer belongs to.
     *
     * Side-effects intentionally minimal: any active session, run, or enrollment
     * token still references the old productId/companyId pair, but those rows
     * are short-lived enough that a manual cleanup pass isn't needed.
     */
    @Transactional
    public Device reassignCompany(JwtPrincipal caller, long deviceId, long newCompanyId) {
        if (caller == null || !caller.platformAdmin()) {
            throw ApiException.forbidden("platform admin only");
        }
        Device d = devices.findById(deviceId)
                .orElseThrow(() -> ApiException.notFound("device"));
        if (d.getCompanyId() != null && d.getCompanyId() == newCompanyId) {
            return d;   // no-op
        }
        access.deleteAllByDeviceId(d.getId());
        d.setCompanyId(newCompanyId);
        d.setRestricted(false);
        return d;
    }
}
