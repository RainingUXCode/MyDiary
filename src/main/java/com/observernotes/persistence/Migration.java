package com.observernotes.persistence;

import com.google.gson.JsonObject;

/** Converte o JSON de uma versão do formato para a seguinte, antes da leitura. */
public interface Migration {

    int from();

    int to();

    JsonObject apply(JsonObject root);
}
