package com.observernotes.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.observernotes.domain.DomainRestorer;
import com.observernotes.domain.DomainState;
import com.observernotes.domain.InvalidStateException;
import com.observernotes.domain.TaskId;
import com.observernotes.domain.TaskView;
import com.observernotes.observer.impl.HistoryEntry;
import com.observernotes.observer.impl.HistoryObserver;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Converte {@link AppSnapshot} de e para JSON (formato versão 1).
 *
 * <pre>
 * { "formatVersion": 1, "savedAt": "...",
 *   "tasks": [ {"id","title","completed","scored"} ],
 *   "achievements": { "scoredTaskIds": [ ... ] },     // única fonte de verdade
 *   "history": [ {"at","kind","taskId","title"} ] }
 * </pre>
 *
 * Marcos, estrelas e medalhas <b>não</b> são gravados: são derivados da posição
 * em {@code scoredTaskIds}, então não existe um segundo número que possa discordar.
 */
final class StateJsonMapper {

    private StateJsonMapper() { }

    static JsonObject toJson(AppSnapshot snapshot, Instant savedAt) {
        JsonObject root = new JsonObject();
        root.addProperty("savedAt", savedAt.toString());

        JsonArray tasks = new JsonArray();
        for (TaskView t : snapshot.domain().tasks()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", t.id().toString());
            o.addProperty("title", t.title());
            o.addProperty("completed", t.completed());
            o.addProperty("scored", t.scored());
            tasks.add(o);
        }
        root.add("tasks", tasks);

        JsonArray scored = new JsonArray();
        snapshot.domain().scoredTaskIds().forEach(id -> scored.add(id.toString()));
        JsonObject achievements = new JsonObject();
        achievements.add("scoredTaskIds", scored);
        root.add("achievements", achievements);

        JsonArray history = new JsonArray();
        for (HistoryEntry h : snapshot.history()) {
            JsonObject o = new JsonObject();
            o.addProperty("at", h.at().toString());
            o.addProperty("kind", h.kind().name());
            o.addProperty("taskId", h.taskId().toString());
            o.addProperty("title", h.taskTitle());
            history.add(o);
        }
        root.add("history", history);
        root.addProperty("taskPage", snapshot.taskPage());
        return root;
    }

    /** @throws InvalidDataException se algo não for aceitável; nada é aplicado parcialmente */
    static AppSnapshot fromJson(JsonObject root) {
        List<TaskView> tasks = new ArrayList<>();
        JsonArray taskArray = JsonFields.array(root, "tasks");
        for (int i = 0; i < taskArray.size(); i++) {
            JsonObject o = JsonFields.asObject(taskArray.get(i), "tasks[" + i + "]");
            tasks.add(new TaskView(
                    new TaskId(JsonFields.uuid(JsonFields.string(o, "id"), "tasks[" + i + "].id")),
                    JsonFields.string(o, "title"),
                    JsonFields.bool(o, "completed"),
                    JsonFields.bool(o, "scored")));
        }

        List<TaskId> scoredIds = new ArrayList<>();
        JsonArray scoredArray = JsonFields.array(JsonFields.object(root, "achievements"), "scoredTaskIds");
        for (int i = 0; i < scoredArray.size(); i++) {
            String raw = JsonFields.asString(scoredArray.get(i), "scoredTaskIds[" + i + "]");
            scoredIds.add(new TaskId(JsonFields.uuid(raw, "scoredTaskIds[" + i + "]")));
        }

        // Valida as regras do domínio antes de aceitar qualquer coisa.
        DomainState domain = new DomainState(tasks, scoredIds);
        try {
            DomainRestorer.restore(domain);
        } catch (InvalidStateException e) {
            throw new InvalidDataException(e.getMessage(), e);
        }

        List<HistoryEntry> history = new ArrayList<>();
        JsonArray historyArray = JsonFields.array(root, "history");
        for (int i = 0; i < historyArray.size(); i++) {
            // O histórico é só um registro: se passar do limite, mantém os mais recentes.
            if (history.size() >= HistoryObserver.MAX_ENTRIES) {
                break;
            }
            JsonElement element = historyArray.get(i);
            history.add(historyEntry(JsonFields.asObject(element, "history[" + i + "]"), i));
        }
        int taskPage = root.has("taskPage") ? JsonFields.integer(root, "taskPage") : 0;
        if (taskPage < 0) {
            throw new InvalidDataException("A página de tarefas não pode ser negativa");
        }
        return new AppSnapshot(domain, history, taskPage);
    }

    private static HistoryEntry historyEntry(JsonObject o, int index) {
        String where = "history[" + index + "]";
        try {
            return new HistoryEntry(
                    Instant.parse(JsonFields.string(o, "at")),
                    HistoryEntry.Kind.valueOf(JsonFields.string(o, "kind")),
                    new TaskId(JsonFields.uuid(JsonFields.string(o, "taskId"), where + ".taskId")),
                    JsonFields.string(o, "title"));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            if (e instanceof InvalidDataException) {
                throw e;
            }
            throw new InvalidDataException("Valor inválido em " + where + ": " + e.getMessage());
        }
    }
}
