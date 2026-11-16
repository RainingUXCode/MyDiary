package com.observernotes.ui.model;

import com.observernotes.ui.assets.Asset;

/**
 * Aparência do ícone de Conquistas no resumo. {@link #NONE} é a roseta vazia e só deve ser usada
 * enquanto não houver nenhuma conquista; os demais usam as medalhas já fornecidas.
 */
public enum AchievementTier {
    NONE(Asset.ACH_ROSETTE_EMPTY),
    /** Já há estrelas, mas ainda nenhuma medalha. */
    STARS(Asset.ACH_STAR_ON),
    BRONZE(Asset.ACH_MEDAL_BRONZE),
    SILVER(Asset.ACH_MEDAL_SILVER),
    GOLD(Asset.ACH_MEDAL_GOLD),
    MAX(Asset.ACH_MAX);

    private final Asset asset;

    AchievementTier(Asset asset) {
        this.asset = asset;
    }

    public Asset asset() {
        return asset;
    }
}
