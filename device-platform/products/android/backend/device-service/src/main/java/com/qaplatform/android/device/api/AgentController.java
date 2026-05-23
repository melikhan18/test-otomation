package com.qaplatform.android.device.api;

import com.qaplatform.android.device.api.dto.DeviceDtos;
import com.qaplatform.android.device.service.EnrollmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final EnrollmentService enrollment;

    public AgentController(EnrollmentService enrollment) { this.enrollment = enrollment; }

    /** Public — the enrollment token itself is the credential. */
    @PostMapping("/enroll")
    public DeviceDtos.EnrollResponse enroll(@RequestBody @Valid DeviceDtos.EnrollRequest req) {
        return enrollment.enroll(req);
    }
}
