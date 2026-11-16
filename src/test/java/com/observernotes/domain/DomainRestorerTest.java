package com.observernotes.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DomainRestorerTest {

    private static TaskView task(String title, boolean completed, boolean scored) {
        return new TaskView(TaskId.random(), title, completed, scored);
    }

    private static List<TaskId> ids(int n) {
        List<TaskId> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(TaskId.random());
        }
        return out;
    }

    @Test
    @DisplayName("restaura tarefas, UUIDs, estados e o ledger exatamente como estavam")
    void restoresExactly() {
        TaskView done = task("a", true, true);
        TaskView open = task("b", false, false);
        DomainState state = new DomainState(List.of(done, open), List.of(done.id()));
        DomainRestorer.Restored r = DomainRestorer.restore(state);
        assertEquals(List.of(done, open), r.board().tasks());
        assertEquals(List.of(done.id()), r.ledger().scoredTaskIds());
        assertEquals(1, r.ledger().totalScored());
    }

    @Test
    @DisplayName("tarefa restaurada como já pontuada nunca pontua de novo")
    void restoredTaskCannotScoreAgain() {
        TaskView done = task("a", true, true);
        DomainRestorer.Restored r = DomainRestorer.restore(new DomainState(List.of(done), List.of(done.id())));
        assertTrue(r.board().uncomplete(done.id()).isPresent());
        CompletionResult again = r.board().complete(done.id()).orElseThrow();
        assertFalse(again.firstCompletion());
        assertTrue(r.ledger().register(done.id()).isEmpty());
        assertEquals(1, r.ledger().totalScored());
    }

    @Test
    @DisplayName("a progressão continua exatamente do ponto em que parou")
    void progressionContinues() {
        DomainRestorer.Restored r = DomainRestorer.restore(new DomainState(List.of(), ids(3)));
        assertEquals(Milestone.BRONZE_MEDAL, r.ledger().register(TaskId.random()).orElseThrow());
        assertEquals(Milestone.STAR_4, r.ledger().register(TaskId.random()).orElseThrow());
    }

    @Test
    @DisplayName("restaura a conquista máxima e não concede mais nada")
    void restoresMax() {
        DomainRestorer.Restored r = DomainRestorer.restore(new DomainState(List.of(), ids(16)));
        assertTrue(r.ledger().isComplete());
        assertTrue(r.ledger().register(TaskId.random()).isEmpty());
        assertEquals(16, r.ledger().totalScored());
    }

    @Test
    @DisplayName("na conquista máxima, tarefa pontuada fora do ledger é aceita")
    void scoredTaskOutsideLedgerAtMax() {
        TaskView late = task("a", true, true);
        assertDoesNotThrow(() -> DomainRestorer.restore(new DomainState(List.of(late), ids(16))));
    }

    @Test
    @DisplayName("rejeita tarefas com identificador duplicado")
    void rejectsDuplicateTaskIds() {
        TaskView a = task("a", false, false);
        assertThrows(InvalidStateException.class,
                () -> DomainRestorer.restore(new DomainState(List.of(a, a), List.of())));
    }

    @Test
    @DisplayName("rejeita duplicata no ledger (daria ponto em dobro)")
    void rejectsDuplicateLedgerIds() {
        TaskId id = TaskId.random();
        assertThrows(InvalidStateException.class,
                () -> DomainRestorer.restore(new DomainState(List.of(), List.of(id, id))));
    }

    @Test
    @DisplayName("rejeita mais que o limite de segurança e mais de 16 pontuações")
    void rejectsOverLimits() {
        List<TaskView> tooMany = new ArrayList<>();
        for (int i = 0; i <= TaskBoard.MAX_TASKS; i++) {
            tooMany.add(task("t" + i, false, false));
        }
        assertThrows(InvalidStateException.class,
                () -> DomainRestorer.restore(new DomainState(tooMany, List.of())));
        assertThrows(InvalidStateException.class,
                () -> DomainRestorer.restore(new DomainState(List.of(), ids(17))));
    }

    @Test
    @DisplayName("rejeita tarefa concluída que nunca pontuou")
    void rejectsCompletedButUnscored() {
        TaskView bad = task("a", true, false);
        assertThrows(InvalidStateException.class,
                () -> DomainRestorer.restore(new DomainState(List.of(bad), List.of())));
    }

    @Test
    @DisplayName("rejeita ledger que cita tarefa existente marcada como não pontuada")
    void rejectsLedgerEntryOfUnscoredTask() {
        TaskView open = task("a", false, false);
        assertThrows(InvalidStateException.class,
                () -> DomainRestorer.restore(new DomainState(List.of(open), List.of(open.id()))));
    }

    @Test
    @DisplayName("rejeita tarefa pontuada ausente do ledger (ponto perdido) antes do máximo")
    void rejectsLostPoint() {
        TaskView scored = task("a", false, true);
        assertThrows(InvalidStateException.class,
                () -> DomainRestorer.restore(new DomainState(List.of(scored), ids(2))));
    }

    @Test
    @DisplayName("rejeita título inválido")
    void rejectsInvalidTitle() {
        TaskView bad = task("   ", false, false);
        assertThrows(InvalidStateException.class,
                () -> DomainRestorer.restore(new DomainState(List.of(bad), List.of())));
    }

    @Test
    @DisplayName("identificador que já pontuou nunca é reemitido, mesmo com a tarefa excluída")
    void scoredIdIsNeverReissued() {
        TaskId scored = TaskId.random();
        TaskBoard board = TaskBoard.restore(List.of(), List.of(scored), scored::value);
        assertThrows(IllegalStateException.class, () -> board.add("nova"));
        assertEquals(0, board.size());
    }

    @Test
    @DisplayName("tarefas restauradas contam para o limite de segurança")
    void restoredTasksCountForLimit() {
        List<TaskView> maximum = new ArrayList<>();
        for (int i = 0; i < TaskBoard.MAX_TASKS; i++) {
            maximum.add(task("t" + i, false, false));
        }
        TaskBoard board = DomainRestorer.restore(new DomainState(maximum, List.of())).board();
        assertThrows(BoardFullException.class, () -> board.add("extra"));
    }
}
