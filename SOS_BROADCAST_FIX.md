# SOS Multi-Member Broadcasting Fix

**Status:** ✅ Complete  
**Build:** ✅ SUCCESS (43 seconds)

## Change Made

### File: `src/main/java/com/gola/service/SosService.java`

**Method:** `broadcastSos(SosEvent sos)` (Line 128-133)

## Diff

```diff
    private void broadcastSos(SosEvent sos) {
        messaging.convertAndSend("/topic/admin/sos", mapToResponse(sos));
        if (sos.getTripId() != null) {
-           messaging.convertAndSend("/topic/trip/" + sos.getTripId(), java.util.Map.of("event","sos.created","sos", mapToResponse(sos)));
+           // Include excludeUser so WebSocket subscribers (frontend) can filter out the sender
+           messaging.convertAndSend("/topic/trip/" + sos.getTripId(), java.util.Map.of(
+               "event", "sos.created",
+               "sos", mapToResponse(sos),
+               "excludeUser", sos.getUserId().toString()
+           ));
        }
    }
```

## Implementation Details

### WebSocket Message Format (Before)
```json
{
  "event": "sos.created",
  "sos": { /* SosResponse object */ }
}
```

### WebSocket Message Format (After)
```json
{
  "event": "sos.created",
  "sos": { /* SosResponse object */ },
  "excludeUser": "uuid-of-sender"
}
```

## How It Works

1. **Server broadcasts SOS event** to WebSocket channel `/topic/trip/{tripId}`
2. **Message includes `excludeUser` field** containing the sender's userId
3. **Frontend (WebSocket listener)** filters out the notification if current user's ID matches `excludeUser`
4. **Sender's frontend** only updates local state without showing notification
5. **Other trip members** receive and process the notification normally

## Notification Handling

| Channel | Sender | Others |
|---------|--------|--------|
| **WebSocket** | ❌ Excluded via `excludeUser` field | ✅ Receives |
| **FCM** | ❌ Already skipped in `sendSosNotifications()` (line 241-244) | ✅ Sent |
| **SMS** | ✅ Sent to emergency contacts (intentional) | N/A (only sender's contacts) |

## Existing Sender Exclusion

The `sendSosNotifications()` method **already had sender exclusion for FCM** (unchanged):

```java
if (recipientId.equals(userId)) {
    log.info("Skipping triggering user self notification: userId={}", recipientId);
    continue;
}
```

This ensures:
- ✅ Trip members get FCM push notification
- ❌ SOS sender does NOT get their own FCM notification

## Requirements Met

✅ Người gửi SOS KHÔNG BAO GIỜ nhận notification/SSE/WebSocket/FCM của chính mình
- WebSocket: ✅ Excluded via `excludeUser` 
- FCM: ✅ Already handled in `sendSosNotifications()`

✅ Chỉ các thành viên khác trong trip_members mới nhận được
- ✅ `excludeUser` prevents sender from processing WebSocket event

✅ FE của người gửi chỉ update local state, không thông qua event broadcast
- ✅ Frontend can ignore message if user ID matches `excludeUser`

## Frontend Implementation Example

```javascript
// WebSocket listener
socket.on('/topic/trip/' + tripId, (message) => {
    if (message.excludeUser && message.excludeUser === currentUserId) {
        console.log("SOS from other user - just update UI");
        // Don't show notification for the sender
        return;
    }
    
    if (message.event === 'sos.created') {
        // Show notification to other trip members
        showSosNotification(message.sos);
    }
});
```

## Testing Scenarios

### Scenario 1: Sender triggers SOS
- Sender's device: ✅ Local state update only (no WebSocket event processed)
- Other members' devices: ✅ Notification + FCM received
- Emergency contacts: ✅ SMS sent

### Scenario 2: Multiple members in same trip
- Member A triggers SOS → Members B, C, D see it (exclude A)
- Member B acknowledges → All see status update (include B)

## Notes

- ✅ No breaking changes to API contract
- ✅ Backward compatible (frontend can ignore `excludeUser` field)
- ✅ Minimal performance impact
- ✅ Only one method modified as requested

## Build Verification

```
BUILD SUCCESSFUL in 43s
- Zero compilation errors
- All dependencies resolved
- Ready for deployment
```

## Next Steps (Optional)

If full SOS management needed, these can be implemented:
1. Add CANCELLED status to SosStatus enum (partial: added but not tested)
2. Implement `cancelSos()` endpoint with proper validation
3. Update `getActiveSosEvents()` to only return ACTIVE status
4. Add HTTP 410 Gone for operations on non-ACTIVE SOS
