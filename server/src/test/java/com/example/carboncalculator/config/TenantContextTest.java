package com.example.carboncalculator.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void deveArmazenarERecuperarOInstitutionIdDaThreadCorrente() {
        TenantContext.setInstitutionId("550e8400-e29b-41d4-a716-446655440000");

        assertEquals("550e8400-e29b-41d4-a716-446655440000", TenantContext.getInstitutionId());
    }

    @Test
    void deveRetornarNuloQuandoNenhumInstitutionIdFoiDefinido() {
        assertNull(TenantContext.getInstitutionId());
    }

    @Test
    void deveLimparOInstitutionIdAposClear() {
        TenantContext.setInstitutionId("550e8400-e29b-41d4-a716-446655440000");

        TenantContext.clear();

        assertNull(TenantContext.getInstitutionId());
    }

    // @spec:AC-010 Acesso direto a laboratório de outra instituição é negado
    @Test
    void naoDeveVazarInstitutionIdEntreThreadsDiferentes() throws InterruptedException {
        TenantContext.setInstitutionId("11111111-1111-1111-1111-111111111111");

        AtomicReference<String> seenOnOtherThread = new AtomicReference<>("valor-nao-substituido");
        Thread other = new Thread(() -> seenOnOtherThread.set(TenantContext.getInstitutionId()));
        other.start();
        other.join();

        assertNull(seenOnOtherThread.get(), "cada thread deve ter seu próprio contexto de tenant");
        assertEquals("11111111-1111-1111-1111-111111111111", TenantContext.getInstitutionId());
    }
}
