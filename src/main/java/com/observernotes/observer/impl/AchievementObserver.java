package com.observernotes.observer.impl;

import com.observernotes.domain.AchievementProgress;
import com.observernotes.domain.Milestone;
import com.observernotes.domain.event.DomainEvent;
import com.observernotes.domain.event.MilestoneUnlocked;
import com.observernotes.observer.Observer;
import java.util.Optional;

/**
 * Acompanha o histórico acumulado de conclusões inéditas. Reage somente a
 * {@link MilestoneUnlocked}. Não decide pontuação (isso é do ledger, no domínio):
 * mantém a visão que a interface consulta e informa o marco mais recente
 * para disparar celebrações. Se este observador falhar, nenhum ponto se perde.
 */
public final class AchievementObserver implements Observer<DomainEvent> {

    private AchievementProgress progress;
    private Milestone lastUnlocked;

    public AchievementObserver() {
        this(AchievementProgress.of(0));
    }

    /** Inicia a partir de um progresso já existente (restauração, religar). */
    public AchievementObserver(AchievementProgress initial) {
        this.progress = initial;
    }

    /** Refaz a visão a partir do ledger. Não é uma conquista: nenhum marco "recente" é anunciado. */
    public void resync(AchievementProgress current) {
        this.progress = current;
        this.lastUnlocked = null;
    }

    @Override
    public void onEvent(DomainEvent event) {
        if (event instanceof MilestoneUnlocked e) {
            progress = AchievementProgress.of(e.totalScored());
            lastUnlocked = e.milestone();
        }
    }

    public AchievementProgress progress() {
        return progress;
    }

    /** Marco conquistado mais recentemente desde que o observador foi criado. */
    public Optional<Milestone> lastUnlocked() {
        return Optional.ofNullable(lastUnlocked);
    }
}
