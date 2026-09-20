package com.example.carboncalculator.config;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_INSTITUTION_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setInstitutionId(String institutionId) {
        CURRENT_INSTITUTION_ID.set(institutionId);
    }

    public static String getInstitutionId() {
        return CURRENT_INSTITUTION_ID.get();
    }

    public static void clear() {
        CURRENT_INSTITUTION_ID.remove();
    }
}
