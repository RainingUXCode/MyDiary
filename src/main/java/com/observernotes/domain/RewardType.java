package com.observernotes.domain;

/** Tipo de recompensa de um marco. */
public enum RewardType {
    STAR, BRONZE_MEDAL, SILVER_MEDAL, GOLD_MEDAL, MAX_ACHIEVEMENT;

    public boolean isMedal() {
        return this == BRONZE_MEDAL || this == SILVER_MEDAL || this == GOLD_MEDAL;
    }
}
