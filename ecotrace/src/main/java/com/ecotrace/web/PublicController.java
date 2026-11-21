package com.ecotrace.web;

import com.ecotrace.dto.Dtos.AssistantRequest;
import com.ecotrace.dto.Dtos.AssistantResponse;
import com.ecotrace.dto.Dtos.PartnerView;
import com.ecotrace.dto.Dtos.PublicTrack;
import com.ecotrace.dto.Dtos.SlotView;
import com.ecotrace.service.AssistantService;
import com.ecotrace.service.PartnerService;
import com.ecotrace.service.PickupService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Endpoints that need no sign-in. None of them returns personal data. */
@RestController
@RequestMapping("/api")
public class PublicController {

    private final PickupService pickups;
    private final PartnerService partners;
    private final AssistantService assistant;

    public PublicController(PickupService pickups, PartnerService partners, AssistantService assistant) {
        this.pickups = pickups;
        this.partners = partners;
        this.assistant = assistant;
    }

    @GetMapping("/track/{reference}")
    public PublicTrack track(@PathVariable String reference) {
        return pickups.track(reference);
    }

    @GetMapping("/partners")
    public List<PartnerView> partners(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String verification) {
        return partners.search(q, category, type, verification);
    }

    @GetMapping("/slots")
    public List<SlotView> slots(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return pickups.slots(date);
    }

    @PostMapping("/assistant/category")
    public AssistantResponse suggest(@Valid @RequestBody AssistantRequest request) {
        return assistant.suggest(request.description());
    }
}
