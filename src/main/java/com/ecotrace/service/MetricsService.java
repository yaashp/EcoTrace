package com.ecotrace.service;

import com.ecotrace.domain.Pickup;
import com.ecotrace.domain.PickupStatus;
import com.ecotrace.dto.Dtos.Metrics;
import com.ecotrace.repo.PickupEventRepository;
import com.ecotrace.repo.PickupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Every figure is computed from stored records at request time; nothing is hardcoded. Demonstration
 * records can be excluded so that real activity is never mixed with sample data.
 * Pickups for the selected range are loaded and aggregated in Java, which suits a demo or small
 * deployment. At larger scale, replace with SQL aggregates.
 */
@Service
public class MetricsService {

    private static final Set<PickupStatus> PENDING = EnumSet.of(PickupStatus.REQUESTED, PickupStatus.CONFIRMED);
    private static final Set<PickupStatus> AWAITING = EnumSet.of(PickupStatus.AT_FACILITY, PickupStatus.TRANSFERRED,
            PickupStatus.RECEIVED_BY_PARTNER, PickupStatus.PROCESSING);
    private static final Set<PickupStatus> CONFIRMED_RECYCLING = EnumSet.of(PickupStatus.RECYCLING_CONFIRMED, PickupStatus.COMPLETED);

    private final PickupRepository pickups;
    private final PickupEventRepository events;

    public MetricsService(PickupRepository pickups, PickupEventRepository events) {
        this.pickups = pickups;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Metrics compute(LocalDate from, LocalDate to, boolean includeDemo) {
        ZoneId zone = ZoneId.systemDefault();
        Instant fromInstant = from == null ? Instant.EPOCH : from.atStartOfDay(zone).toInstant();
        Instant toInstant = to == null ? Instant.now().plus(3650, ChronoUnit.DAYS) : to.plusDays(1).atStartOfDay(zone).toInstant();

        List<Pickup> list = pickups.findForMetrics(fromInstant, toInstant, includeDemo);
        LocalDate today = LocalDate.now();

        double avgHours = events.transitionTimes(PickupStatus.COLLECTED, fromInstant, toInstant, includeDemo).stream()
                .mapToDouble(row -> Duration.between((Instant) row[0], (Instant) row[1]).toMinutes() / 60.0)
                .filter(h -> h >= 0)
                .average().orElse(0);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (PickupStatus s : PickupStatus.values()) {
            long count = list.stream().filter(p -> p.getStatus() == s).count();
            if (count > 0) {
                byStatus.put(s.label(), count);
            }
        }
        Map<String, Long> byCategory = new TreeMap<>();
        for (Object[] row : pickups.itemsByCategory(fromInstant, toInstant, includeDemo)) {
            byCategory.put((String) row[0], ((Number) row[1]).longValue());
        }

        return new Metrics(
                list.size(),
                count(list, p -> PENDING.contains(p.getStatus())),
                count(list, p -> p.getStatus().isCollectedOrLater()),
                count(list, p -> AWAITING.contains(p.getStatus())),
                count(list, p -> CONFIRMED_RECYCLING.contains(p.getStatus())),
                count(list, p -> p.getStatus() == PickupStatus.FAILED_PICKUP),
                count(list, p -> p.getStatus() == PickupStatus.REJECTED),
                count(list, p -> p.getStatus() == PickupStatus.CANCELLED),
                count(list, p -> PENDING.contains(p.getStatus()) && p.getPickupDate().isBefore(today)),
                round1(list.stream().filter(p -> p.getStatus() != PickupStatus.CANCELLED && p.getEstimatedWeightKg() != null)
                        .mapToDouble(Pickup::getEstimatedWeightKg).sum()),
                round1(list.stream().filter(p -> p.getVerifiedWeightKg() != null).mapToDouble(Pickup::getVerifiedWeightKg).sum()),
                round1(avgHours),
                list.stream().anyMatch(Pickup::isDemo),
                byStatus,
                group(list, p -> YearMonth.from(p.getCreatedAt().atZone(zone)).toString()),
                byCategory,
                group(list, Pickup::getLocality));
    }

    private static int count(List<Pickup> list, Predicate<Pickup> test) {
        return (int) list.stream().filter(test).count();
    }

    private static Map<String, Long> group(List<Pickup> list, Function<Pickup, String> key) {
        return list.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.counting()));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
