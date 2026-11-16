package com.observernotes.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Agregado que guarda as tarefas (no máximo {@value #MAX_TASKS}, concluídas ou não)
 * e aplica as regras de cada transição. Não publica eventos e não pontua:
 * apenas informa o que aconteceu. Não é thread-safe; o serviço serializa o acesso.
 */
public final class TaskBoard {

    /** Limite de segurança do arquivo; a interface distribui estas tarefas em páginas de seis. */
    public static final int MAX_TASKS = 600;
    public static final int MAX_TITLE_LENGTH = 60;

    private final Map<TaskId, Task> tasks = new LinkedHashMap<>();
    /** Todo identificador já emitido, mesmo de tarefas excluídas: nunca é reaproveitado. */
    private final Set<TaskId> issuedIds = new HashSet<>();
    private final Supplier<UUID> idGenerator;

    public TaskBoard() {
        this(UUID::randomUUID);
    }

    /** Construtor para testes: permite um gerador de UUID controlado. */
    public TaskBoard(Supplier<UUID> idGenerator) {
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    /**
     * Reconstrói o tabuleiro a partir de dados salvos, validando-os. Nenhum evento é
     * gerado e nenhuma pontuação é concedida.
     *
     * @param retiredIds identificadores que nunca podem ser reemitidos (os que já pontuaram)
     * @throws InvalidStateException se os dados violarem as regras do tabuleiro
     */
    public static TaskBoard restore(List<TaskView> views, Collection<TaskId> retiredIds) {
        return restore(views, retiredIds, UUID::randomUUID);
    }

    public static TaskBoard restore(List<TaskView> views, Collection<TaskId> retiredIds,
                                    Supplier<UUID> idGenerator) {
        Objects.requireNonNull(views, "views");
        Objects.requireNonNull(retiredIds, "retiredIds");
        if (views.size() > MAX_TASKS) {
            throw new InvalidStateException("Mais de " + MAX_TASKS + " tarefas: " + views.size());
        }
        TaskBoard board = new TaskBoard(idGenerator);
        for (TaskView v : views) {
            if (v == null || v.id() == null) {
                throw new InvalidStateException("Tarefa sem identificador");
            }
            String title;
            try {
                title = normalize(v.title());
            } catch (IllegalArgumentException e) {
                throw new InvalidStateException("Título inválido na tarefa " + v.id() + ": " + e.getMessage());
            }
            if (v.completed() && !v.scored()) {
                throw new InvalidStateException("Tarefa concluída que nunca pontuou: " + v.id());
            }
            if (board.tasks.containsKey(v.id())) {
                throw new InvalidStateException("Tarefa duplicada: " + v.id());
            }
            board.tasks.put(v.id(), Task.restored(v.id(), title, v.completed(), v.scored()));
            board.issuedIds.add(v.id());
        }
        for (TaskId retired : retiredIds) {
            board.issuedIds.add(retired);
        }
        return board;
    }

    public TaskView add(String rawTitle) {
        String title = normalize(rawTitle);
        if (tasks.size() >= MAX_TASKS) {
            throw new BoardFullException(MAX_TASKS);
        }
        TaskId id = new TaskId(idGenerator.get());
        if (!issuedIds.add(id)) {
            throw new IllegalStateException("Identificador já emitido: " + id);
        }
        Task task = new Task(id, title);
        tasks.put(id, task);
        return task.view();
    }

    /** @return vazio se a tarefa não existe ou já estava concluída (chamada repetida). */
    public Optional<CompletionResult> complete(TaskId id) {
        Task task = tasks.get(id);
        if (task == null || !task.complete()) {
            return Optional.empty();
        }
        boolean first = task.consumeFirstCompletion();
        return Optional.of(new CompletionResult(task.view(), first));
    }

    /** @return vazio se a tarefa não existe ou já estava pendente. */
    public Optional<TaskView> uncomplete(TaskId id) {
        Task task = tasks.get(id);
        if (task == null || !task.uncomplete()) {
            return Optional.empty();
        }
        return Optional.of(task.view());
    }

    /** @return a tarefa como estava ao ser removida, ou vazio se não existe. */
    public Optional<TaskView> remove(TaskId id) {
        Task task = tasks.remove(id);
        return Optional.ofNullable(task).map(Task::view);
    }

    public Optional<TaskView> find(TaskId id) {
        return Optional.ofNullable(tasks.get(id)).map(Task::view);
    }

    public List<TaskView> tasks() {
        List<TaskView> out = new ArrayList<>(tasks.size());
        for (Task t : tasks.values()) {
            out.add(t.view());
        }
        return List.copyOf(out);
    }

    public int size() {
        return tasks.size();
    }

    public int completedCount() {
        int n = 0;
        for (Task t : tasks.values()) {
            if (t.view().completed()) {
                n++;
            }
        }
        return n;
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("O título da tarefa não pode ser vazio");
        }
        String title = raw.strip();
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("O título excede " + MAX_TITLE_LENGTH + " caracteres");
        }
        return title;
    }
}
