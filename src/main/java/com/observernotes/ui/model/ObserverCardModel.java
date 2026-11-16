package com.observernotes.ui.model;

import com.observernotes.ui.assets.Asset;
import java.util.Objects;

/** Um cartão do painel de observadores; {@code key} identifica o observador no clique do interruptor. */
public record ObserverCardModel(String key, Asset icon, String title, String description, boolean enabled) {
    public ObserverCardModel {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
    }

    public ObserverCardModel(Asset icon, String title, String description, boolean enabled) {
        this(title, icon, title, description, enabled);
    }
}
