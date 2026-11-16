package com.observernotes.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Livro-razão das conquistas: guarda os identificadores das tarefas que já
 * pontuaram, independente de elas ainda existirem. Excluir uma tarefa não
 * altera o ledger. É a única autoridade de pontuação do sistema.
 */
public final class AchievementLedger {

    private final Set<TaskId> scoredTasks = new LinkedHashSet<>();

    /**
     * Reconstrói o ledger a partir dos identificadores salvos, na ordem em que pontuaram
     * (a posição define o número do marco). Não concede nada: só valida e restaura.
     *
     * @throws InvalidStateException se houver nulo, duplicata ou mais de 16 registros
     */
    public static AchievementLedger restore(Collection<TaskId> orderedIds) {
        Objects.requireNonNull(orderedIds, "orderedIds");
        if (orderedIds.size() > Milestone.COUNT) {
            throw new InvalidStateException("Mais de " + Milestone.COUNT + " pontuações: " + orderedIds.size());
        }
        AchievementLedger ledger = new AchievementLedger();
        for (TaskId id : orderedIds) {
            if (id == null) {
                throw new InvalidStateException("Identificador nulo no ledger");
            }
            if (!ledger.scoredTasks.add(id)) {
                throw new InvalidStateException("Pontuação duplicada no ledger: " + id);
            }
        }
        return ledger;
    }

    /** Identificadores que pontuaram, na ordem. É a única fonte de verdade das conquistas. */
    public List<TaskId> scoredTaskIds() {
        return List.copyOf(scoredTasks);
    }

    /**
     * Registra a primeira conclusão de uma tarefa.
     *
     * @return o marco conquistado; vazio se a tarefa já pontuou antes ou se a
     *         conquista máxima já foi alcançada (nada mais é concedido)
     */
    public Optional<Milestone> register(TaskId taskId) {
        Objects.requireNonNull(taskId, "taskId");
        if (isComplete() || !scoredTasks.add(taskId)) {
            return Optional.empty();
        }
        return Milestone.forNumber(scoredTasks.size());
    }

    public boolean hasScored(TaskId taskId) {
        return scoredTasks.contains(taskId);
    }

    /** Total histórico de conclusões inéditas. Não é o número de tarefas concluídas agora. */
    public int totalScored() {
        return scoredTasks.size();
    }

    public boolean isComplete() {
        return scoredTasks.size() >= Milestone.COUNT;
    }

    public AchievementProgress progress() {
        return AchievementProgress.of(scoredTasks.size());
    }
}
