package com.observernotes.settings;

import java.util.EnumMap;
import java.util.Map;

/**
 * Preferência de ativação de cada observador (imutável). Todos começam ativos.
 * Ativar ou desativar um observador só muda o que é exibido: a pontuação é decidida
 * no domínio e não depende de nenhum observador.
 */
public final class ObserverPreferences {

    private final Map<ObserverKind, Boolean> enabled;

    private ObserverPreferences(Map<ObserverKind, Boolean> enabled) {
        this.enabled = enabled;
    }

    public static ObserverPreferences allEnabled() {
        Map<ObserverKind, Boolean> m = new EnumMap<>(ObserverKind.class);
        for (ObserverKind k : ObserverKind.values()) {
            m.put(k, true);
        }
        return new ObserverPreferences(m);
    }

    public boolean isEnabled(ObserverKind kind) {
        return enabled.get(kind);
    }

    public ObserverPreferences with(ObserverKind kind, boolean value) {
        Map<ObserverKind, Boolean> m = new EnumMap<>(enabled);
        m.put(kind, value);
        return new ObserverPreferences(m);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ObserverPreferences other && enabled.equals(other.enabled);
    }

    @Override
    public int hashCode() {
        return enabled.hashCode();
    }

    @Override
    public String toString() {
        return enabled.toString();
    }
}
