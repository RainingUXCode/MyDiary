package com.observernotes.domain;

/**
 * Entidade tarefa. Visível só dentro do pacote: quem muda o estado é o
 * {@link TaskBoard}, que garante as regras do conjunto.
 */
final class Task {

    private final TaskId id;
    private final String title;
    private boolean completed;
    private boolean scored;

    Task(TaskId id, String title) {
        this.id = id;
        this.title = title;
    }

    /** Recria uma tarefa a partir de dados salvos (já validados pelo {@link TaskBoard}). */
    static Task restored(TaskId id, String title, boolean completed, boolean scored) {
        Task task = new Task(id, title);
        task.completed = completed;
        task.scored = scored;
        return task;
    }

    /** @return {@code true} se o estado mudou (estava pendente). */
    boolean complete() {
        if (completed) {
            return false;
        }
        completed = true;
        return true;
    }

    /** @return {@code true} se o estado mudou (estava concluída). */
    boolean uncomplete() {
        if (!completed) {
            return false;
        }
        completed = false;
        return true;
    }

    /**
     * Consome a primeira conclusão. Só retorna {@code true} uma vez na vida da
     * tarefa; desmarcar e marcar de novo nunca repete.
     */
    boolean consumeFirstCompletion() {
        if (scored) {
            return false;
        }
        scored = true;
        return true;
    }

    TaskView view() {
        return new TaskView(id, title, completed, scored);
    }
}
