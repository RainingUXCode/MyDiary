package com.observernotes.ui.controller;

import com.observernotes.application.AppCore;
import com.observernotes.domain.BoardFullException;
import com.observernotes.domain.TaskBoard;
import com.observernotes.domain.TaskId;
import com.observernotes.settings.ObserverKind;
import com.observernotes.domain.RewardType;
import com.observernotes.domain.AchievementProgress;
import com.observernotes.domain.event.MilestoneUnlocked;
import com.observernotes.domain.TaskView;
import com.observernotes.ui.anim.EffectSink;
import com.observernotes.ui.model.AchievementTier;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.TaskRowModel;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.widget.TextInput;
import com.observernotes.ui.widget.UiAction;
import com.observernotes.sfx.SfxEffect;
import com.observernotes.sfx.SoundEffects;
import com.observernotes.persistence.PersistenceException;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Traduz ações do usuário em chamadas ao {@link AppCore} e devolve o modelo da tela. Não tem
 * regra de negócio própria: validação, limite de tarefas e pontuação são do domínio; aqui só
 * há navegação, texto do campo e avisos. Use na thread de eventos do Swing.
 */
public final class AppController {

    /** Tempo que um aviso de validação fica no rodapé. */
    static final int NOTICE_MILLIS = 4000;

    private final AppCore app;
    private final SaveFailureTracker failures;
    private final TextInput input;
    private final ZoneId zone;
    private final Runnable onChange;
    private final BiConsumer<Runnable, Integer> scheduler;
    private final FooterMessages footer;
    private final CelebrationCollector celebrations = new CelebrationCollector();
    private final EffectSink effects;
    private final SoundEffects sfx;
    private PlayerUi player;

    private PanelMode mode = PanelMode.TASKS;
    private String notice = "";
    private int noticeSerial;
    private boolean stateSaveFailing;
    private boolean settingsSaveFailing;
    private boolean sfxSaveFailing;
    private boolean noticeWarning = true;
    private int historyOffset;
    private int taskPage;

    /**
     * @param scheduler agenda uma tarefa na thread de eventos depois de N ms (no app, um Timer do Swing)
     */
    public AppController(AppCore app, SaveFailureTracker failures, TextInput input, ZoneId zone, Runnable onChange,
                         BiConsumer<Runnable, Integer> scheduler) {
        this(app, failures, input, zone, onChange, scheduler, EffectSink.NONE);
    }

    /** @param effects recebe os pedidos de animação; falhas dele são ignoradas e nunca afetam o núcleo */
    public AppController(AppCore app, SaveFailureTracker failures, TextInput input, ZoneId zone, Runnable onChange,
                         BiConsumer<Runnable, Integer> scheduler, EffectSink effects) {
        this(app, failures, input, zone, onChange, scheduler, effects, SoundEffects.NONE);
    }

    /** SFX é uma porta independente do player musical; em testes pode ser substituída por um gravador simples. */
    public AppController(AppCore app, SaveFailureTracker failures, TextInput input, ZoneId zone, Runnable onChange,
                         BiConsumer<Runnable, Integer> scheduler, EffectSink effects, SoundEffects soundEffects) {
        this.effects = effects == null ? EffectSink.NONE : effects;
        this.sfx = soundEffects == null ? SoundEffects.NONE : soundEffects;
        this.app = app;
        this.failures = failures;
        this.input = input;
        this.zone = zone;
        this.onChange = onChange;
        this.scheduler = scheduler;
        this.footer = new FooterMessages(zone);
        footer.startFrom(app.history().entries().stream().findFirst());
        app.bus().subscribe(footer);
        app.bus().subscribe(celebrations);
        stateSaveFailing = app.lastSaveError().isPresent();
        taskPage = clampTaskPage(app.taskPage());
        if (taskPage != app.taskPage()) {
            app.setTaskPage(taskPage);
        }
    }

    public PanelMode mode() {
        return mode;
    }

    /** Aviso de validação atual (vazio se não houver). */
    public String notice() {
        return notice;
    }

