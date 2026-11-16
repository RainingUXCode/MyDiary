package com.observernotes.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskBoardTest {

    private TaskBoard board;

    @BeforeEach
    void setUp() {
        board = new TaskBoard();
    }

    @Test
    @DisplayName("cria tarefa com UUID e título sem espaços nas pontas")
    void createsTask() {
        TaskView t = board.add("  Escrever o README  ");
        assertEquals("Escrever o README", t.title());
        assertNotNull(t.id().value());
        assertFalse(t.completed());
        assertFalse(t.scored());
        assertEquals(1, board.size());
    }

    @Test
    @DisplayName("identificadores são únicos")
    void idsAreUnique() {
        Set<TaskId> ids = new HashSet<>();
        for (int i = 0; i < TaskBoard.MAX_TASKS; i++) {
            ids.add(board.add("t" + i).id());
        }
        assertEquals(TaskBoard.MAX_TASKS, ids.size());
    }

    @Test
    @DisplayName("rejeita título nulo, vazio, em branco ou longo demais")
    void rejectsInvalidTitles() {
        assertThrows(IllegalArgumentException.class, () -> board.add(null));
        assertThrows(IllegalArgumentException.class, () -> board.add(""));
        assertThrows(IllegalArgumentException.class, () -> board.add("   "));
        assertThrows(IllegalArgumentException.class, () -> board.add("x".repeat(TaskBoard.MAX_TITLE_LENGTH + 1)));
        assertEquals(0, board.size());
        assertDoesNotThrow(() -> board.add("x".repeat(TaskBoard.MAX_TITLE_LENGTH)));
    }

    @Test
    @DisplayName("limite de segurança vale para pendentes e concluídas")
    void limitsToMaximumTasks() {
        for (int i = 0; i < TaskBoard.MAX_TASKS; i++) {
            TaskView t = board.add("t" + i);
            if (i % 2 == 0) {
                board.complete(t.id());
            }
        }
        assertEquals(TaskBoard.MAX_TASKS, board.size());
        assertThrows(BoardFullException.class, () -> board.add("extra"));
        assertEquals(TaskBoard.MAX_TASKS, board.size());
    }

    @Test
    @DisplayName("com todas concluídas o limite continua valendo")
    void limitHoldsWhenAllCompleted() {
        for (int i = 0; i < TaskBoard.MAX_TASKS; i++) {
            board.complete(board.add("t" + i).id());
        }
        assertEquals(TaskBoard.MAX_TASKS, board.completedCount());
        assertThrows(BoardFullException.class, () -> board.add("extra"));
    }

    @Test
    @DisplayName("excluir libera uma vaga")
    void removeFreesSlot() {
        TaskView first = board.add("a");
        for (int i = 1; i < TaskBoard.MAX_TASKS; i++) {
            board.add("t" + i);
        }
        assertTrue(board.remove(first.id()).isPresent());
        assertDoesNotThrow(() -> board.add("nova"));
        assertEquals(TaskBoard.MAX_TASKS, board.size());
    }

    @Test
    @DisplayName("conclui e desmarca")
    void completeAndUncomplete() {
        TaskView t = board.add("a");
        assertTrue(board.complete(t.id()).orElseThrow().task().completed());
        assertEquals(1, board.completedCount());
        assertFalse(board.uncomplete(t.id()).orElseThrow().completed());
        assertEquals(0, board.completedCount());
    }

    @Test
    @DisplayName("concluir de novo uma tarefa já concluída não faz nada")
    void completeIsIdempotent() {
        TaskView t = board.add("a");
        assertTrue(board.complete(t.id()).isPresent());
        assertTrue(board.complete(t.id()).isEmpty());
        assertTrue(board.uncomplete(t.id()).isPresent());
        assertTrue(board.uncomplete(t.id()).isEmpty());
    }

    @Test
    @DisplayName("só a primeira conclusão é inédita, mesmo após desmarcar")
    void firstCompletionHappensOnce() {
        TaskView t = board.add("a");
        assertTrue(board.complete(t.id()).orElseThrow().firstCompletion());
        board.uncomplete(t.id());
        CompletionResult again = board.complete(t.id()).orElseThrow();
        assertFalse(again.firstCompletion());
        assertTrue(again.task().scored());
    }

    @Test
    @DisplayName("identificadores desconhecidos são ignorados")
    void unknownIdIsIgnored() {
        TaskId ghost = TaskId.random();
        assertTrue(board.complete(ghost).isEmpty());
        assertTrue(board.uncomplete(ghost).isEmpty());
        assertTrue(board.remove(ghost).isEmpty());
        assertEquals(Optional.empty(), board.find(ghost));
    }

    @Test
    @DisplayName("nunca reaproveita um identificador já emitido, mesmo após excluir")
    void neverReusesIssuedId() {
        UUID fixed = UUID.randomUUID();
        TaskBoard rigged = new TaskBoard(() -> fixed);
        TaskView first = rigged.add("a");
        rigged.remove(first.id());
        assertThrows(IllegalStateException.class, () -> rigged.add("b"));
        assertEquals(0, rigged.size());
    }

    @Test
    @DisplayName("a foto devolvida não muda quando a tarefa muda depois")
    void viewIsSnapshot() {
        TaskView before = board.add("a");
        board.complete(before.id());
        assertFalse(before.completed());
        assertTrue(board.find(before.id()).orElseThrow().completed());
    }
}
