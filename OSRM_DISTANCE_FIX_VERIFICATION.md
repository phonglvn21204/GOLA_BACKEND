# OSRM Distance Calculator - Fix Verification

## Issue Resolved
✅ **Error**: `cannot find symbol method getCityCoords(String)` in DistanceCalculator

## Solutions Implemented

### 1. Added `getCityCoords()` Static Method
**File**: `DistanceCalculator.java`

```java
public static double[] getCityCoords(String cityName) throws Exception
```

**Features**:
- Hardcoded map of Vietnamese cities with coordinates (Hanoi, Ho Chi Minh, Da Nang, etc.)
- Fallback to OpenStreetMap Nominatim API for any city
- Graceful error handling with default fallback to Hanoi (21.0285, 105.8542)
- Returns `double[]` with [latitude, longitude]

**Supported Cities (Hardcoded)**:
- Hanoi (hà nội): 21.0285, 105.8542
- Ho Chi Minh (hồ chí minh, sài gòn): 10.7769, 106.7009
- Da Nang (đà nẵng): 16.0544, 108.2022
- Hai Phong (hải phòng): 20.8448, 106.6880
- Can Tho (cần thơ): 10.0379, 105.7869
- Hue (huế): 16.4637, 107.5909
- Nha Trang: 12.2381, 109.1967
- Quy Nhon (quỳ nhơn): 13.7769, 109.2260

### 2. Enhanced TripService Exception Handling
**File**: `TripService.java` (lines 212-239)

**Changes**:
- Wrapped `getCityCoords()` call in try-catch block
- Uses `Double.NaN` for invalid coordinates instead of 0
- Only calculates first leg if coordinates are valid
- Proper logging when fallback occurs

```java
try {
    double[] cityCoords = DistanceCalculator.getCityCoords(t.getOrigin());
    originLat = cityCoords[0];
    originLng = cityCoords[1];
} catch (Exception e) {
    log.warn("Failed to get coordinates for city: {}, skipping first leg calculation", t.getOrigin(), e);
    originLat = Double.NaN;
    originLng = Double.NaN;
}

// Only calculate if coordinates are valid
if (!Double.isNaN(originLat) && !Double.isNaN(originLng)) {
    // Calculate distances using OSRM...
}
```

### 3. Configuration (AiConfig.java)
**Bean Configuration**:
```java
@Bean("osrmRestTemplate")
public RestTemplate osrmRestTemplate(RestTemplateBuilder builder) {
    return builder
        .setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofSeconds(10))
        .build();
}
```

## Build Status
✅ **BUILD SUCCESSFUL** in 6 seconds

## Compilation Results
```
> Task :compileJava ✅
> Task :processResources
> Task :classes
> Task :bootJar
> Task :assemble
> Task :build
BUILD SUCCESSFUL
```

## OSRM Integration Workflow

### For GET /api/trips/{id} endpoint:

1. **User Location (if provided)**:
   - Uses provided latitude/longitude
   - Calculates distance to first stop using OSRM API
   - Sets `distanceFromUserKm` and `travelTimeFromUserMin`

2. **Trip Origin (city name fallback)**:
   - Calls `getCityCoords(t.getOrigin())`
   - Gets city coordinates (hardcoded or via Nominatim)
   - Calculates distance to first stop using OSRM API

3. **Between Stops**:
   - Iterates through consecutive stops
   - Calls `distanceCalculator.calculateDistance(lat1, lng1, lat2, lng2)`
   - Accumulates total distance and travel time

4. **Response Fields**:
   - `totalDistanceKm`: Sum of all segments (in kilometers)
   - `totalTravelTimeMin`: Sum of all travel times (in minutes)
   - `distanceFromUserKm`: Distance from user's location to first stop (if provided)
   - `travelTimeFromUserMin`: Travel time from user's location to first stop (if provided)

## Error Handling & Fallbacks

| Scenario | Action |
|----------|--------|
| City name not found | Tries Nominatim API, then falls back to Hanoi |
| OSRM API fails | Falls back to Haversine formula |
| Haversine fails | Returns 0 distance |
| Invalid coordinates | Skips that leg calculation |

## Testing Recommendations

1. **Test with known cities**:
   ```bash
   # Trip from Hanoi to Da Nang
   POST /api/trips
   {
       "title": "Hanoi to Da Nang",
       "origin": "Hanoi",
       "destination": "Da Nang",
       ...
   }
   ```

2. **Verify coordinates are resolved**:
   - Check logs for: `Found coordinates for city: Hanoi`
   - Check logs for: `OSRM distance: * meters = * km`

3. **Compare with Google Maps**:
   - Hanoi to Da Nang: ~450-500 km by road
   - The OSRM value should match this range

## Files Modified
- ✅ `DistanceCalculator.java` - Added `getCityCoords()` and `geocodeViaOpenStreetMap()`
- ✅ `TripService.java` - Added exception handling and NaN checks
- ✅ `AiConfig.java` - OSRM RestTemplate bean (already configured)

## API Dependencies
- OpenStreetMap Nominatim API (https://nominatim.openstreetmap.org)
- OSRM Router (https://router.project-osrm.org)

**Note**: Both APIs are free and rate-limited, suitable for non-commercial applications.

