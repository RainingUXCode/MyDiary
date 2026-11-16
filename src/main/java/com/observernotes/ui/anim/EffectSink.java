package com.observernotes.ui.anim;

import com.observernotes.ui.model.AchievementTier;
import com.observernotes.ui.model.TaskRowModel;

/**
 * Porta pela qual o controlador pede efeitos visuais. Efeitos são só aparência: nunca
 * recebem nem devolvem nada do domínio, e o controlador ignora qualquer falha deles.
 */
public interface EffectSink {

    /** Não faz nada (padrão quando não há tela, como nos testes de núcleo). */
    EffectSink NONE = new EffectSink() {
        @Override
        public void taskCreated(String taskId) { }

        @Override
        public void taskCompleted(String taskId) { }

        @Override
        public void taskDeleted(TaskRowModel snapshot, int slot) { }

        @Override
        public void star(int starInTier) { }

        @Override
        public void medal(AchievementTier tier) { }
    };

    /** O player passou a tocar ({@code true}) ou pausou ({@code false}). Padrão: nada. */
    default void musicState(boolean playing) { }

    /** Gancho legado de troca de faixa; permanece vazio porque o título agora aparece somente no rodapé. */
    default void musicTrack(String title) { }

    void taskCreated(String taskId);

    void taskCompleted(String taskId);

    /** @param snapshot a linha como estava antes de sumir; {@code slot} é a posição em que estava */
    void taskDeleted(TaskRowModel snapshot, int slot);

    /** Nova estrela (1 a 3 dentro do ciclo atual). */
    void star(int starInTier);

    /** Nova medalha ({@code BRONZE}, {@code SILVER}, {@code GOLD}) ou a conquista máxima ({@code MAX}). */
    void medal(AchievementTier tier);
}
