package com.gola;

import java.util.UUID;

/** IDs and emails from Flyway V999 seed data. */
public final class TestDataConstants {
    private TestDataConstants() {}

    public static final String SEED_USER_EMAIL = "linh.nguyen@example.com";
    public static final String SEED_USER2_EMAIL = "minh.tran@example.com";
    public static final String SEED_ADMIN_EMAIL = "admin@gola.app";
    public static final String SEED_PASSWORD = "Password123!";

    public static final UUID SEED_USER_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    public static final UUID SEED_USER2_ID = UUID.fromString("10000000-0000-4000-8000-000000000002");
    public static final UUID SEED_ADMIN_ID = UUID.fromString("10000000-0000-4000-8000-000000000011");

    public static final UUID SEED_TRIP_ID = UUID.fromString("90000000-0000-4000-8000-000000000001");
    public static final UUID SEED_TRIP2_ID = UUID.fromString("90000000-0000-4000-8000-000000000002");
    public static final UUID SEED_POST_ID = UUID.fromString("96000000-0000-4000-8000-000000000001");
    public static final UUID SEED_QUEST_ID = UUID.fromString("70000000-0000-4000-8000-000000000001");
    /** Quest with IN_PROGRESS progress for {@link #SEED_USER_ID} (not completed). */
    public static final UUID SEED_QUEST_IN_PROGRESS_ID = UUID.fromString("70000000-0000-4000-8000-000000000009");
    public static final UUID SEED_POST_FOR_HIDE_ID = UUID.fromString("96000000-0000-4000-8000-000000000008");
    public static final UUID SEED_PLACE_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");
    public static final UUID SEED_ALBUM_ID = UUID.fromString("99000000-0000-4000-8000-000000000001");
    public static final UUID SEED_REWARD_ID = UUID.fromString("62000000-0000-4000-8000-000000000001");
    public static final UUID SEED_ACTIVE_SESSION_ID = UUID.fromString("94000000-0000-4000-8000-000000000002");
}
