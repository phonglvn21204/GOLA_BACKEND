package com.gola.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.dto.safety.ApplyIncidentSuggestionResponse;
import com.gola.dto.safety.IncidentAiSuggestion;
import com.gola.dto.safety.IncidentAiSuggestionAction;
import com.gola.dto.safety.IncidentAiSuggestionsResponse;
import com.gola.dto.trip.TripResponse;
import com.gola.entity.Incident;
import com.gola.entity.Trip;
import com.gola.entity.TripStop;
import com.gola.entity.enums.MemberRole;
import com.gola.exception.GolaException;
import com.gola.repository.IncidentRepository;
import com.gola.repository.TripMemberRepository;
import com.gola.repository.TripRepository;
import com.gola.repository.TripStopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentAiService {
    private static final List<MemberRole> EDIT_ROLES = List.of(MemberRole.OWNER, MemberRole.EDITOR);

    private final IncidentRepository incidentRepo;
    private final TripRepository tripRepo;
    private final TripStopRepository stopRepo;
    private final TripMemberRepository memberRepo;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final TripService tripService;
    private final Map<UUID, List<IncidentAiSuggestion>> suggestionCache = new ConcurrentHashMap<>();

    @Transactional
    public IncidentAiSuggestionsResponse generateSuggestions(UUID incidentId, UUID userId) {
        Incident incident = incidentRepo.findById(incidentId).orElseThrow(() -> GolaException.notFound("Incident"));
        Trip trip = requireTripAndMembership(incident, userId);
        List<TripStop> stops = stopRepo.findByTrip_IdOrderByOrderIdxAsc(trip.getId());
        log.info("AI incident suggestion requested: incidentId={} tripId={} stopCount={}", incidentId, trip.getId(), stops.size());

        String source = "GEMINI";
        List<IncidentAiSuggestion> suggestions;
        try {
            suggestions = parseSuggestions(geminiClient.generateContent(buildPrompt(incident, trip, stops)));
            if (suggestions.isEmpty()) throw new IllegalArgumentException("Gemini returned no suggestions");
        } catch (Exception e) {
            source = "FALLBACK";
            log.warn("AI incident suggestions failed for incident {}. Falling back: {}", incidentId, e.getMessage());
            suggestions = fallbackSuggestions(incident, stops);
        }

        IncidentAiSuggestionsResponse response = IncidentAiSuggestionsResponse.builder()
                .incidentId(incidentId)
                .tripId(trip.getId())
                .source(source)
                .suggestions(suggestions)
                .build();
        persistSuggestion(incident, response);
        suggestionCache.put(incidentId, suggestions);
        log.info("AI incident suggestion generated: incidentId={} tripId={} count={} source={}",
                incidentId, trip.getId(), suggestions.size(), source);
        return response;
    }

    @Transactional
    public ApplyIncidentSuggestionResponse applySuggestion(UUID incidentId, UUID userId, String suggestionId) {
        Incident incident = incidentRepo.findById(incidentId).orElseThrow(() -> GolaException.notFound("Incident"));
        Trip trip = requireTripAndMembership(incident, userId);
        log.info("Apply incident suggestion requested: incidentId={} tripId={} userId={} suggestionId={}",
                incidentId, trip.getId(), userId, suggestionId);

        if (!memberRepo.existsByTripIdAndUserIdAndRoleIn(trip.getId(), userId, EDIT_ROLES)) {
            throw GolaException.forbidden();
        }

        List<TripStop> stops = stopRepo.findByTrip_IdOrderByOrderIdxAsc(trip.getId());
        List<IncidentAiSuggestion> suggestions = loadSuggestions(incident, stops);
        String effectiveSuggestionId = suggestionId;
        if ((effectiveSuggestionId == null || effectiveSuggestionId.isBlank()) && !suggestions.isEmpty()) {
            effectiveSuggestionId = suggestions.get(0).getId();
        }
        final String selectedSuggestionId = effectiveSuggestionId;
        IncidentAiSuggestion suggestion = suggestions.stream()
                .filter(item -> item.getId() != null && item.getId().equals(selectedSuggestionId))
                .findFirst()
                .orElseThrow(() -> GolaException.notFound("Incident suggestion"));

        ApplyResult result = applyActions(trip, stops, suggestion.getActions() != null ? suggestion.getActions() : List.of());
        incident.setStatus("APPLIED");
        incidentRepo.save(incident);
        suggestionCache.remove(incidentId);

        TripResponse tripResponse = tripService.getTrip(trip.getId(), userId, null, null);
        log.info("Incident suggestion applied: incidentId={} tripId={} actionTypes={} changedStopsCount={}",
                incidentId, trip.getId(), result.actionTypes(), result.changedStopsCount());
        return ApplyIncidentSuggestionResponse.builder()
                .incidentId(incidentId)
                .tripId(trip.getId())
                .suggestionId(selectedSuggestionId)
                .summary("Đã áp dụng phương án xử lý sự cố.")
                .actionTypes(result.actionTypes())
                .changedStopsCount(result.changedStopsCount())
                .trip(tripResponse)
                .build();
    }

    private Trip requireTripAndMembership(Incident incident, UUID userId) {
        if (incident.getTripId() == null) {
            throw GolaException.badRequest("Incident is not linked to a trip");
        }
        Trip trip = tripRepo.findActiveById(incident.getTripId()).orElseThrow(() -> GolaException.notFound("Trip"));
        if (!memberRepo.existsByTripIdAndUserId(trip.getId(), userId)) {
            throw GolaException.forbidden();
        }
        return trip;
    }

    private String buildPrompt(Incident incident, Trip trip, List<TripStop> stops) {
        List<Map<String, Object>> stopPayload = stops.stream()
                .map(stop -> Map.<String, Object>of(
                        "id", stop.getId().toString(),
                        "name", stop.getName() != null ? stop.getName() : "Địa điểm",
                        "category", stop.getCategory() != null ? stop.getCategory() : "",
                        "orderIdx", stop.getOrderIdx(),
                        "arrivalAt", stop.getArrivalAt() != null ? stop.getArrivalAt().toString() : "",
                        "durationMin", stop.getDurationMin() != null ? stop.getDurationMin() : 60,
                        "completed", stop.getCompletedAt() != null,
                        "lat", stop.getLat() != null ? stop.getLat() : 0,
                        "lng", stop.getLng() != null ? stop.getLng() : 0
                ))
                .toList();

        String stopsJson;
        try {
            stopsJson = objectMapper.writeValueAsString(stopPayload);
        } catch (Exception e) {
            stopsJson = stopPayload.toString();
        }

        return """
            Bạn là AI điều phối lịch trình du lịch khi có sự cố. Chỉ đề xuất, không tự sửa lịch.
            Trả về JSON array gồm đúng 2-3 phương án. Mỗi item có:
            id, title, description, impact, actions.

            Action hợp lệ:
            - RESCHEDULE: cần stopId, newArrivalAt ISO-8601, reason
            - SHIFT_TIME: cần minutes, fromStopId, reason
            - REPLACE: cần stopId hoặc oldStopId, newName, newCategory, reason
            - ADD: cần newName, newCategory, newArrivalAt ISO-8601, reason
            - REMOVE: cần stopId, reason. Không đề xuất remove transport/check-in/check-out/meal quan trọng.
            - KEEP_PLAN_WITH_WARNING: chỉ cảnh báo, không sửa lịch.

            Quy tắc:
            - Không tự gọi số khẩn cấp.
            - Không xóa nhiều điểm dừng.
            - Giữ bữa ăn, check-in/check-out, di chuyển liên tỉnh nếu còn hợp lý.
            - Ưu tiên thay đổi thực tế, ít phá lịch.
            - Tiếng Việt, không markdown, chỉ JSON.

            Trip: %s
            Destination: %s
            Incident type: %s
            Severity: %s
            Description: %s
            Estimated delay minutes: %s
            Current stop id: %s
            Affected stop id: %s
            Context: %s
            Stops: %s
            """.formatted(
                trip.getTitle(),
                trip.getDestination(),
                incident.getType(),
                incident.getSeverity(),
                incident.getDescription(),
                incident.getEstimatedDelayMinutes(),
                incident.getCurrentStopId(),
                incident.getAffectedStopId(),
                incident.getContext(),
                stopsJson
        ).trim();
    }

    private List<IncidentAiSuggestion> parseSuggestions(String text) throws Exception {
        JsonNode root = objectMapper.readTree(extractJsonArray(text));
        if (!root.isArray()) throw new IllegalArgumentException("AI response is not an array");

        List<IncidentAiSuggestion> suggestions = new ArrayList<>();
        for (JsonNode node : root) {
            List<IncidentAiSuggestionAction> actions = new ArrayList<>();
            JsonNode actionNodes = node.path("actions");
            if (actionNodes.isArray()) {
                for (JsonNode actionNode : actionNodes) {
                    actions.add(IncidentAiSuggestionAction.builder()
                            .type(actionNode.path("type").asText(""))
                            .minutes(actionNode.hasNonNull("minutes") ? actionNode.path("minutes").asInt() : null)
                            .fromStopId(parseUuid(actionNode.path("fromStopId").asText(null)))
                            .stopId(parseUuid(actionNode.path("stopId").asText(null)))
                            .oldStopId(parseUuid(actionNode.path("oldStopId").asText(null)))
                            .newPlaceName(actionNode.path("newPlaceName").asText(null))
                            .newName(actionNode.path("newName").asText(null))
                            .newCategory(actionNode.path("newCategory").asText(null))
                            .newArrivalAt(actionNode.path("newArrivalAt").asText(null))
                            .reason(actionNode.path("reason").asText(null))
                            .latitude(actionNode.hasNonNull("latitude") ? actionNode.path("latitude").asDouble() : null)
                            .longitude(actionNode.hasNonNull("longitude") ? actionNode.path("longitude").asDouble() : null)
                            .build());
                }
            }
            suggestions.add(IncidentAiSuggestion.builder()
                    .id(node.path("id").asText("suggestion-" + (suggestions.size() + 1)))
                    .title(node.path("title").asText("Phương án xử lý"))
                    .description(node.path("description").asText(""))
                    .impact(node.path("impact").asText(""))
                    .actions(actions)
                    .build());
            if (suggestions.size() == 3) break;
        }
        return suggestions;
    }

    private List<IncidentAiSuggestion> fallbackSuggestions(Incident incident, List<TripStop> stops) {
        int delay = incident.getEstimatedDelayMinutes() != null && incident.getEstimatedDelayMinutes() > 0
                ? incident.getEstimatedDelayMinutes()
                : 60;
        TripStop affected = findAffectedStop(incident, stops);
        TripStop optional = stops.stream()
                .filter(stop -> stop.getCompletedAt() == null)
                .filter(stop -> !isProtectedStop(stop))
                .max(Comparator.comparingDouble(TripStop::getOrderIdx))
                .orElse(null);

        List<IncidentAiSuggestion> suggestions = new ArrayList<>();
        suggestions.add(IncidentAiSuggestion.builder()
                .id("shift-time-" + delay)
                .title("Lùi lịch trình " + delay + " phút")
                .description("Giữ nguyên các điểm đến nhưng dời thời gian các điểm phía sau.")
                .impact("Không bỏ điểm nào, nhưng lịch có thể kết thúc muộn hơn.")
                .actions(List.of(IncidentAiSuggestionAction.builder()
                        .type("SHIFT_TIME")
                        .minutes(delay)
                        .fromStopId(affected != null ? affected.getId() : null)
                        .reason("Bù thời gian do sự cố")
                        .build()))
                .build());

        if (optional != null) {
            suggestions.add(IncidentAiSuggestion.builder()
                    .id("skip-low-priority")
                    .title("Bỏ bớt điểm ít ưu tiên")
                    .description("Bỏ một điểm chưa hoàn thành để giữ nhịp lịch trình sau sự cố.")
                    .impact("Tiết kiệm thời gian nhưng mất một điểm tham quan.")
                    .actions(List.of(IncidentAiSuggestionAction.builder()
                            .type("REMOVE")
                            .stopId(optional.getId())
                            .reason("Điểm có thể bỏ qua khi lịch bị ảnh hưởng")
                            .build()))
                    .build());
        }

        suggestions.add(IncidentAiSuggestion.builder()
                .id("keep-plan-with-warning")
                .title("Giữ lịch trình và cảnh báo")
                .description("Không thay đổi lịch, chỉ ghi nhận rủi ro để cả nhóm chủ động.")
                .impact("Không thay đổi dữ liệu chuyến đi.")
                .actions(List.of(IncidentAiSuggestionAction.builder()
                        .type("KEEP_PLAN_WITH_WARNING")
                        .minutes(delay)
                        .reason("Người dùng muốn tự xử lý")
                        .build()))
                .build());
        return suggestions;
    }

    private ApplyResult applyActions(Trip trip, List<TripStop> stops, List<IncidentAiSuggestionAction> actions) {
        List<String> actionTypes = new ArrayList<>();
        int changed = 0;
        for (IncidentAiSuggestionAction action : actions) {
            String type = normalizeActionType(action.getType());
            actionTypes.add(type);
            switch (type) {
                case "SHIFT_TIME" -> changed += shiftTime(stops, action);
                case "RESCHEDULE" -> changed += rescheduleStop(trip, action);
                case "REMOVE" -> changed += removeStop(trip, action);
                case "REPLACE" -> changed += replaceStop(trip, action);
                case "ADD" -> changed += addStop(trip, action, stops);
                case "KEEP_PLAN_WITH_WARNING" -> {
                    // No itinerary mutation by design.
                }
                default -> log.warn("Unknown incident suggestion action type: {}", type);
            }
        }
        return new ApplyResult(actionTypes, changed);
    }

    private int shiftTime(List<TripStop> stops, IncidentAiSuggestionAction action) {
        int minutes = action.getMinutes() != null && action.getMinutes() > 0 ? action.getMinutes() : 60;
        double fromOrder = 0;
        if (action.getFromStopId() != null) {
            TripStop from = stops.stream().filter(stop -> stop.getId().equals(action.getFromStopId())).findFirst().orElse(null);
            if (from != null) fromOrder = from.getOrderIdx();
        }

        final double shiftFromOrder = fromOrder;
        List<TripStop> changed = stops.stream()
                .filter(stop -> stop.getCompletedAt() == null)
                .filter(stop -> stop.getOrderIdx() >= shiftFromOrder)
                .toList();
        for (TripStop stop : changed) {
            if (stop.getArrivalAt() != null) {
                stop.setArrivalAt(stop.getArrivalAt().plus(minutes, ChronoUnit.MINUTES));
            }
            stop.setOrderIdx(stop.getOrderIdx() + minutes);
            stop.setNotes(appendNote(stop.getNotes(), "Đã dời lịch do sự cố: " + nullToDefault(action.getReason(), "điều chỉnh thời gian")));
        }
        stopRepo.saveAll(changed);
        return changed.size();
    }

    private int rescheduleStop(Trip trip, IncidentAiSuggestionAction action) {
        UUID stopId = action.getStopId() != null ? action.getStopId() : action.getOldStopId();
        TripStop stop = findMutableStop(trip, stopId);
        if (stop == null) return 0;
        Instant arrival = parseInstant(action.getNewArrivalAt());
        if (arrival == null) return 0;
        stop.setArrivalAt(arrival);
        stop.setOrderIdx(orderIdxFromArrival(stop.getOrderIdx(), arrival));
        stop.setNotes(appendNote(stop.getNotes(), "Đã đổi giờ do sự cố: " + nullToDefault(action.getReason(), "điều chỉnh lịch")));
        stopRepo.save(stop);
        return 1;
    }

    private int replaceStop(Trip trip, IncidentAiSuggestionAction action) {
        UUID stopId = action.getOldStopId() != null ? action.getOldStopId() : action.getStopId();
        TripStop stop = findMutableStop(trip, stopId);
        if (stop == null || isProtectedStop(stop)) return 0;
        String newName = firstNonBlank(action.getNewName(), action.getNewPlaceName());
        if (newName != null) stop.setName(newName);
        if (action.getNewCategory() != null && !action.getNewCategory().isBlank()) stop.setCategory(action.getNewCategory());
        if (action.getLatitude() != null) stop.setLat(action.getLatitude());
        if (action.getLongitude() != null) stop.setLng(action.getLongitude());
        stop.setNotes(appendNote(stop.getNotes(), "Đã thay đổi do sự cố: " + nullToDefault(action.getReason(), "AI đề xuất điểm thay thế")));
        stopRepo.save(stop);
        return 1;
    }

    private int removeStop(Trip trip, IncidentAiSuggestionAction action) {
        TripStop stop = findMutableStop(trip, action.getStopId());
        if (stop == null || isProtectedStop(stop)) return 0;
        stopRepo.delete(stop);
        return 1;
    }

    private int addStop(Trip trip, IncidentAiSuggestionAction action, List<TripStop> currentStops) {
        String name = firstNonBlank(action.getNewName(), action.getNewPlaceName());
        if (name == null) return 0;
        Instant arrival = parseInstant(action.getNewArrivalAt());
        double orderIdx = arrival != null
                ? orderIdxFromArrival(currentStops.isEmpty() ? 100000 : currentStops.get(0).getOrderIdx(), arrival)
                : currentStops.stream().mapToDouble(TripStop::getOrderIdx).max().orElse(100000) + 50;
        TripStop stop = TripStop.builder()
                .trip(trip)
                .name(name)
                .category(firstNonBlank(action.getNewCategory(), "NOTE"))
                .arrivalAt(arrival)
                .durationMin(60)
                .orderIdx(orderIdx)
                .lat(action.getLatitude())
                .lng(action.getLongitude())
                .notes("Thêm do xử lý sự cố: " + nullToDefault(action.getReason(), "AI đề xuất"))
                .systemStop(false)
                .build();
        stopRepo.save(stop);
        return 1;
    }

    private TripStop findAffectedStop(Incident incident, List<TripStop> stops) {
        UUID preferred = incident.getAffectedStopId() != null ? incident.getAffectedStopId() : incident.getCurrentStopId();
        if (preferred != null) {
            return stops.stream().filter(stop -> stop.getId().equals(preferred)).findFirst().orElse(null);
        }
        return stops.stream().filter(stop -> stop.getCompletedAt() == null)
                .min(Comparator.comparingDouble(TripStop::getOrderIdx)).orElse(null);
    }

    private TripStop findMutableStop(Trip trip, UUID stopId) {
        if (stopId == null) return null;
        TripStop stop = stopRepo.findById(stopId).orElse(null);
        if (stop == null || !stop.getTrip().getId().equals(trip.getId()) || stop.getCompletedAt() != null) return null;
        return stop;
    }

    private boolean isProtectedStop(TripStop stop) {
        String text = ((stop.getCategory() != null ? stop.getCategory() : "") + " " + (stop.getName() != null ? stop.getName() : "")).toLowerCase();
        return text.contains("transport")
                || text.contains("travel")
                || text.contains("di chuyển")
                || text.contains("check-in")
                || text.contains("checkin")
                || text.contains("checkout")
                || text.contains("trả phòng")
                || text.contains("nhận phòng")
                || text.contains("food")
                || text.contains("ăn ");
    }

    private String normalizeActionType(String type) {
        String value = type == null ? "" : type.trim().toUpperCase();
        return switch (value) {
            case "REMOVE_STOP" -> "REMOVE";
            case "REPLACE_STOP" -> "REPLACE";
            default -> value;
        };
    }

    private double orderIdxFromArrival(double fallbackOrderIdx, Instant arrival) {
        long seconds = arrival.getEpochSecond();
        long day = Math.max(1, Math.floorDiv(seconds, 86_400L) % 10_000L);
        int minuteOfDay = (int) Math.floorMod(seconds, 86_400L) / 60;
        if (fallbackOrderIdx >= 100000) {
            day = Math.max(1, (long) Math.floor(fallbackOrderIdx / 100000));
        }
        return day * 100000.0 + minuteOfDay;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private List<IncidentAiSuggestion> loadSuggestions(Incident incident, List<TripStop> stops) {
        List<IncidentAiSuggestion> cached = suggestionCache.get(incident.getId());
        if (cached != null && !cached.isEmpty()) return cached;
        if (incident.getAiSuggestionJson() != null && !incident.getAiSuggestionJson().isBlank()) {
            try {
                IncidentAiSuggestionsResponse response = objectMapper.readValue(incident.getAiSuggestionJson(), IncidentAiSuggestionsResponse.class);
                if (response.getSuggestions() != null && !response.getSuggestions().isEmpty()) return response.getSuggestions();
            } catch (Exception e) {
                log.warn("Could not parse persisted incident suggestion for {}: {}", incident.getId(), e.getMessage());
            }
        }
        return fallbackSuggestions(incident, stops);
    }

    private void persistSuggestion(Incident incident, IncidentAiSuggestionsResponse response) {
        try {
            incident.setAiSummary(summaryFrom(response.getSuggestions()));
            incident.setAiSuggestionJson(objectMapper.writeValueAsString(response));
            incident.setStatus("AI_SUGGESTED");
            incidentRepo.save(incident);
        } catch (Exception e) {
            log.warn("Could not persist incident AI suggestion for {}: {}", incident.getId(), e.getMessage());
        }
    }

    private String summaryFrom(List<IncidentAiSuggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) return null;
        IncidentAiSuggestion first = suggestions.get(0);
        return first.getDescription() != null && !first.getDescription().isBlank()
                ? first.getDescription()
                : first.getTitle();
    }

    private String appendNote(String notes, String addition) {
        if (notes == null || notes.isBlank()) return addition;
        return notes + "\n" + addition;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractJsonArray(String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("(?s)^```(?:json)?\\s*", "").replaceFirst("(?s)\\s*```$", "");
        }
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start < 0 || end < start) throw new IllegalArgumentException("No JSON array found");
        return cleaned.substring(start, end + 1);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String nullToDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private record ApplyResult(List<String> actionTypes, int changedStopsCount) {}
}
