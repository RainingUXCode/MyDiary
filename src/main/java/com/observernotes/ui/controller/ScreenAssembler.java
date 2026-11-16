package com.observernotes.ui.controller;

import com.observernotes.application.AppCore;
import com.observernotes.domain.AchievementProgress;
import com.observernotes.domain.Milestone;
import com.observernotes.domain.RewardType;
import com.observernotes.domain.TaskView;
import com.observernotes.observer.impl.HistoryEntry;
import com.observernotes.settings.ObserverKind;
import com.observernotes.ui.model.AchievementTier;
import com.observernotes.ui.model.HistoryRowModel;
import com.observernotes.ui.model.ObserverCardModel;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.model.TaskRowModel;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Traduz o estado do {@link AppCore} (lista de tarefas e observadores) para o {@link ScreenModel}.
 * Só lê: nunca altera domínio, observadores nem preferências.
 */
public final class ScreenAssembler {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    public static final int TASKS_PER_PAGE = 6;

    private ScreenAssembler() { }

    public static ScreenModel assemble(AppCore app, PanelMode mode, ScreenModel.Feedback feedback, String footer,
                                       ZoneId zone) {
        return assemble(app, mode, feedback, footer, zone, 0);
    }

    public static ScreenModel assemble(AppCore app, PanelMode mode, ScreenModel.Feedback feedback, String footer,
                                       ZoneId zone, int historyOffset) {
        return assemble(app, mode, feedback, footer, zone, historyOffset, 0);
    }

    public static ScreenModel assemble(AppCore app, PanelMode mode, ScreenModel.Feedback feedback, String footer,
                                       ZoneId zone, int historyOffset, int requestedTaskPage) {
        var registry = app.observers();
        boolean historyOn = registry.isEnabled(ObserverKind.HISTORY);
        boolean counterOn = registry.isEnabled(ObserverKind.COUNTER);
        boolean constellationOn = registry.isEnabled(ObserverKind.CONSTELLATION);
        boolean achievementsOn = registry.isEnabled(ObserverKind.ACHIEVEMENTS);

        List<TaskView> allTasks = app.service().tasks();
        int pageCount = Math.max(1, (allTasks.size() + TASKS_PER_PAGE - 1) / TASKS_PER_PAGE);
        int taskPage = Math.max(0, Math.min(requestedTaskPage, pageCount - 1));
        int from = taskPage * TASKS_PER_PAGE;
        int to = Math.min(allTasks.size(), from + TASKS_PER_PAGE);
        List<TaskRowModel> tasks = new ArrayList<>();
        for (TaskView t : allTasks.subList(from, to)) {
            tasks.add(new TaskRowModel(t.id().toString(), t.title(), t.completed()));
        }

        List<HistoryRowModel> history = new ArrayList<>();
        for (HistoryEntry e : app.history().entries()) {
            history.add(new HistoryRowModel(HOUR.format(e.at().atZone(zone)), switch (e.kind()) {
                case CREATED -> "Criada: ";
                case COMPLETED -> "Concluída: ";
                case DELETED -> "Excluída: ";
            } + e.taskTitle()));
        }

        int done = app.counter().completedCount();
        int total = app.counter().taskCount();
        int lit = app.constellation().litCount();
        int stars = app.constellation().starCount();
        AchievementProgress progress = app.achievements().progress();

        var views = new ScreenModel.ObserverViews(historyOn, counterOn, done, total, constellationOn, achievementsOn);
        return new ScreenModel(mode, tasks, cards(app), history, lit, stars, "Conquistas", achievementText(progress),
                tier(progress), footer, false, views, feedback, historyOffset,
                new ScreenModel.TaskPages(taskPage, pageCount, allTasks.size()));
    }

    private static List<ObserverCardModel> cards(AppCore app) {
        List<ObserverCardModel> base = ScreenModel.defaultObservers();
        List<ObserverCardModel> out = new ArrayList<>();
        for (ObserverCardModel c : base) {
            out.add(new ObserverCardModel(c.key(), c.icon(), c.title(), c.description(),
                    app.observers().isEnabled(ObserverKind.valueOf(c.key()))));
        }
        return out;
    }

    /** Roseta vazia só com 0 conquistas; depois, a maior recompensa já obtida. */
    static AchievementTier tier(AchievementProgress p) {
        if (p.totalScored() == 0) {
            return AchievementTier.NONE;
        }
        if (p.isMax()) {
            return AchievementTier.MAX;
        }
        return p.highestMedal().map(r -> switch (r) {
            case BRONZE_MEDAL -> AchievementTier.BRONZE;
            case SILVER_MEDAL -> AchievementTier.SILVER;
            case GOLD_MEDAL -> AchievementTier.GOLD;
            default -> AchievementTier.STARS;
        }).orElse(AchievementTier.STARS);
    }

    static String achievementText(AchievementProgress p) {
        if (p.totalScored() == 0) {
            return "nenhuma ainda";
        }
        return p.totalScored() + " de " + Milestone.COUNT;
    }
}
