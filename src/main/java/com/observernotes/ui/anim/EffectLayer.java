package com.observernotes.ui.anim;

import com.observernotes.ui.model.AchievementTier;
import com.observernotes.ui.model.TaskRowModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Lista de efeitos em andamento. Sem Swing e sem relógio próprio: o tempo vem de um
 * {@link LongSupplier} (milissegundos), então tudo é testável quadro a quadro.
 * Fica na thread de eventos; não bloqueia nada: cada efeito é só "início + duração".
 */
public final class EffectLayer implements EffectSink {

    public enum Kind { TASK_CREATED, TASK_COMPLETED, TASK_DELETED, STAR, MEDAL, MUSIC_PLAY, MUSIC_PAUSE }

    /**
     * @param key    id da tarefa (efeitos de linha)
     * @param ghost  cópia da linha excluída
     * @param slot   posição da linha excluída
     * @param number estrela (1 a 3) para STAR
     * @param tier   medalha para MEDAL
     */
    public record Effect(Kind kind, String key, TaskRowModel ghost, int slot, int number, AchievementTier tier,
                         long start, long duration) { }

    public static final long CREATE_MS = 320;
    public static final long COMPLETE_MS = 650;
    public static final long DELETE_MS = 300;
    public static final long STAR_MS = 1500;
    public static final long MEDAL_MS = 2800;
    public static final long MAX_MS = 3600;
    public static final long MUSIC_STATE_MS = 480;
    /** Teto de efeitos simultâneos (rajadas de cliques não acumulam trabalho). */
    static final int LIMIT = 24;

    private final LongSupplier clock;
    private final List<Effect> effects = new ArrayList<>();
    private Runnable onActivate = () -> { };

    public EffectLayer(LongSupplier clockMillis) {
        this.clock = clockMillis;
    }

    /** Chamado quando entra um efeito (o canvas liga o seu temporizador de quadros). */
    public void setOnActivate(Runnable r) {
        this.onActivate = r == null ? () -> { } : r;
    }

    public long now() {
        return clock.getAsLong();
    }

    @Override
    public void musicState(boolean playing) {
        add(new Effect(playing ? Kind.MUSIC_PLAY : Kind.MUSIC_PAUSE, "", null, 0, 0, null, now(), MUSIC_STATE_MS));
    }

    @Override
    public void taskCreated(String taskId) {
        add(new Effect(Kind.TASK_CREATED, taskId, null, 0, 0, null, now(), CREATE_MS));
    }

    @Override
    public void taskCompleted(String taskId) {
        add(new Effect(Kind.TASK_COMPLETED, taskId, null, 0, 0, null, now(), COMPLETE_MS));
    }

    @Override
    public void taskDeleted(TaskRowModel snapshot, int slot) {
        add(new Effect(Kind.TASK_DELETED, snapshot.id(), snapshot, slot, 0, null, now(), DELETE_MS));
    }

    @Override
    public void star(int starInTier) {
        add(new Effect(Kind.STAR, "", null, 0, starInTier, null, now(), STAR_MS));
    }

    @Override
    public void medal(AchievementTier tier) {
        add(new Effect(Kind.MEDAL, "", null, 0, 0, tier, now(), tier == AchievementTier.MAX ? MAX_MS : MEDAL_MS));
    }

    private void add(Effect e) {
        prune();
        if (effects.size() >= LIMIT) {
            effects.remove(0);
        }
        effects.add(e);
        onActivate.run();
    }

    /** Efeitos ainda em andamento (descarta os terminados). */
    public List<Effect> active() {
        prune();
        return List.copyOf(effects);
    }

    public boolean isActive() {
        prune();
        return !effects.isEmpty();
    }

    /** Progresso de 0 a 1 de um efeito, no instante atual. */
    public double progress(Effect e) {
        return progress(e, now());
    }

    public static double progress(Effect e, long now) {
        double p = (now - e.start()) / (double) e.duration();
        return Math.max(0, Math.min(1, p));
    }

    /** Há efeito de linha (criada/concluída) para esta tarefa? Devolve o mais recente, ou null. */
    public Effect rowEffect(String taskId, Kind kind) {
        prune();
        Effect found = null;
        for (Effect e : effects) {
            if (e.kind() == kind && e.key().equals(taskId)) {
                found = e;
            }
        }
        return found;
    }

    public void clear() {
        effects.clear();
    }

    private void prune() {
        long t = now();
        effects.removeIf(e -> t - e.start() >= e.duration());
    }

    // ------------------------------------------------------------------ curvas

    /** Sai de 0 e passa de 1 um pouco antes de assentar em 1 (efeito de "pulinho"). */
    public static double easeOutBack(double t) {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        double u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    public static double easeOutCubic(double t) {
        double u = 1 - t;
        return 1 - u * u * u;
    }
}
