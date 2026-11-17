package com.observernotes.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.observernotes.sfx.SfxEffect;
import com.observernotes.sfx.SfxPreferences;
import com.observernotes.sfx.SoundEffects;
import com.observernotes.support.TempFiles;
import com.observernotes.ui.anim.EffectSink;
import com.observernotes.ui.widget.UiAction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppControllerSfxTest {

    private static final class Recorder implements SoundEffects {
        final List<SfxEffect> log = new ArrayList<>();
        SfxPreferences prefs = SfxPreferences.defaults();
        @Override public void play(SfxEffect effect) { if (prefs.enabled()) log.add(effect); }
        @Override public SfxPreferences preferences() { return prefs; }
        @Override public void setEnabled(boolean enabled) { prefs = prefs.withEnabled(enabled); }
        @Override public void setVolume(double volume) { prefs = prefs.withVolume(volume); }
        @Override public void close() { }
    }

    private Path dir;
    private Recorder sounds;
    private World w;

    @BeforeEach
    void setUp() throws Exception {
        dir = TempFiles.create();
        sounds = new Recorder();
        w = new World(dir, EffectSink.NONE, sounds);
    }

    @AfterEach
    void tearDown() throws Exception {
        TempFiles.deleteRecursively(dir);
    }

    @Test
    void successfulCreatePageChangeAndInvalidActionUseOnlyTheirSpecificSound() {
        for (int i = 1; i <= 7; i++) w.add("t" + i);
        assertEquals(7, sounds.log.stream().filter(x -> x == SfxEffect.CREATE).count());
        sounds.log.clear();
        w.controller.handle(UiAction.of(UiAction.Type.TASK_PAGE_PREVIOUS));
        w.controller.handle(UiAction.of(UiAction.Type.TASK_PAGE_PREVIOUS)); // limite: inválido e silencioso
        w.add("   ");
        assertEquals(List.of(SfxEffect.PAGE), sounds.log);
    }

    @Test
    void completionUsesLevelUpAndMedalOverridesItAtTheFourthMilestone() {
        for (int i = 1; i <= 4; i++) w.add("t" + i);
        sounds.log.clear();
        for (int i = 0; i < 4; i++) w.toggle(i);
        assertEquals(List.of(SfxEffect.COMPLETE, SfxEffect.COMPLETE, SfxEffect.COMPLETE, SfxEffect.MEDAL),
                sounds.log);
    }

    @Test
    void uncheckDeleteAndObserverUseClickAndDisablingIsSilent() {
        w.add("a");
        w.toggle(0);
        sounds.log.clear();
        w.toggle(0);
        w.flipObserver("COUNTER");
        w.delete(0);
        assertEquals(List.of(SfxEffect.CLICK, SfxEffect.CLICK, SfxEffect.CLICK), sounds.log);
        sounds.log.clear();
        w.controller.handle(UiAction.of(UiAction.Type.SFX_TOGGLE));
        assertFalse(sounds.prefs.enabled());
        assertEquals(List.of(), sounds.log);
    }

    @Test
    void sfxVolumeIsIndependentAndSliderChangesStaySilent() {
        w.controller.handle(new UiAction(UiAction.Type.SFX_VOLUME, "set:0.75"));
        assertEquals(0.75, sounds.prefs.volume());
        assertEquals(List.of(), sounds.log);
        w.controller.handle(UiAction.of(UiAction.Type.SFX_TOGGLE));
        w.controller.handle(new UiAction(UiAction.Type.SFX_VOLUME, "set:0.10"));
        assertFalse(sounds.prefs.enabled());
        assertEquals(0.75, sounds.prefs.volume());                 // desligar preserva e bloqueia o volume
    }
}
