package com.gola;

import org.junit.jupiter.api.BeforeEach;

/** Logs in as seed admin {@link TestDataConstants#SEED_ADMIN_EMAIL} before each test. */
public abstract class AdminIntegrationTest extends BaseIntegrationTest {

    @BeforeEach
    void authenticateAdmin() throws Exception {
        loginAsAdmin();
    }
}
