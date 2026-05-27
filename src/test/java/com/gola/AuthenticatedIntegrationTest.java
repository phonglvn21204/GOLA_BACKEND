package com.gola;

import org.junit.jupiter.api.BeforeEach;

/** Logs in as seed user {@link TestDataConstants#SEED_USER_EMAIL} before each test. */
public abstract class AuthenticatedIntegrationTest extends BaseIntegrationTest {

    @BeforeEach
    void authenticateSeedUser() throws Exception {
        loginAsSeedUser();
    }
}
