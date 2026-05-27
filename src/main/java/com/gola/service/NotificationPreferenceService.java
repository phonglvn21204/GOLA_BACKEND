package com.gola.service;

import com.gola.entity.NotificationPreference;
import com.gola.entity.enums.NotifType;
import com.gola.entity.enums.NotificationChannel;
import com.gola.exception.GolaException;
import com.gola.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository prefRepo;

    public List<NotificationPreference> getPreferences(UUID userId) {
        return prefRepo.findByUserId(userId);
    }

    @Transactional
    public void setPreference(UUID userId, String type, NotificationChannel channel, boolean isEnabled) {
        NotifType notifType;
        try {
            notifType = NotifType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw GolaException.badRequest("Invalid notification type: " + type);
        }

        NotificationPreference pref = prefRepo.findByUserIdAndType(userId, notifType)
                .orElseGet(() -> NotificationPreference.builder()
                        .userId(userId)
                        .type(notifType)
                        .build());
        
        pref.setChannel(channel);
        pref.setEnabled(isEnabled);
        prefRepo.save(pref);
    }
}
