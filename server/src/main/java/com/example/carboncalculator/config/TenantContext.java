package com.example.carboncalculator.config;

/**
 * Guarda, em um ThreadLocal, a instituição (tenant) ativa da requisição HTTP
 * corrente. É preenchido pelo {@link TenantFilter} e lido por qualquer
 * componente (service, repository) que precise saber qual instituição está
 * no contexto — sem precisar recebê-la como parâmetro em toda a cadeia.
 */
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
