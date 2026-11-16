package com.observernotes.domain;

/**
 * Reconstrói o domínio a partir de uma {@link DomainState} salva, rejeitando qualquer
 * dado inconsistente <em>antes</em> de aplicá-lo. Restaurar nunca publica eventos nem
 * concede pontos: o ledger volta exatamente como estava.
 */
public final class DomainRestorer {

    public record Restored(TaskBoard board, AchievementLedger ledger) { }

    private DomainRestorer() { }

    /** @throws InvalidStateException se os dados forem inválidos ou inconsistentes entre si */
    public static Restored restore(DomainState state) {
        AchievementLedger ledger = AchievementLedger.restore(state.scoredTaskIds());
        TaskBoard board = TaskBoard.restore(state.tasks(), ledger.scoredTaskIds());

        boolean ledgerFull = ledger.isComplete();
        for (TaskView task : state.tasks()) {
            boolean inLedger = ledger.hasScored(task.id());
            if (inLedger && !task.scored()) {
                throw new InvalidStateException(
                        "O ledger diz que a tarefa pontuou, mas ela não está marcada como pontuada: " + task.id());
            }
            // Antes da conquista máxima, toda tarefa marcada como pontuada tem que estar no ledger.
            if (!ledgerFull && task.scored() && !inLedger) {
                throw new InvalidStateException(
                        "Tarefa marcada como pontuada, mas ausente do ledger (ponto perdido): " + task.id());
            }
        }
        return new Restored(board, ledger);
    }
}
