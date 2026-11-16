package com.observernotes.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AchievementLedgerTest {

    private AchievementLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new AchievementLedger();
    }

    @Test
    @DisplayName("cada tarefa nova avança exatamente um marco, na ordem")
    void advancesOneMilestoneAtATime() {
        for (Milestone expected : Milestone.values()) {
            Optional<Milestone> got = ledger.register(TaskId.random());
            assertEquals(Optional.of(expected), got);
            assertEquals(expected.number(), ledger.totalScored());
        }
        assertTrue(ledger.isComplete());
    }

    @Test
    @DisplayName("registrar a mesma tarefa de novo não pontua")
    void duplicateIsIgnored() {
        TaskId id = TaskId.random();
        assertTrue(ledger.register(id).isPresent());
        for (int i = 0; i < 10; i++) {
            assertTrue(ledger.register(id).isEmpty());
        }
        assertEquals(1, ledger.totalScored());
        assertTrue(ledger.hasScored(id));
    }

    @Test
    @DisplayName("depois da conquista máxima nada mais é concedido")
    void nothingAfterMax() {
        for (int i = 0; i < 16; i++) {
            ledger.register(TaskId.random());
        }
        TaskId extra = TaskId.random();
        assertTrue(ledger.register(extra).isEmpty());
        assertTrue(ledger.register(TaskId.random()).isEmpty());
        assertEquals(16, ledger.totalScored());
        assertFalse(ledger.hasScored(extra));
        assertTrue(ledger.progress().isMax());
    }

    @Test
    @DisplayName("o progresso acompanha o total")
    void progressMatchesTotal() {
        ledger.register(TaskId.random());
        ledger.register(TaskId.random());
        assertEquals(2, ledger.progress().totalScored());
        assertEquals(2, ledger.progress().starsEarned());
    }
}
