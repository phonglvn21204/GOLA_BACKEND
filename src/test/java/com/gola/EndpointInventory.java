package com.gola;

/**
 * Catalog of REST endpoints discovered from {@code com.gola.controller} (context path {@code /api}).
 */
public final class EndpointInventory {
    private EndpointInventory() {}

    public static final int TOTAL_ENDPOINTS = 75;
    public static final int TESTED_ENDPOINTS = 62;
    public static final int SKIPPED_ENDPOINTS = 13;

    /**
     * Skipped: Stripe webhook, AI (external policy), auth reset-password (needs Redis token),
     * destructive DELETE on seed entities, quest proof submit (needs media), SOS resolve (mutates seed),
     * admin incident status patch (covered indirectly), trip stop DELETE, expense/note/album DELETE.
     */
}
