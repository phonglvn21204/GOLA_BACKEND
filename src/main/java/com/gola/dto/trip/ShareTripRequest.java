package com.gola.dto.trip;
import com.gola.entity.enums.ShareScope;
import lombok.Data;
@Data public class ShareTripRequest {
    private ShareScope scope = ShareScope.VIEW;
    private int ttlDays = 7;
}