    public ScreenModel model() {
        boolean full = app.service().tasks().size() >= TaskBoard.MAX_TASKS;
        String placeholder = full ? "Limite de " + TaskBoard.MAX_TASKS + " tarefas: conclua ou exclua uma" : "";
        ScreenModel.Feedback fb;
        if (!notice.isEmpty()) {
            fb = new ScreenModel.Feedback(notice, noticeWarning, noticeWarning, placeholder);
        } else if (stateSaveFailing || settingsSaveFailing || sfxSaveFailing) {
            fb = new ScreenModel.Feedback(saveFailureText(), true, false, placeholder);
        } else {
            fb = new ScreenModel.Feedback("", false, false, placeholder);
        }
        int total = app.history().entries().size();
        historyOffset = Math.max(0, Math.min(historyOffset, total - ScreenModel.HISTORY_ROWS));
        taskPage = clampTaskPage(taskPage);
        return ScreenAssembler.assemble(app, mode, fb, footer.last(), zone, historyOffset, taskPage);
    }

    private String saveFailureText() {
        if (sfxSaveFailing && !stateSaveFailing && !settingsSaveFailing) {
            return "Falha ao salvar os efeitos sonoros: a mudança ainda não está no disco";
        }
        return settingsSaveFailing && !stateSaveFailing
                ? "Falha ao salvar as preferências: a mudança ainda não está no disco"
                : "Falha ao salvar: as alterações ainda não estão no disco";
    }

    /** Liga o player musical (opcional). As ações do player nunca passam pelo núcleo das tarefas. */
    public void attachPlayer(PlayerUi playerUi) {
        this.player = playerUi;
    }

    /** Estado do player para desenhar; {@code NONE} se não há player. */
    public com.observernotes.ui.model.PlayerModel playerModel() {
        var base = player == null ? com.observernotes.ui.model.PlayerModel.NONE : player.model();
        var prefs = sfx.preferences();
        return base.withSfx(prefs.enabled(), prefs.volume());
    }

    /** Aviso vindo do player (arquivos adicionados, faixa que não abriu...). */
    public void notifyUser(String text, boolean warning) {
        setNotice(text, warning);
        onChange.run();
    }

    /** Executa uma ação do usuário e avisa a interface para redesenhar. */
    public void handle(UiAction action) {
        if (action.type() == UiAction.Type.SFX_TOGGLE || action.type() == UiAction.Type.SFX_VOLUME) {
            handleSfxSetting(action);
            onChange.run();
            return;
        }
        if (action.type().isPlayer()) {
            if (player != null) {
                boolean changed = player.handle(action);
                if (changed && !(action.type() == UiAction.Type.PLAYER_VOLUME && action.key().startsWith("set:"))
                        && action.type() != UiAction.Type.PLAYER_SCROLL) {
                    sfx.play(SfxEffect.CLICK);
                }
            }
            onChange.run();
            return;
        }
        if (player != null && (action.type() == UiAction.Type.NAV_LIST || action.type() == UiAction.Type.NAV_ALERTS
                || action.type() == UiAction.Type.NAV_RECORD)) {
            player.closePanel();                           // navegar fecha a playlist; a música continua
        }
        celebrations.drain();                              // descarta qualquer sobra antiga
        SfxEffect[] sound = {null};
        switch (action.type()) {
            case NAV_LIST -> { mode = PanelMode.TASKS; sound[0] = SfxEffect.CLICK; }
            case NAV_ALERTS -> { mode = PanelMode.OBSERVERS; sound[0] = SfxEffect.CLICK; }
            case NAV_RECORD -> { mode = PanelMode.HISTORY; sound[0] = SfxEffect.CLICK; }
            case TASK_PAGE_PREVIOUS -> { if (changeTaskPage(-1)) sound[0] = SfxEffect.PAGE; }
            case TASK_PAGE_NEXT -> { if (changeTaskPage(1)) sound[0] = SfxEffect.PAGE; }
            case ADD, SUBMIT -> { if (submit()) sound[0] = SfxEffect.CREATE; }
            case TOGGLE_TASK -> withTask(action.key(), id -> {
                boolean wasDone = isDone(action.key());
                int before = failures.count();
                app.service().toggle(id);
                stateSaveFailing = failures.count() != before || app.lastSaveError().isPresent();
                if (!wasDone && isDone(action.key())) {
                    fx(() -> effects.taskCompleted(action.key()));
                    sound[0] = SfxEffect.COMPLETE;
                } else {
                    sound[0] = SfxEffect.CLICK;
                }
            });
            case DELETE_TASK -> withTask(action.key(), id -> {
                var rows = currentRows();
                int slot = indexOf(rows, action.key());
                int pageBefore = taskPage;
                int before = failures.count();
                boolean removed = app.service().delete(id);
                stateSaveFailing = failures.count() != before || app.lastSaveError().isPresent();
                if (removed) {
                    setTaskPage(clampTaskPage(taskPage));
                }
                if (removed && slot >= 0 && taskPage == pageBefore) {
                    fx(() -> effects.taskDeleted(rows.get(slot), slot));
                }
                if (removed) {
                    sound[0] = SfxEffect.CLICK;
                }
            });
            case TOGGLE_OBSERVER -> { if (toggleObserver(action.key())) sound[0] = SfxEffect.CLICK; }
            case CLEAR -> {
                int before = failures.count();
                app.clearHistory();
                historyOffset = 0;
                stateSaveFailing = failures.count() != before || app.lastSaveError().isPresent();
                sound[0] = SfxEffect.CLICK;
            }
            case SCROLL_HISTORY -> scrollHistory(action.key());
            case HINT -> setNotice(action.key(), false);
            case FOCUS_FIELD -> { }
        }
        if (celebrate()) {
            sound[0] = SfxEffect.MEDAL;                    // medalha/conquista máxima vence conclusão/estrela
        }
        if (sound[0] != null) {
            sfx.play(sound[0]);
        }
        onChange.run();
    }

