# getCityCoords() Implementation Reference

## Method Signature
```java
public static double[] getCityCoords(String cityName) throws Exception
```

## How It Works

### Step 1: City Name Resolution
```
Input: "Hanoi", "hà nội", "Ho Chi Minh", "Saigon", etc.
↓
Normalize to lowercase and trim whitespace
↓
Try exact match against hardcoded city map
```

### Step 2: Hardcoded Cities (Priority 1)
```
IF EXACT MATCH FOUND:
  → Return [latitude, longitude] immediately
  → Log: "Found coordinates for city: {cityName}"
  → Example: "hanoi" → [21.0285, 105.8542]
```

### Step 3: Partial Match (Priority 2)
```
IF NO EXACT MATCH:
  → Try partial string matching
  → Example: "chi minh" matches "hồ chí minh"
  → Log: "Found partial match for city: {cityName} -> {matchedKey}"
```

### Step 4: API Geocoding (Priority 3 - Fallback)
```
IF NO HARDCODED MATCH:
  → Call geocodeViaOpenStreetMap(cityName)
  → Uses OpenStreetMap Nominatim API
  → URL: https://nominatim.openstreetmap.org/search?q={cityName}&format=json
  → Parse response to extract [lat, lon]
```

### Step 5: Ultimate Fallback
```
IF ALL ATTEMPTS FAIL:
  → Log error: "Falling back to default coordinates (Hanoi)"
  → Return DEFAULT: [21.0285, 105.8542]
  → No exception thrown (graceful degradation)
```

## Hardcoded City Mapping

| City Name | Aliases | Latitude | Longitude |
|-----------|---------|----------|-----------|
| hanoi | hà nội | 21.0285 | 105.8542 |
| ho chi minh | hồ chí minh, sài gòn, saigon | 10.7769 | 106.7009 |
| da nang | đà nẵng | 16.0544 | 108.2022 |
| hai phong | hải phòng | 20.8448 | 106.6880 |
| can tho | cần thơ | 10.0379 | 105.7869 |
| hue | huế | 16.4637 | 107.5909 |
| nha trang | nha trang | 12.2381 | 109.1967 |
| qui nhon | quỳ nhơn | 13.7769 | 109.2260 |

## Usage Examples

### Example 1: Direct Call with Hardcoded City
```java
double[] coords = DistanceCalculator.getCityCoords("Hanoi");
// Returns: [21.0285, 105.8542]
```

### Example 2: Vietnamese Name Variant
```java
double[] coords = DistanceCalculator.getCityCoords("hà nội");
// Returns: [21.0285, 105.8542] (exact match)
```

### Example 3: Mixed Case with Partial Match
```java
double[] coords = DistanceCalculator.getCityCoords("Ho Chi Minh");
// Returns: [10.7769, 106.7009]
```

### Example 4: Unknown City (API Geocoding)
```java
double[] coords = DistanceCalculator.getCityCoords("Phu Quoc");
// Calls Nominatim API
// Returns: [10.2789, 103.9905] (if found)
// Falls back to: [21.0285, 105.8542] (if not found)
```

### Example 5: Error Handling in TripService
```java
try {
    double[] cityCoords = DistanceCalculator.getCityCoords(t.getOrigin());
    double originLat = cityCoords[0];
    double originLng = cityCoords[1];
} catch (Exception e) {
    log.warn("Failed to get coordinates for city: {}, skipping first leg calculation", t.getOrigin(), e);
    originLat = Double.NaN;
    originLng = Double.NaN;
}

// Only calculate if valid coordinates
if (!Double.isNaN(originLat) && !Double.isNaN(originLng)) {
    // Calculate distance using OSRM...
}
```

## Integration with OSRM Distance Calculator

### Flow Diagram
```
TripService.mapToResponse()
    ↓
    IF (userLat != null && userLng != null)
        ↓ [User Location Provided]
        originLat = userLat
        originLng = userLng
    ELSE
        ↓ [Use Trip Origin City]
        getCityCoords(t.getOrigin())
        ↓
        originLat = coords[0]
        originLng = coords[1]
    ↓
    DistanceCalculator.calculateDistance(originLat, originLng, firstStop.lat, firstStop.lng)
        ↓
        OSRM API Call
        ↓
        Return distance in km
```

## Error Handling Hierarchy

```
Level 1: Hardcoded City Map
  ✓ No network latency
  ✓ Fastest lookup
  ✓ Covers 8 major Vietnamese cities

Level 2: Partial Match
  ✓ Flexible city name matching
  ✓ Handles Vietnamese diacritics
  ✓ Handles English aliases

Level 3: Nominatim API Geocoding
  ✓ Works for any city worldwide
  ✓ Network dependent
  ✓ More latency (100-500ms)

Level 4: Default Fallback
  ✓ Always succeeds
  ✓ No exception thrown
  ✓ May not be accurate for user's intended city
```

## Performance Notes

| Method | Latency | Reliability |
|--------|---------|-------------|
| Hardcoded lookup | <1ms | 100% |
| Partial match | <1ms | 100% |
| Nominatim API | 100-500ms | 95%+ |
| Default fallback | <1ms | 100% |

## Logging

The method produces the following log messages:

### Info Level
```
[INFO] Geocoded city {cityName} to coordinates: [{lat}, {lon}]
```

### Debug Level
```
[DEBUG] Found coordinates for city: {cityName}
[DEBUG] Found partial match for city: {cityName} -> {matchedKey}
```

### Warn Level
```
[WARN] City not found in hardcoded map, attempting Nominatim geocoding: {cityName}
[WARN] Falling back to default coordinates (Hanoi)
```

### Error Level
```
[ERROR] Geocoding failed for city: {cityName}
```

## Testing Checklist

- [ ] Test with Vietnamese city names (e.g., "Hà Nội")
- [ ] Test with English variants (e.g., "Hanoi")
- [ ] Test with aliases (e.g., "Saigon" for HCMC)
- [ ] Test with unknown cities (should fall back to Hanoi)
- [ ] Verify logs show correct city matches
- [ ] Test OSRM distance calculation after city coords resolved
- [ ] Verify totalDistanceKm in trip response matches Google Maps
- [ ] Test with user location provided (should skip city geocoding)

## Related Files

- **DistanceCalculator.java** - Contains getCityCoords() implementation
- **TripService.java** - Calls getCityCoords() in mapToResponse()
- **AiConfig.java** - Provides osrmRestTemplate bean
- **OSRM_DISTANCE_FIX_VERIFICATION.md** - Full verification documentation

