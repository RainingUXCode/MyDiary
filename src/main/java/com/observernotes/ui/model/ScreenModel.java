package com.observernotes.ui.model;

import com.observernotes.ui.assets.Asset;
import java.util.List;
import java.util.Objects;

/**
 * Tudo o que a cena desenha, em dados simples (sem dependência do domínio). Na tela real é
 * montado por {@code ScreenAssembler} a partir dos observadores; {@link #empty()} é o estado inicial.
 *
 * @param tasks          até 6 tarefas, na ordem de exibição
 * @param observers      cartões do painel de observadores
 * @param history        linhas do histórico, da mais recente para a mais antiga
 * @param littleNotes    bilhetes do mural e estado de edição
 * @param achievementTier qual PNG de conquista aparece (NONE = roseta vazia)
 * @param footerMessage  última mensagem do rodapé (vazio = nenhuma)
 * @param newAchievement mostra o selo "NOVA!"
 * @param views          o que cada observador está mostrando (contador, ligado/desligado)
 * @param feedback       avisos ao usuário (limite, erro, falha ao salvar)
 * @param historyOffset  primeira linha do histórico visível (rolagem); 0 = as mais recentes
 */
public record ScreenModel(PanelMode mode, List<TaskRowModel> tasks, List<ObserverCardModel> observers,
                          List<HistoryRowModel> history, LittleNotesModel littleNotes,
                          String achievementLabel, String achievementState, AchievementTier achievementTier,
                          String footerMessage, boolean newAchievement,
                          ObserverViews views, Feedback feedback, int historyOffset, TaskPages taskPages) {

    public static final int MAX_TASKS = 6;
    /** Linhas de histórico visíveis ao mesmo tempo. */
    public static final int HISTORY_ROWS = 6;

    /** Página visual da lista; os totais continuam representando todas as tarefas. */
    public record TaskPages(int current, int count, int totalTasks) {
        public TaskPages {
            if (current < 0 || count < 1 || current >= count || totalTasks < 0) {
                throw new IllegalArgumentException("paginação inválida");
            }
        }

        public static TaskPages single(int totalTasks) {
            return new TaskPages(0, 1, totalTasks);
        }
    }

    /**
     * Visão de cada observador. Observador desligado mostra "desligado" em vez de dados
     * velhos; o contador vem do {@code CounterObserver}, não da lista de tarefas.
     */
    public record ObserverViews(boolean historyOn, boolean counterOn, int done, int total,
                                boolean achievementsOn) {
        public ObserverViews {
            if (done < 0 || total < 0 || done > total) {
                throw new IllegalArgumentException("contador inválido: " + done + "/" + total);
            }
        }

        public static ObserverViews allOn(int done, int total) {
            return new ObserverViews(true, true, done, total, true);
        }
    }

    /**
     * Avisos.
     *
     * @param notice         texto do rodapé que tem prioridade sobre a última mensagem (pode ser vazio)
     * @param warning        o aviso é de problema (cor de alerta)
     * @param fieldHighlight contorno de alerta no campo de texto
     * @param placeholder    texto do campo vazio (vazio = padrão)
     */
    public record Feedback(String notice, boolean warning, boolean fieldHighlight, String placeholder) {
        public static final Feedback NONE = new Feedback("", false, false, "");

        public Feedback {
            Objects.requireNonNull(notice, "notice");
            Objects.requireNonNull(placeholder, "placeholder");
        }
    }

    public ScreenModel {
        Objects.requireNonNull(mode, "mode");
        tasks = List.copyOf(tasks);
        observers = List.copyOf(observers);
        history = List.copyOf(history);
        Objects.requireNonNull(littleNotes, "littleNotes");
        if (tasks.size() > MAX_TASKS) {
            throw new IllegalArgumentException("máximo de " + MAX_TASKS + " tarefas");
        }
        Objects.requireNonNull(achievementLabel, "achievementLabel");
        Objects.requireNonNull(achievementState, "achievementState");
        Objects.requireNonNull(achievementTier, "achievementTier");
        Objects.requireNonNull(footerMessage, "footerMessage");
        Objects.requireNonNull(views, "views");
        Objects.requireNonNull(feedback, "feedback");
        Objects.requireNonNull(taskPages, "taskPages");
        if (historyOffset < 0 || historyOffset > Math.max(0, history.size() - HISTORY_ROWS)) {
            throw new IllegalArgumentException("rolagem inválida: " + historyOffset);
        }
    }

    /** Sem rolagem (offset 0). */
    public ScreenModel(PanelMode mode, List<TaskRowModel> tasks, List<ObserverCardModel> observers,
                       List<HistoryRowModel> history, LittleNotesModel littleNotes,
                       String achievementLabel, String achievementState, AchievementTier achievementTier,
                       String footerMessage, boolean newAchievement, ObserverViews views, Feedback feedback) {
        this(mode, tasks, observers, history, littleNotes, achievementLabel, achievementState,
                achievementTier, footerMessage, newAchievement, views, feedback, 0, TaskPages.single(tasks.size()));
    }

    /** Forma anterior com rolagem de histórico e uma única página de tarefas. */
    public ScreenModel(PanelMode mode, List<TaskRowModel> tasks, List<ObserverCardModel> observers,
                       List<HistoryRowModel> history, LittleNotesModel littleNotes,
                       String achievementLabel, String achievementState, AchievementTier achievementTier,
                       String footerMessage, boolean newAchievement, ObserverViews views, Feedback feedback,
                       int historyOffset) {
        this(mode, tasks, observers, history, littleNotes, achievementLabel, achievementState,
                achievementTier, footerMessage, newAchievement, views, feedback, historyOffset,
                TaskPages.single(tasks.size()));
    }

    /** Forma curta (telas de exemplo): contador derivado das tarefas, tudo ligado, sem avisos. */
    public ScreenModel(PanelMode mode, List<TaskRowModel> tasks, List<ObserverCardModel> observers,
                       List<HistoryRowModel> history, LittleNotesModel littleNotes,
                       String achievementLabel, String achievementState, AchievementTier achievementTier,
                       String footerMessage, boolean newAchievement) {
        this(mode, tasks, observers, history, littleNotes, achievementLabel, achievementState,
                achievementTier, footerMessage, newAchievement,
                ObserverViews.allOn((int) tasks.stream().filter(TaskRowModel::done).count(), tasks.size()),
                Feedback.NONE, 0, TaskPages.single(tasks.size()));
    }

    /** Estado inicial: sem tarefas, sem histórico, observadores todos ligados. */
    public static ScreenModel empty() {
        return new ScreenModel(PanelMode.TASKS, List.of(), defaultObservers(), List.of(), LittleNotesModel.empty(),
                "Conquistas", "nenhuma ainda", AchievementTier.NONE, "", false);
    }

    /** Os três observadores do app, todos ligados (padrão de {@code ObserverPreferences}). */
    public static List<ObserverCardModel> defaultObservers() {
        return List.of(
                new ObserverCardModel("HISTORY", Asset.ICON_HISTORY, "Histórico", "Registra cada evento", true),
                new ObserverCardModel("COUNTER", Asset.ICON_COUNTER, "Contador", "Conta tarefas concluídas", true),
                new ObserverCardModel("ACHIEVEMENTS", Asset.ACH_STAR_ON, "Conquistas",
                        "Premia marcos de progresso", true));
    }

    public ScreenModel withMode(PanelMode newMode) {
        return new ScreenModel(newMode, tasks, observers, history, littleNotes, achievementLabel,
                achievementState, achievementTier, footerMessage, newAchievement, views, feedback,
                Math.min(historyOffset, Math.max(0, history.size() - HISTORY_ROWS)), taskPages);
    }

    /** Tarefas concluídas segundo o <em>contador</em> (não a lista). */
    public int doneCount() {
        return views.done();
    }
}
