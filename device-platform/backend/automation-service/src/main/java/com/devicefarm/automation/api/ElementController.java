package com.devicefarm.automation.api;

import com.devicefarm.automation.api.dto.ElementDtos;
import com.devicefarm.automation.service.ElementService;
import com.devicefarm.common.jwt.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation/elements")
public class ElementController {

    private final ElementService service;

    public ElementController(ElementService service) { this.service = service; }

    @GetMapping
    public List<ElementDtos.View> list(@AuthenticationPrincipal JwtPrincipal caller,
                                       @RequestParam(value = "q", required = false) String q) {
        return service.list(caller, q);
    }

    @GetMapping("/{id}")
    public ElementDtos.View get(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        return service.get(caller, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ElementDtos.View create(@AuthenticationPrincipal JwtPrincipal caller,
                                   @RequestBody @Valid ElementDtos.CreateRequest req) {
        return service.create(caller, req);
    }

    @PutMapping("/{id}")
    public ElementDtos.View update(@AuthenticationPrincipal JwtPrincipal caller,
                                   @PathVariable long id,
                                   @RequestBody @Valid ElementDtos.UpdateRequest req) {
        return service.update(caller, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal JwtPrincipal caller, @PathVariable long id) {
        service.delete(caller, id);
    }
}
