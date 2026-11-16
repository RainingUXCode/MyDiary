package com.observernotes.ui.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.observernotes.ui.model.AchievementTier;
import com.observernotes.ui.model.TaskRowModel;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class EffectLayerTest {

    private final AtomicLong now = new AtomicLong(1000);
    private final EffectLayer fx = new EffectLayer(now::get);

    @Test
    void startsIdle() {
        assertFalse(fx.isActive());
        assertTrue(fx.active().isEmpty());
    }

    @Test
    void effectRunsForItsDurationThenDisappears() {
        fx.taskCreated("a");
        assertTrue(fx.isActive());
        now.addAndGet(EffectLayer.CREATE_MS - 1);
        assertTrue(fx.isActive());
        now.addAndGet(1);
        assertFalse(fx.isActive());
    }

    @Test
    void progressGoesFromZeroToOneAndIsClamped() {
        fx.taskCompleted("a");
        var e = fx.active().get(0);
        assertEquals(0.0, fx.progress(e), 1e-9);
        now.addAndGet(EffectLayer.COMPLETE_MS / 2);
        assertEquals(0.5, fx.progress(e), 1e-9);
        now.addAndGet(10_000);
        assertEquals(1.0, fx.progress(e), 1e-9);
        assertEquals(0.0, EffectLayer.progress(e, 0), 1e-9);          // antes do início
    }

    @Test
    void durationsMatchTheKindOfCelebration() {
        fx.star(1);
        fx.medal(AchievementTier.BRONZE);
        fx.medal(AchievementTier.MAX);
        var list = fx.active();
        assertEquals(EffectLayer.STAR_MS, list.get(0).duration());
        assertEquals(EffectLayer.MEDAL_MS, list.get(1).duration());
        assertEquals(EffectLayer.MAX_MS, list.get(2).duration());
        assertTrue(EffectLayer.STAR_MS < EffectLayer.MEDAL_MS && EffectLayer.MEDAL_MS < EffectLayer.MAX_MS);
    }

    @Test
    void deletedEffectKeepsTheGhostAndSlot() {
        fx.taskDeleted(new TaskRowModel("id1", "título", true), 3);
        var e = fx.active().get(0);
        assertEquals(EffectLayer.Kind.TASK_DELETED, e.kind());
        assertEquals("título", e.ghost().title());
        assertEquals(3, e.slot());
    }

    @Test
    void rowEffectFindsTheLatestForThatTaskAndKind() {
        fx.taskCreated("a");
        now.addAndGet(100);
        fx.taskCreated("b");
        assertEquals("a", fx.rowEffect("a", EffectLayer.Kind.TASK_CREATED).key());
        assertNull(fx.rowEffect("a", EffectLayer.Kind.TASK_COMPLETED));
        assertNull(fx.rowEffect("zzz", EffectLayer.Kind.TASK_CREATED));
    }

    @Test
    void burstOfEffectsIsCappedSoWorkNeverPilesUp() {
        for (int i = 0; i < 200; i++) {
            fx.taskCompleted("t" + i);
        }
        assertEquals(24, fx.active().size());
    }

    @Test
    void activationCallbackFiresForEveryNewEffect() {
        int[] calls = {0};
        fx.setOnActivate(() -> calls[0]++);
        fx.star(2);
        fx.taskCreated("a");
        assertEquals(2, calls[0]);
    }

    @Test
    void clearRemovesEverything() {
        fx.star(1);
        fx.clear();
        assertFalse(fx.isActive());
    }

    @Test
    void easingCurvesHitTheirEndpoints() {
        assertEquals(0.0, EffectLayer.easeOutBack(0), 1e-9);
        assertEquals(1.0, EffectLayer.easeOutBack(1), 1e-9);
        assertTrue(EffectLayer.easeOutBack(0.7) > 1.0);               // passa de 1 antes de assentar
        assertEquals(0.0, EffectLayer.easeOutCubic(0), 1e-9);
        assertEquals(1.0, EffectLayer.easeOutCubic(1), 1e-9);
    }
}
