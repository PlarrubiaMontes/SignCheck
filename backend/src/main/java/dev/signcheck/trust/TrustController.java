package dev.signcheck.trust;

import dev.signcheck.trust.dto.TrustStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/trust/status}: which trust anchors are loaded. */
@RestController
@RequestMapping("/api/v1/trust")
@Tag(name = "Trust", description = "Trust anchors used by the validator")
public class TrustController {

    private final TrustStatusService trustStatusService;

    public TrustController(TrustStatusService trustStatusService) {
        this.trustStatusService = trustStatusService;
    }

    @Operation(summary = "Status of the trust anchors",
            description = "Trust mode in use and, in LOTL mode, whether the EU Trusted Lists are loaded.")
    @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public TrustStatus status() {
        return trustStatusService.status();
    }
}
