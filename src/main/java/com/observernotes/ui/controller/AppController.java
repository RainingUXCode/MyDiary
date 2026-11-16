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
    private PlayerUi player;

    private PanelMode mode = PanelMode.TASKS;
    private String notice = "";
    private int noticeSerial;
    private boolean stateSaveFailing;
    private boolean settingsSaveFailing;
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
        this.effects = effects == null ? EffectSink.NONE : effects;
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
        } else if (stateSaveFailing || settingsSaveFailing) {
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
        return player == null ? com.observernotes.ui.model.PlayerModel.NONE : player.model();
    }

    /** Aviso vindo do player (arquivos adicionados, faixa que não abriu...). */
    public void notifyUser(String text, boolean warning) {
        setNotice(text, warning);
        onChange.run();
    }

    /** Executa uma ação do usuário e avisa a interface para redesenhar. */
    public void handle(UiAction action) {
        if (action.type().isPlayer()) {
            if (player != null) {
                player.handle(action);
            }
            onChange.run();
            return;
        }
        if (player != null && (action.type() == UiAction.Type.NAV_LIST || action.type() == UiAction.Type.NAV_ALERTS
                || action.type() == UiAction.Type.NAV_RECORD)) {
            player.closePanel();                           // navegar fecha a playlist; a música continua
        }
        celebrations.drain();                              // descarta qualquer sobra antiga
        switch (action.type()) {
            case NAV_LIST -> mode = PanelMode.TASKS;
            case NAV_ALERTS -> mode = PanelMode.OBSERVERS;
            case NAV_RECORD -> mode = PanelMode.HISTORY;
            case TASK_PAGE_PREVIOUS -> changeTaskPage(-1);
            case TASK_PAGE_NEXT -> changeTaskPage(1);
            case ADD, SUBMIT -> submit();
            case TOGGLE_TASK -> withTask(action.key(), id -> {
                boolean wasDone = isDone(action.key());
                int before = failures.count();
                app.service().toggle(id);
                stateSaveFailing = failures.count() != before || app.lastSaveError().isPresent();
                if (!wasDone && isDone(action.key())) {
                    fx(() -> effects.taskCompleted(action.key()));
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
            });
            case TOGGLE_OBSERVER -> toggleObserver(action.key());
            case CLEAR -> {
                int before = failures.count();
                app.clearHistory();
                historyOffset = 0;
                stateSaveFailing = failures.count() != before || app.lastSaveError().isPresent();
            }
            case SCROLL_HISTORY -> scrollHistory(action.key());
            case HINT -> setNotice(action.key(), false);
            case FOCUS_FIELD -> { }
        }
        celebrate();
        onChange.run();
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

    private void changeTaskPage(int delta) {
        if (mode == PanelMode.TASKS) {
            setTaskPage(clampTaskPage(taskPage + delta));
        }
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
    private void celebrate() {
        var unlocked = celebrations.drain();
        if (!app.observers().isEnabled(ObserverKind.ACHIEVEMENTS)) {
            return;
        }
        for (MilestoneUnlocked m : unlocked) {
            RewardType r = m.milestone().reward();
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

    private void submit() {
        String text = input.text();
        if (text.isBlank()) {
            setNotice("Escreva um título para a tarefa");
            return;
        }
        int before = failures.count();
        TaskView created;
        try {
            created = app.service().createTask(text);
        } catch (BoardFullException e) {
            setNotice("Limite de " + TaskBoard.MAX_TASKS + " tarefas: conclua ou exclua uma para criar outra");
            return;
        } catch (IllegalArgumentException e) {
            setNotice(e.getMessage());
            return;
        }
        stateSaveFailing = failures.count() != before || app.lastSaveError().isPresent();
        input.clear();
        notice = "";
        String id = created.id().toString();
        setTaskPage((app.service().tasks().size() - 1) / ScreenAssembler.TASKS_PER_PAGE);
        fx(() -> effects.taskCreated(id));
    }

    private void withTask(String key, java.util.function.Consumer<TaskId> action) {
        try {
            action.accept(new TaskId(UUID.fromString(key)));
        } catch (IllegalArgumentException ignored) {
            // chave inválida: nenhuma tarefa é afetada
        }
    }

    private void toggleObserver(String key) {
        ObserverKind kind;
        try {
            kind = ObserverKind.valueOf(key);
        } catch (IllegalArgumentException e) {
            return;
        }
        int before = failures.count();
        app.observers().setEnabled(kind, !app.observers().isEnabled(kind));
        settingsSaveFailing = failures.count() != before;
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
