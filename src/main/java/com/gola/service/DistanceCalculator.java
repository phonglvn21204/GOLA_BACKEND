package com.gola.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service for geographic distance calculation and travel time estimation.
 * Uses OSRM API for actual road distances with Haversine fallback.
 */
@Slf4j
@Service
public class DistanceCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double AVG_SPEED_KMH = 40.0;
    private static final String OSRM_BASE_URL = "https://router.project-osrm.org/route/v1/driving";

    private final RestTemplate osrmRestTemplate;
    private final ObjectMapper objectMapper;

    public DistanceCalculator(@Qualifier("osrmRestTemplate") RestTemplate osrmRestTemplate,
                              ObjectMapper objectMapper) {
        this.osrmRestTemplate = osrmRestTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Calculates the actual road distance between two coordinates using OSRM API.
     * Falls back to Haversine formula if OSRM fails.
     *
     * @param lat1 latitude of point 1 (degrees)
     * @param lng1 longitude of point 1 (degrees)
     * @param lat2 latitude of point 2 (degrees)
     * @param lng2 longitude of point 2 (degrees)
     * @return distance in kilometres
     */
    public double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        try {
            return callOsrmDistance(lat1, lng1, lat2, lng2);
        } catch (Exception e) {
            log.warn("OSRM API call failed, falling back to Haversine: {}", e.getMessage());
            return haversineDistance(lat1, lng1, lat2, lng2);
        }
    }

    /**
     * Estimates travel time based on OSRM API duration.
     * Falls back to distance-based estimation if OSRM fails.
     *
     * @param lat1 latitude of point 1 (degrees)
     * @param lng1 longitude of point 1 (degrees)
     * @param lat2 latitude of point 2 (degrees)
     * @param lng2 longitude of point 2 (degrees)
     * @return estimated travel time in minutes
     */
    public double estimateTravelTime(double lat1, double lng1, double lat2, double lng2) {
        try {
            return callOsrmTravelTime(lat1, lng1, lat2, lng2);
        } catch (Exception e) {
            log.warn("OSRM API call failed, falling back to distance-based estimation: {}", e.getMessage());
            double distanceKm = haversineDistance(lat1, lng1, lat2, lng2);
            return (distanceKm / AVG_SPEED_KMH) * 60.0;
        }
    }

    /**
     * Legacy method for backward compatibility.
     * Calculates the great-circle distance using Haversine formula.
     *
     * @param lat1 latitude of point 1 (degrees)
     * @param lng1 longitude of point 1 (degrees)
     * @param lat2 latitude of point 2 (degrees)
     * @param lng2 longitude of point 2 (degrees)
     * @return distance in kilometres
     */
    public static double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Calls OSRM API to get actual road distance.
     *
     * @param lat1 latitude of point 1 (degrees)
     * @param lng1 longitude of point 1 (degrees)
     * @param lat2 latitude of point 2 (degrees)
     * @param lng2 longitude of point 2 (degrees)
     * @return distance in kilometres
     * @throws Exception if OSRM API call fails or response cannot be parsed
     */
    private double callOsrmDistance(double lat1, double lng1, double lat2, double lng2) throws Exception {
        String url = String.format("%s/%s,%s;%s,%s?overview=false", 
                OSRM_BASE_URL, lng1, lat1, lng2, lat2);
        
        String response = osrmRestTemplate.getForObject(url, String.class);
        JsonNode root = objectMapper.readTree(response);
        
        // Get distance from routes[0].distance (in meters)
        JsonNode routes = root.path("routes");
        if (routes.isArray() && !routes.isEmpty()) {
            long distanceMeters = routes.get(0).path("distance").asLong(0);
            double distanceKm = distanceMeters / 1000.0;
            log.debug("OSRM distance: {} meters = {} km", distanceMeters, distanceKm);
            return distanceKm;
        } else {
            throw new RuntimeException("OSRM response: no routes found");
        }
    }

    /**
     * Calls OSRM API to get actual travel time.
     *
     * @param lat1 latitude of point 1 (degrees)
     * @param lng1 longitude of point 1 (degrees)
     * @param lat2 latitude of point 2 (degrees)
     * @param lng2 longitude of point 2 (degrees)
     * @return travel time in minutes
     * @throws Exception if OSRM API call fails or response cannot be parsed
     */
    private double callOsrmTravelTime(double lat1, double lng1, double lat2, double lng2) throws Exception {
        String url = String.format("%s/%s,%s;%s,%s?overview=false", 
                OSRM_BASE_URL, lng1, lat1, lng2, lat2);
        
        String response = osrmRestTemplate.getForObject(url, String.class);
        JsonNode root = objectMapper.readTree(response);
        
        // Get duration from routes[0].duration (in seconds)
        JsonNode routes = root.path("routes");
        if (routes.isArray() && !routes.isEmpty()) {
            long durationSeconds = routes.get(0).path("duration").asLong(0);
            double durationMinutes = durationSeconds / 60.0;
            log.debug("OSRM duration: {} seconds = {} minutes", durationSeconds, durationMinutes);
            return durationMinutes;
        } else {
            throw new RuntimeException("OSRM response: no routes found");
        }
    }

    /**
     * Gets coordinates (lat, lng) for a city name.
     * Uses hardcoded map for common cities, falls back to Nominatim geocoding API.
     *
     * @param cityName name of the city
     * @return array [latitude, longitude]
     * @throws Exception if city not found
     */
    public static double[] getCityCoords(String cityName) throws Exception {
        if (cityName == null || cityName.trim().isEmpty()) {
            throw new IllegalArgumentException("City name cannot be null or empty");
        }

        // Hardcoded coordinates for common Vietnamese cities
        java.util.Map<String, double[]> cityCoordinates = new java.util.HashMap<>();
        cityCoordinates.put("hanoi", new double[]{21.0285, 105.8542});
        cityCoordinates.put("hà nội", new double[]{21.0285, 105.8542});
        cityCoordinates.put("ho chi minh", new double[]{10.7769, 106.7009});
        cityCoordinates.put("hồ chí minh", new double[]{10.7769, 106.7009});
        cityCoordinates.put("saigon", new double[]{10.7769, 106.7009});
        cityCoordinates.put("sài gòn", new double[]{10.7769, 106.7009});
        cityCoordinates.put("da nang", new double[]{16.0544, 108.2022});
        cityCoordinates.put("đà nẵng", new double[]{16.0544, 108.2022});
        cityCoordinates.put("hai phong", new double[]{20.8448, 106.6880});
        cityCoordinates.put("hải phòng", new double[]{20.8448, 106.6880});
        cityCoordinates.put("can tho", new double[]{10.0379, 105.7869});
        cityCoordinates.put("cần thơ", new double[]{10.0379, 105.7869});
        cityCoordinates.put("hue", new double[]{16.4637, 107.5909});
        cityCoordinates.put("huế", new double[]{16.4637, 107.5909});
        cityCoordinates.put("nha trang", new double[]{12.2381, 109.1967});
        cityCoordinates.put("qui nhon", new double[]{13.7769, 109.2260});
        cityCoordinates.put("quỳ nhơn", new double[]{13.7769, 109.2260});

        // Try exact match first (lowercase)
        String cityLower = cityName.toLowerCase().trim();
        if (cityCoordinates.containsKey(cityLower)) {
            log.debug("Found coordinates for city: {}", cityName);
            return cityCoordinates.get(cityLower);
        }

        // Try partial match
        for (String key : cityCoordinates.keySet()) {
            if (cityLower.contains(key) || key.contains(cityLower)) {
                log.debug("Found partial match for city: {} -> {}", cityName, key);
                return cityCoordinates.get(key);
            }
        }

        // Fallback: use Nominatim API for geocoding
        log.warn("City not found in hardcoded map, attempting Nominatim geocoding: {}", cityName);
        return geocodeViaOpenStreetMap(cityName);
    }

    /**
     * Uses OpenStreetMap Nominatim API to geocode a city name.
     *
     * @param cityName name of the city
     * @return array [latitude, longitude]
     * @throws Exception if geocoding fails
     */
    private static double[] geocodeViaOpenStreetMap(String cityName) throws Exception {
        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            String url = String.format("https://nominatim.openstreetmap.org/search?q=%s&format=json", 
                    java.net.URLEncoder.encode(cityName, "UTF-8"));
            
            String response = restTemplate.getForObject(url, String.class);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode results = mapper.readTree(response);
            
            if (results.isArray() && !results.isEmpty()) {
                JsonNode firstResult = results.get(0);
                double lat = firstResult.path("lat").asDouble();
                double lon = firstResult.path("lon").asDouble();
                log.info("Geocoded city {} to coordinates: [{}, {}]", cityName, lat, lon);
                return new double[]{lat, lon};
            } else {
                throw new RuntimeException("No results found from Nominatim API for: " + cityName);
            }
        } catch (Exception e) {
            log.error("Geocoding failed for city: {}", cityName, e);
            // Fallback to default coordinates (Hanoi center)
            log.warn("Falling back to default coordinates (Hanoi)");
            return new double[]{21.0285, 105.8542};
        }
    }
}
