package com.observernotes.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.observernotes.settings.ObserverKind;
import com.observernotes.settings.ObserverPreferences;

/**
 * Formato versão 1 de {@code settings.json}:
 * {@code { "formatVersion": 1, "observers": { "HISTORY": true, ... } }}.
 * Observador ausente = ativo. Chave desconhecida é ignorada (compatibilidade futura).
 */
final class SettingsJsonMapper {

    private SettingsJsonMapper() { }

    static JsonObject toJson(ObserverPreferences prefs) {
        JsonObject observers = new JsonObject();
        for (ObserverKind k : ObserverKind.values()) {
            observers.addProperty(k.name(), prefs.isEnabled(k));
        }
        JsonObject root = new JsonObject();
        root.add("observers", observers);
        return root;
    }

    static ObserverPreferences fromJson(JsonObject root) {
        JsonObject observers = JsonFields.object(root, "observers");
        ObserverPreferences prefs = ObserverPreferences.allEnabled();
        for (ObserverKind k : ObserverKind.values()) {
            JsonElement e = observers.get(k.name());
            if (e == null) {
                continue;
            }
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
                throw new InvalidDataException("O observador " + k + " deveria ser verdadeiro ou falso");
            }
            prefs = prefs.with(k, e.getAsBoolean());
        }
        return prefs;
    }
}
