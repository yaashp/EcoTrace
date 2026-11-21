package com.ecotrace.service;

import com.ecotrace.dto.Dtos.AssistantResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Keyword rules, not a machine-learning model. The response says so, so the interface never presents it as
 * an AI prediction. A real vision model could replace this behind the same endpoint.
 */
@Service
public class AssistantService {

    private record Rule(String keyword, String category, String guidance) {
    }

    private static final String BATTERY_GUIDANCE =
            "Damaged or swollen batteries can overheat. Do not charge, puncture, crush or open the device. "
                    + "Keep it apart from other items and tell the collector before handover.";

    private static final List<Rule> RULES = List.of(
            new Rule("swollen", "Battery", BATTERY_GUIDANCE),
            new Rule("bulging", "Battery", BATTERY_GUIDANCE),
            new Rule("power bank", "Battery", BATTERY_GUIDANCE),
            new Rule("battery", "Battery", BATTERY_GUIDANCE),
            new Rule("laptop", "Laptop", "Back up and wipe personal data before handover."),
            new Rule("notebook", "Laptop", "Back up and wipe personal data before handover."),
            new Rule("smartphone", "Mobile phone", "Remove the SIM and memory cards and wipe the device."),
            new Rule("phone", "Mobile phone", "Remove the SIM and memory cards and wipe the device."),
            new Rule("desktop", "Desktop or monitor", "Wipe the drive or remove it before handover."),
            new Rule("monitor", "Desktop or monitor", "Handle carefully and do not open the casing."),
            new Rule("screen", "Desktop or monitor", "Handle carefully and do not open the casing."),
            new Rule("printer", "Printer", "Remove ink or toner cartridges if you can."),
            new Rule("cable", "Cables", "Bundle loosely."),
            new Rule("charger", "Accessories", "Bundle loosely."),
            new Rule("headphone", "Accessories", "Remove any removable battery first."),
            new Rule("mouse", "Accessories", "No special preparation needed."),
            new Rule("keyboard", "Accessories", "No special preparation needed."));

    public AssistantResponse suggest(String description) {
        String text = description.toLowerCase(Locale.ROOT);
        String method = "RULE_BASED";
        String notice = "Keyword matching, not a machine-learning prediction. Confirm or change the category.";
        for (Rule rule : RULES) {
            if (text.contains(rule.keyword())) {
                return new AssistantResponse(rule.category(), rule.keyword(), rule.guidance(), method, notice);
            }
        }
        return new AssistantResponse(null, null,
                "No keyword matched. Choose a category yourself. If the item is damaged or has a battery you "
                        + "cannot remove, tell the collector before handover.",
                method, notice);
    }
}
