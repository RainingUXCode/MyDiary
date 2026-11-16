package com.observernotes.domain;

import java.util.List;

/**
 * Foto do que o domínio precisa para ser reconstruído: as tarefas e, separadamente,
 * os identificadores que pontuaram (a única fonte de verdade das conquistas;
 * marcos e estrelas são derivados da posição nessa lista).
 */
public record DomainState(List<TaskView> tasks, List<TaskId> scoredTaskIds) {

    public DomainState {
        tasks = List.copyOf(tasks);
        scoredTaskIds = List.copyOf(scoredTaskIds);
    }

    public static DomainState empty() {
        return new DomainState(List.of(), List.of());
    }
}
