package com.observernotes.ui.widget;

import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.ScreenModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Foco por teclado (Tab / Shift+Tab) e a regra de ativação. Lógica pura, sem Swing.
 *
 * <p>Segurança contra acidentes: Enter só adiciona tarefa (campo ou botão ADD) e aciona
 * navegação e interruptores. Marcar/desmarcar exige Espaço. Excluir uma tarefa e limpar o
 * histórico pelo teclado exigem Espaço duas vezes seguidas (a primeira só avisa), dentro de
 * {@link #CONFIRM_MS}; qualquer outra tecla ou mudança de foco cancela. Digitar no campo
 * nunca aciona nada.
 */
public final class FocusRing {

    public static final UiAction FIELD = UiAction.of(UiAction.Type.FOCUS_FIELD);
    public static final long CONFIRM_MS = 4000;

    /** Resultado de pressionar Espaço/Enter no item focado. */
    public record Activation(UiAction action) {
        public static final Activation NONE = new Activation(null);

        public boolean isNone() {
            return action == null;
        }
    }

    private final LongSupplier clock;
    private UiAction current;
    private UiAction armed;
    private long armedAt;

    public FocusRing(LongSupplier clockMillis) {
        this.clock = clockMillis;
    }

    /** Ordem de Tab: navegação, campo, ADD e depois os itens do painel atual. */
    public static List<UiAction> order(ScreenModel m) {
        return order(m, com.observernotes.ui.model.PlayerModel.NONE);
    }

    private static UiAction p(UiAction.Type t, String key) {
        return new UiAction(t, key);
    }

    /** Com o player: o painel da playlist (se aberto) substitui os itens do painel central; os controles do player vêm por último. */
    public static List<UiAction> order(ScreenModel m, com.observernotes.ui.model.PlayerModel pm) {
        List<UiAction> o = new ArrayList<>();
        o.add(UiAction.of(UiAction.Type.NAV_LIST));
        o.add(UiAction.of(UiAction.Type.NAV_ALERTS));
        o.add(UiAction.of(UiAction.Type.NAV_RECORD));
        o.add(FIELD);
        o.add(UiAction.of(UiAction.Type.ADD));
        if (pm.available() && pm.panelOpen()) {
            o.add(UiAction.of(UiAction.Type.PLAYER_ADD));                       // "+ Música" fica dentro da playlist
            int end = Math.min(pm.rows().size(), pm.panelOffset() + com.observernotes.ui.model.PlayerModel.PANEL_ROWS);
            for (int i = pm.panelOffset(); i < end; i++) {
                String path = pm.rows().get(i).path();
                o.add(p(UiAction.Type.PLAYER_PLAY, path));
                o.add(p(UiAction.Type.PLAYER_UP, path));
                o.add(p(UiAction.Type.PLAYER_DOWN, path));
                o.add(p(UiAction.Type.PLAYER_REMOVE, path));
            }
            o.add(UiAction.of(UiAction.Type.PLAYER_REPEAT));
            o.add(p(UiAction.Type.PLAYER_VOLUME, "-"));
            o.add(p(UiAction.Type.PLAYER_VOLUME, "+"));
            o.add(UiAction.of(UiAction.Type.SFX_TOGGLE));
            if (pm.sfxEnabled()) {
                o.add(p(UiAction.Type.SFX_VOLUME, "-"));
                o.add(p(UiAction.Type.SFX_VOLUME, "+"));
            }
        } else if (m.mode() == PanelMode.TASKS) {
            if (m.taskPages().current() > 0) {
                o.add(UiAction.of(UiAction.Type.TASK_PAGE_PREVIOUS));
            }
            if (m.taskPages().current() + 1 < m.taskPages().count()) {
                o.add(UiAction.of(UiAction.Type.TASK_PAGE_NEXT));
            }
            for (var t : m.tasks()) {
                o.add(new UiAction(UiAction.Type.TOGGLE_TASK, t.id()));
                o.add(new UiAction(UiAction.Type.DELETE_TASK, t.id()));
            }
        } else if (m.mode() == PanelMode.OBSERVERS) {
            for (var c : m.observers()) {
                o.add(new UiAction(UiAction.Type.TOGGLE_OBSERVER, c.key()));
            }
        } else {
            o.add(UiAction.of(UiAction.Type.CLEAR));
        }
        if (pm.available()) {
            o.add(UiAction.of(UiAction.Type.PLAYER_PREVIOUS));
            o.add(UiAction.of(UiAction.Type.PLAYER_TOGGLE));
            o.add(UiAction.of(UiAction.Type.PLAYER_NEXT));
            o.add(UiAction.of(UiAction.Type.PLAYER_PANEL));
        }
        return o;
    }

    private com.observernotes.ui.model.PlayerModel player = com.observernotes.ui.model.PlayerModel.NONE;

    /** Informa o estado do player (muda a ordem do Tab). */
    public void setPlayer(com.observernotes.ui.model.PlayerModel pm) {
        this.player = pm == null ? com.observernotes.ui.model.PlayerModel.NONE : pm;
    }

    public UiAction current() {
        return current;
    }

    public boolean isField() {
        return FIELD.equals(current);
    }

    /** Foco definido por clique (ou null para esconder o indicador). */
    public void set(UiAction target) {
        current = target;
        armed = null;
    }

    public void clear() {
        set(null);
    }

    /** Move o foco; sem foco, Tab vai ao primeiro e Shift+Tab ao último. */
    public void move(ScreenModel m, boolean forward) {
        List<UiAction> o = order(m, player);
        int i = current == null ? -1 : o.indexOf(current);
        int next;
        if (i < 0) {
            next = forward ? 0 : o.size() - 1;
        } else {
            next = (i + (forward ? 1 : -1) + o.size()) % o.size();
        }
        set(o.get(next));
    }

    /** Se o item focado deixou de existir (tarefa excluída, outro painel), tira o foco. */
    public void retain(ScreenModel m) {
        if (current != null && !order(m, player).contains(current)) {
            clear();
        }
    }

    /** Qualquer outra tecla cancela uma confirmação pendente. */
    public void cancelPending() {
        armed = null;
    }

    public boolean hasPending() {
        return armed != null;
    }

    /**
     * Espaço ou Enter no item focado.
     *
     * @param hint recebe o aviso quando a ação pede confirmação
     */
    public Activation activate(ScreenModel m, boolean space, java.util.function.Consumer<String> hint) {
        UiAction a = current;
        if (a == null || FIELD.equals(a)) {
            return Activation.NONE;
        }
        switch (a.type()) {
            case TOGGLE_TASK:
                armed = null;
                return space ? new Activation(a) : Activation.NONE;     // Enter nunca marca
            case DELETE_TASK:
                if (!space) {
                    armed = null;
                    return Activation.NONE;                              // Enter nunca exclui
                }
                return confirm(a, hint, "Pressione Espaço de novo para excluir \"" + titleOf(m, a.key()) + "\"");
            case PLAYER_REMOVE:
                armed = null;
                return space ? new Activation(a) : Activation.NONE;     // Enter nunca remove da playlist
            case CLEAR:
                return confirm(a, hint, "Pressione Espaço (ou Enter) de novo para limpar o histórico");
            default:
                armed = null;
                return new Activation(a);
        }
    }

    private Activation confirm(UiAction a, java.util.function.Consumer<String> hint, String text) {
        long now = clock.getAsLong();
        if (a.equals(armed) && now - armedAt < CONFIRM_MS) {
            armed = null;
            return new Activation(a);
        }
        armed = a;
        armedAt = now;
        hint.accept(text);
        return Activation.NONE;
    }

    private static String titleOf(ScreenModel m, String id) {
        return m.tasks().stream().filter(t -> t.id().equals(id)).map(t -> t.title()).findFirst().orElse("tarefa");
    }
}
