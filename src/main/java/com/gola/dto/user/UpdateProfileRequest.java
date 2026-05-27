package com.gola.dto.user;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String displayName;
    private String avatarUrl;
    private String bio;
    private String locale;
    private String theme;
    private String homeCity;
    private Boolean isPublic;
    private String phone;
}
