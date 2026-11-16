package com.observernotes.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.UUID;

/** Leitura estrita de campos JSON: tipo errado ou campo ausente nunca é aceito em silêncio. */
final class JsonFields {

    private JsonFields() { }

    static JsonElement required(JsonObject o, String name) {
        JsonElement e = o.get(name);
        if (e == null || e.isJsonNull()) {
            throw new InvalidDataException("Campo obrigatório ausente: " + name);
        }
        return e;
    }

    static JsonObject object(JsonObject o, String name) {
        JsonElement e = required(o, name);
        if (!e.isJsonObject()) {
            throw new InvalidDataException("O campo " + name + " deveria ser um objeto");
        }
        return e.getAsJsonObject();
    }

    static JsonArray array(JsonObject o, String name) {
        JsonElement e = required(o, name);
        if (!e.isJsonArray()) {
            throw new InvalidDataException("O campo " + name + " deveria ser uma lista");
        }
        return e.getAsJsonArray();
    }

    static JsonObject asObject(JsonElement e, String context) {
        if (e == null || !e.isJsonObject()) {
            throw new InvalidDataException("Item inválido em " + context + ": esperava um objeto");
        }
        return e.getAsJsonObject();
    }

    static String string(JsonObject o, String name) {
        JsonElement e = required(o, name);
        return asString(e, name);
    }

    static String asString(JsonElement e, String context) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw new InvalidDataException("O campo " + context + " deveria ser texto");
        }
        return e.getAsString();
    }

    static boolean bool(JsonObject o, String name) {
        JsonElement e = required(o, name);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
            throw new InvalidDataException("O campo " + name + " deveria ser verdadeiro ou falso");
        }
        return e.getAsBoolean();
    }

    static int integer(JsonObject o, String name) {
        JsonElement e = required(o, name);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new InvalidDataException("O campo " + name + " deveria ser um número inteiro");
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        try {
            return new BigDecimal(p.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new InvalidDataException("O campo " + name + " deveria ser um número inteiro");
        }
    }

    static UUID uuid(String text, String context) {
        try {
            UUID parsed = UUID.fromString(text);
            if (!parsed.toString().equalsIgnoreCase(text)) {
                throw new IllegalArgumentException("formato não canônico");
            }
            return parsed;
        } catch (IllegalArgumentException e) {
            throw new InvalidDataException("UUID inválido em " + context + ": " + text);
        }
    }
}