    private void handleSfxSetting(UiAction action) {
        try {
            if (action.type() == UiAction.Type.SFX_TOGGLE) {
                boolean enable = !sfx.preferences().enabled();
                sfx.setEnabled(enable);
                sfxSaveFailing = false;
                if (enable) {
                    sfx.play(SfxEffect.CLICK);              // desligar nunca produz som
                }
                return;
            }
            if (!sfx.preferences().enabled()) {
                return;                                     // controles de volume ficam realmente desabilitados
            }
            double value = sfx.preferences().volume();
            if (action.key().equals("+")) {
                value += 0.1;
            } else if (action.key().equals("-")) {
                value -= 0.1;
            } else if (action.key().startsWith("set:")) {
                value = Double.parseDouble(action.key().substring(4));
            } else {
                return;
            }
            sfx.setVolume(Math.round(Math.max(0, Math.min(1, value)) * 100) / 100.0);
            sfxSaveFailing = false;
            // Slider e ajuste contínuo ficam silenciosos; não há prévia repetitiva.
        } catch (NumberFormatException ignored) {
            // ação inválida não altera nem toca nada
        } catch (PersistenceException e) {
            sfxSaveFailing = true;
            setNotice("Não foi possível salvar as preferências de efeitos sonoros", true);
        }
    }

    private java.util.List<TaskRowModel> currentRows() {
        java.util.List<TaskRowModel> rows = new java.util.ArrayList<>();
        java.util.List<TaskView> all = app.service().tasks();
        int from = Math.min(all.size(), taskPage * ScreenAssembler.TASKS_PER_PAGE);
        int to = Math.min(all.size(), from + ScreenAssembler.TASKS_PER_PAGE);
        for (TaskView t : all.subList(from, to)) {
            rows.add(new TaskRowModel(t.id().toString(), t.title(), t.completed()));
        }
        return rows;
    }

    private static int indexOf(java.util.List<TaskRowModel> rows, String id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isDone(String id) {
        return app.service().tasks().stream().anyMatch(r -> r.id().toString().equals(id) && r.completed());
    }

    private int pageCount() {
        return Math.max(1, (app.service().tasks().size() + ScreenAssembler.TASKS_PER_PAGE - 1)
                / ScreenAssembler.TASKS_PER_PAGE);
    }

    private int clampTaskPage(int page) {
        return Math.max(0, Math.min(page, pageCount() - 1));
    }

    private boolean changeTaskPage(int delta) {
        if (mode == PanelMode.TASKS) {
            int before = taskPage;
            setTaskPage(clampTaskPage(taskPage + delta));
            return taskPage != before;
        }
        return false;
    }

    private void setTaskPage(int page) {
        int target = clampTaskPage(page);
        if (target == taskPage && target == app.taskPage()) {
            return;
        }
        taskPage = target;
        int before = failures.count();
        boolean saved = app.setTaskPage(target);
        stateSaveFailing = !saved || failures.count() != before || app.lastSaveError().isPresent();
    }

    /** Pede comemoração só para marcos que o domínio acabou de publicar e só com o observador de Conquistas ligado. */
    private boolean celebrate() {
        var unlocked = celebrations.drain();
        boolean show = app.observers().isEnabled(ObserverKind.ACHIEVEMENTS);
        boolean medal = false;
        for (MilestoneUnlocked m : unlocked) {
            RewardType r = m.milestone().reward();
            if (r != RewardType.STAR) {
                medal = true;
            }
            if (!show) {
                continue; // o desbloqueio continua real e sonoro; só a animação depende do observador visual
            }
            if (r == RewardType.STAR) {
                int n = AchievementProgress.of(m.totalScored()).starsInCurrentTier();
                fx(() -> effects.star(n));
            } else {
                AchievementTier tier = switch (r) {
                    case BRONZE_MEDAL -> AchievementTier.BRONZE;
                    case SILVER_MEDAL -> AchievementTier.SILVER;
                    case GOLD_MEDAL -> AchievementTier.GOLD;
                    default -> AchievementTier.MAX;
                };
                fx(() -> effects.medal(tier));
            }
        }
        return medal;
    }

    /** Efeitos são só aparência: qualquer falha deles é descartada. */
    private static void fx(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ignored) {
            // animação com defeito não pode afetar tarefas, pontuação nem gravação
        }
    }

    private void scrollHistory(String key) {
        if (mode != PanelMode.HISTORY) {
            return;
        }
        int total = app.history().entries().size();
        int max = Math.max(0, total - ScreenModel.HISTORY_ROWS);
        int target;
        switch (key) {
            case "home" -> target = 0;
            case "end" -> target = max;
            default -> {
                try {
                    target = historyOffset + Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    return;
                }
            }
        }
        historyOffset = Math.max(0, Math.min(max, target));
    }

    private boolean submit() {
        String text = input.text();
        if (text.isBlank()) {
            setNotice("Escreva um título para a tarefa");
            return false;
        }
        int before = failures.count();
        TaskView created;
        try {
            created = app.service().createTask(text);
        } catch (BoardFullException e) {
            setNotice("Limite de " + TaskBoard.MAX_TASKS + " tarefas: conclua ou exclua uma para criar outra");
            return false;
        } catch (IllegalArgumentException e) {
            setNotice(e.getMessage());
            return false;
        }
        stateSaveFailing = failures.count() != before || app.lastSaveError().isPresent();
        input.clear();
        notice = "";
        String id = created.id().toString();
        setTaskPage((app.service().tasks().size() - 1) / ScreenAssembler.TASKS_PER_PAGE);
        fx(() -> effects.taskCreated(id));
        return true;
    }

    private void withTask(String key, java.util.function.Consumer<TaskId> action) {
        try {
            action.accept(new TaskId(UUID.fromString(key)));
        } catch (IllegalArgumentException ignored) {
            // chave inválida: nenhuma tarefa é afetada
        }
    }

    private boolean toggleObserver(String key) {
        ObserverKind kind;
        try {
            kind = ObserverKind.valueOf(key);
        } catch (IllegalArgumentException e) {
            return false;
        }
        int before = failures.count();
        app.observers().setEnabled(kind, !app.observers().isEnabled(kind));
        settingsSaveFailing = failures.count() != before;
        return true;
    }

    private void setNotice(String text) {
        setNotice(text, true);
    }

    private void setNotice(String text, boolean warning) {
        notice = text;
        noticeWarning = warning;
        int serial = ++noticeSerial;
        scheduler.accept(() -> {
            if (serial == noticeSerial && !notice.isEmpty()) {
                notice = "";
                onChange.run();
            }
        }, NOTICE_MILLIS);
    }
}
