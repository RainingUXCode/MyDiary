package com.observernotes.ui.scene;

import com.observernotes.ui.FooterRotation;
import com.observernotes.ui.Fonts;
import com.observernotes.ui.Layout;
import com.observernotes.ui.Palette;
import com.observernotes.ui.TextPainter;
import com.observernotes.ui.assets.Asset;
import com.observernotes.ui.assets.SliceSpec;
import com.observernotes.ui.assets.SpriteCache.Tint;
import com.observernotes.ui.anim.EffectLayer;
import com.observernotes.ui.anim.EffectLayer.Effect;
import com.observernotes.ui.model.AchievementTier;
import com.observernotes.ui.model.HistoryRowModel;
import com.observernotes.ui.model.LittleNotesModel;
import com.observernotes.ui.model.ObserverCardModel;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.PlayerModel;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.model.TaskRowModel;
import com.observernotes.ui.render.Surface;
import com.observernotes.ui.widget.ButtonId;
import com.observernotes.ui.widget.InteractionState;
import com.observernotes.ui.widget.TextInput;
import com.observernotes.ui.widget.UiAction;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.EnumMap;
import java.util.Map;

/**
 * Compõe a tela inteira em coordenadas lógicas a partir de um {@link ScreenModel}. É só
 * desenho: não conhece domínio, eventos nem persistência.
 */
public final class MainScene {

    private static final SliceSpec PILL = SliceSpec.stretch(88, 88);
    private static final SliceSpec ROW = SliceSpec.stretch(50, 50);
    private static final SliceSpec FIELD = SliceSpec.tiled(150, 70, 47);
    private static final SliceSpec CARD = SliceSpec.stretch(60, 60);

    private static final Map<ButtonId, Rectangle2D> HOTSPOTS = new EnumMap<>(ButtonId.class);

    static {
        HOTSPOTS.put(ButtonId.NAV_LIST, Layout.NAV_LIST);
        HOTSPOTS.put(ButtonId.NAV_ALERTS, Layout.NAV_ALERTS);
        HOTSPOTS.put(ButtonId.NAV_RECORD, Layout.NAV_RECORD);
        HOTSPOTS.put(ButtonId.ADD, Layout.ADD);
    }

    private final Fonts fonts;

    // Contexto do quadro atual (a cena roda só na thread de eventos).
    private InteractionState ui;
    private EffectLayer fx;
    private UiAction focus;
    private PlayerModel player = PlayerModel.NONE;
    private FooterRotation.View footerView;
    private String taskMarqueeId;
    private long taskMarqueeSince;

    private static final long MARQUEE_DELAY_MS = 1000;
    private static final long MARQUEE_END_PAUSE_MS = 650;
    private static final long MARQUEE_RETURN_MS = 700;
    private static final long MARQUEE_START_PAUSE_MS = 350;
    private static final double MARQUEE_SPEED = 0.050; // pixels lógicos por milissegundo
    private static final long TASK_MARQUEE_START_PAUSE_MS = 650;
    private static final long TASK_MARQUEE_END_PAUSE_MS = 700;
    private static final double TASK_MARQUEE_SPEED = 0.035;

    /** Estado atual da alternância do rodapé (null = sem alternância: mostra só aviso/última mensagem). */
    public void setFooterView(FooterRotation.View view) {
        this.footerView = view;
    }

    /** Define a única tarefa cujo título longo pode se mover neste quadro. */
    public void setTaskMarqueeTarget(String taskId, long now) {
        if (!java.util.Objects.equals(taskMarqueeId, taskId)) {
            taskMarqueeId = taskId;
            taskMarqueeSince = now;
        }
    }

    public MainScene(Fonts fonts) {
        this.fonts = fonts;
    }

    /** Largura útil do título de uma tarefa na linha (entre a caixinha e o X). */
    private static double taskTitleMax(Rectangle2D r) {
        double h = r.getHeight();
        return r.getMaxX() - h * 1.1 - (r.getX() + h * 1.25);
    }

    /** Título da tarefa: tamanho 26, reduzido só muito levemente e depois cortado com "…". */
    public static TextPainter.Fitted taskTitleFit(Graphics2D g, Fonts fonts, String title, double max) {
        return TextPainter.fitEllipsis(g, title, fonts.body(26), max, 0.94);
    }

    /** Id da tarefa sob o ponto; a linha inteira conta, sem transformar o texto em um novo comando. */
    public static String taskIdAt(Point2D logical, ScreenModel m) {
        if (m.mode() != PanelMode.TASKS) {
            return null;
        }
        for (int i = 0; i < m.tasks().size() && i < Layout.rowCount(); i++) {
            if (Layout.row(i).contains(logical)) {
                return m.tasks().get(i).id();
            }
        }
        return null;
    }

    /** Título completo da tarefa sob o ponto se ele aparece cortado na tela (para o tooltip); senão null. */
    public static String truncatedTaskTitleAt(Graphics2D g, Fonts fonts, Point2D logical, ScreenModel m) {
        if (m.mode() != PanelMode.TASKS) {
            return null;
        }
        for (int i = 0; i < m.tasks().size() && i < Layout.rowCount(); i++) {
            Rectangle2D row = Layout.row(i);
            if (row.contains(logical)) {
                String title = m.tasks().get(i).title();
                return taskTitleFit(g, fonts, title, taskTitleMax(row)).truncated() ? title : null;
            }
        }
        return null;
    }

    /** Qual botão com hover/pressionado está sob o ponto (coordenadas lógicas), ou null. */
    public static ButtonId hitTest(Point2D logical, ScreenModel m) {
        return hitTest(logical, m, PlayerModel.NONE);
    }

    /** Com o player: anterior, tocar/pausar, próximo, "♪ Playlist" e (com a playlist aberta) "+ Música" também são botões. */
    public static ButtonId hitTest(Point2D logical, ScreenModel m, PlayerModel pm) {
        if (pm.available()) {
            if (Layout.PLAYER_PREVIOUS.contains(logical)) {
                return ButtonId.PLAYER_PREVIOUS;
            }
            if (Layout.PLAYER_PAUSE.contains(logical)) {
                return ButtonId.PLAYER_TOGGLE;
            }
            if (Layout.PLAYER_NEXT.contains(logical)) {
                return ButtonId.PLAYER_NEXT;
            }
            if (Layout.NEW_BADGE.contains(logical)) {
                return ButtonId.PLAYER_PANEL;
            }
            if (pm.panelOpen() && Layout.ADD_MUSIC.contains(logical)) {
                return ButtonId.NEW_MUSIC;                         // "+ Música" só existe dentro da playlist
            }
        }
        for (var e : HOTSPOTS.entrySet()) {
            if (e.getValue().contains(logical)) {
                return e.getKey();
            }
        }
        if (m.mode() == PanelMode.HISTORY && Layout.CLEAR.contains(logical)) {
            return ButtonId.CLEAR;
        }
        if (m.mode() == PanelMode.TASKS) {
            if (m.taskPages().current() > 0 && Layout.TASK_PAGE_PREVIOUS.contains(logical)) {
                return ButtonId.TASK_PAGE_PREVIOUS;
            }
            if (m.taskPages().current() + 1 < m.taskPages().count() && Layout.TASK_PAGE_NEXT.contains(logical)) {
                return ButtonId.TASK_PAGE_NEXT;
            }
        }
        return null;
    }

    /** Ação do usuário para um clique neste ponto, ou null. Itens são identificados pela chave do modelo. */
    public static UiAction actionAt(Point2D logical, ScreenModel m) {
        return actionAt(logical, m, PlayerModel.NONE);
    }

    public static UiAction actionAt(Point2D logical, ScreenModel m, PlayerModel pm) {
        ButtonId b = hitTest(logical, m, pm);
        if (b != null) {
            return UiAction.of(b);
        }
        if (pm.available() && pm.panelOpen() && Layout.MAIN_PANEL.contains(logical)) {
            return playlistActionAt(logical, pm);                    // a playlist cobre o painel central
        }
        UiAction noteAction = noteActionAt(logical, m);
        if (noteAction != null) {
            return noteAction;
        }
        if (m.mode() == PanelMode.TASKS) {
            for (int i = 0; i < m.tasks().size(); i++) {
                Rectangle2D row = Layout.row(i);
                String id = m.tasks().get(i).id();
                if (grow(checkRect(row), 8).contains(logical)) {
                    return new UiAction(UiAction.Type.TOGGLE_TASK, id);
                }
                if (grow(closeRect(row), 6).contains(logical)) {
                    return new UiAction(UiAction.Type.DELETE_TASK, id);
                }
            }
        } else if (m.mode() == PanelMode.OBSERVERS) {
            for (int i = 0; i < m.observers().size(); i++) {
                if (grow(toggleRect(cardRect(i)), 10).contains(logical)) {
                    return new UiAction(UiAction.Type.TOGGLE_OBSERVER, m.observers().get(i).key());
                }
            }
        }
        return null;
    }

    /** Ação do mural sob o ponto, independente do painel central selecionado. */
    public static UiAction noteActionAt(Point2D logical, ScreenModel m) {
        LittleNotesModel notes = m.littleNotes();
        int visible = notes.items().size() + (notes.adding() ? 1 : 0);
        for (int i = visible - 1; i >= 0; i--) {
            String id = i < notes.items().size() ? notes.items().get(i).id() : LittleNotesModel.NEW_ID;
            Rectangle2D card = noteRect(i);
            if (noteCloseRect(card).contains(logical)) {
                return new UiAction(UiAction.Type.NOTE_DELETE, id);
            }
            if (card.contains(logical)) {
                return new UiAction(UiAction.Type.NOTE_EDIT, id);
            }
        }
        if (notes.canAdd() && noteAddRect(notes).contains(logical)) {
            return UiAction.of(UiAction.Type.NOTE_ADD);
        }
        return null;
    }

    /** Texto completo do bilhete se a versão desenhada usa reticências. */
    public static String truncatedNoteTextAt(Graphics2D g, Fonts fonts, Point2D logical, ScreenModel m) {
        for (int i = 0; i < m.littleNotes().items().size(); i++) {
            if (noteRect(i).contains(logical)) {
                String text = m.littleNotes().items().get(i).text();
                double max = noteRect(i).getWidth() - 39;
                return TextPainter.fitEllipsis(g, text, fonts.body(18), max, 1).truncated() ? text : null;
            }
        }
        return null;
    }

    public static Rectangle2D noteRect(int index) {
        Rectangle2D a = Layout.NOTES_AREA;
        double width = a.getWidth() - 23;
        return new Rectangle2D.Double(a.getCenterX() - width / 2, a.getY() + 6 + index * 38,
                width, 34);
    }

    public static Rectangle2D emptyNoteRect() {
        Rectangle2D a = Layout.NOTES_AREA;
        double width = a.getWidth() - 23;
        return new Rectangle2D.Double(a.getCenterX() - width / 2, a.getY() + 8, width, 48);
    }

    public static Rectangle2D noteAddRect(LittleNotesModel notes) {
        Rectangle2D a = Layout.NOTES_AREA;
        double cy = notes.items().isEmpty() ? a.getY() + 88
                : Math.min(a.getMaxY() - 16, noteRect(notes.items().size() - 1).getMaxY() + 24);
        return new Rectangle2D.Double(a.getCenterX() - 14, cy - 14, 28, 28);
    }

    private static Rectangle2D noteCloseRect(Rectangle2D card) {
        return new Rectangle2D.Double(card.getMaxX() - 20, card.getY() + 3, 17, 17);
    }

    /** Círculo desenhado um pouco menor que o hotspot, preservando uma área clicável confortável. */
    private static Rectangle2D noteCloseVisualRect(Rectangle2D card) {
        Rectangle2D hit = noteCloseRect(card);
        return new Rectangle2D.Double(hit.getCenterX() - 7.5, hit.getCenterY() - 7.5, 15, 15);
    }

    /** True se o ponto está sobre algo clicável (para o cursor de mão). */
    public static boolean overClickable(Point2D logical, ScreenModel m) {
        return actionAt(logical, m) != null;
    }

    public static boolean overClickable(Point2D logical, ScreenModel m, PlayerModel pm) {
        return actionAt(logical, m, pm) != null;
    }

    // ---- geometria do painel da playlist (linhas 0 a 3 = faixas; 4 = música; 5 = SFX) ----

    private static final int CONTROL_ROW = 4;
    private static final int SFX_ROW = 5;

    public static Rectangle2D playlistRemoveRect(int slot) {
        return closeRect(Layout.row(slot));
    }

    public static Rectangle2D playlistDownRect(int slot) {
        Rectangle2D c = closeRect(Layout.row(slot));
        return moved(c, -c.getWidth() * 1.4, 0);
    }

    public static Rectangle2D playlistUpRect(int slot) {
        Rectangle2D c = closeRect(Layout.row(slot));
        return moved(c, -c.getWidth() * 2.8, 0);
    }

    public static Rectangle2D repeatRect() {
        return audioToggleRect(CONTROL_ROW);
    }

    public static Rectangle2D volumeMinusRect() {
        return audioMinusRect(CONTROL_ROW);
    }

    public static Rectangle2D volumePlusRect() {
        return audioPlusRect(CONTROL_ROW);
    }

    public static Rectangle2D volumeBarRect() {
        return audioBarRect(CONTROL_ROW);
    }

    public static Rectangle2D sfxToggleRect() {
        return audioToggleRect(SFX_ROW);
    }

    public static Rectangle2D sfxVolumeMinusRect() {
        return audioMinusRect(SFX_ROW);
    }

    public static Rectangle2D sfxVolumePlusRect() {
        return audioPlusRect(SFX_ROW);
    }

    public static Rectangle2D sfxVolumeBarRect() {
        return audioBarRect(SFX_ROW);
    }

    /** Mesma grade nas duas linhas: botão, indicador, menos, slider e mais. */
    private static Rectangle2D audioToggleRect(int rowIndex) {
        Rectangle2D r = Layout.row(rowIndex);
        double h = Layout.row(CONTROL_ROW).getHeight() * 0.72;
        return new Rectangle2D.Double(r.getX() + 2, r.getCenterY() - h / 2, r.getWidth() * 0.27, h);
    }

    private static Rectangle2D audioMinusRect(int rowIndex) {
        Rectangle2D r = Layout.row(rowIndex);
        double sq = Layout.row(CONTROL_ROW).getHeight() * 0.56;
        return new Rectangle2D.Double(r.getX() + r.getWidth() * 0.52, r.getCenterY() - sq / 2, sq, sq);
    }

    private static Rectangle2D audioPlusRect(int rowIndex) {
        Rectangle2D r = Layout.row(rowIndex);
        double sq = Layout.row(CONTROL_ROW).getHeight() * 0.56;
        return new Rectangle2D.Double(r.getMaxX() - sq - 4, r.getCenterY() - sq / 2, sq, sq);
    }

    private static Rectangle2D audioBarRect(int rowIndex) {
        Rectangle2D minus = audioMinusRect(rowIndex);
        Rectangle2D plus = audioPlusRect(rowIndex);
        double x0 = minus.getMaxX() + 10;
        return new Rectangle2D.Double(x0, minus.getCenterY() - 6, plus.getX() - 10 - x0, 12);
    }

    private static Rectangle2D audioVolumeLabelRect(int rowIndex) {
        Rectangle2D toggle = audioToggleRect(rowIndex);
        Rectangle2D minus = audioMinusRect(rowIndex);
        Rectangle2D row = Layout.row(rowIndex);
        return new Rectangle2D.Double(toggle.getMaxX() + 6, row.getY(),
                minus.getX() - toggle.getMaxX() - 10, row.getHeight());
    }

    private static UiAction playlistActionAt(Point2D p, PlayerModel pm) {
        for (int slot = 0; slot < PlayerModel.PANEL_ROWS; slot++) {
            int idx = pm.panelOffset() + slot;
            if (idx >= pm.rows().size()) {
                break;
            }
            String path = pm.rows().get(idx).path();
            if (grow(playlistRemoveRect(slot), 4).contains(p)) {
                return new UiAction(UiAction.Type.PLAYER_REMOVE, path);
            }
            if (grow(playlistDownRect(slot), 4).contains(p)) {
                return new UiAction(UiAction.Type.PLAYER_DOWN, path);
            }
            if (grow(playlistUpRect(slot), 4).contains(p)) {
                return new UiAction(UiAction.Type.PLAYER_UP, path);
            }
            if (Layout.row(slot).contains(p)) {
                return new UiAction(UiAction.Type.PLAYER_PLAY, path);
            }
        }
        if (repeatRect().contains(p)) {
            return UiAction.of(UiAction.Type.PLAYER_REPEAT);
        }
        if (grow(volumeMinusRect(), 4).contains(p)) {
            return new UiAction(UiAction.Type.PLAYER_VOLUME, "-");
        }
        if (grow(volumePlusRect(), 4).contains(p)) {
            return new UiAction(UiAction.Type.PLAYER_VOLUME, "+");
        }
        Rectangle2D bar = volumeBarRect();
        if (grow(bar, 8).contains(p)) {
            double v = Math.round((p.getX() - bar.getX()) / bar.getWidth() * 20) / 20.0;
            return new UiAction(UiAction.Type.PLAYER_VOLUME, String.format(java.util.Locale.ROOT, "set:%.2f",
                    Math.max(0.0, Math.min(1.0, v))));
        }
        if (sfxToggleRect().contains(p)) {
            return UiAction.of(UiAction.Type.SFX_TOGGLE);
        }
        if (pm.sfxEnabled() && grow(sfxVolumeMinusRect(), 4).contains(p)) {
            return new UiAction(UiAction.Type.SFX_VOLUME, "-");
        }
        if (pm.sfxEnabled() && grow(sfxVolumePlusRect(), 4).contains(p)) {
            return new UiAction(UiAction.Type.SFX_VOLUME, "+");
        }
        Rectangle2D sfxBar = sfxVolumeBarRect();
        if (pm.sfxEnabled() && grow(sfxBar, 8).contains(p)) {
            double v = Math.round((p.getX() - sfxBar.getX()) / sfxBar.getWidth() * 20) / 20.0;
            return new UiAction(UiAction.Type.SFX_VOLUME, String.format(java.util.Locale.ROOT, "set:%.2f",
                    Math.max(0.0, Math.min(1.0, v))));
        }
        return null;
    }

    public static Rectangle2D checkRect(Rectangle2D row) {
        double h = row.getHeight();
        double size = h * 0.55;
        double centerX = row.getX() + h * 0.67;
        return new Rectangle2D.Double(centerX - size / 2, row.getCenterY() - size / 2, size, size);
    }

    public static Rectangle2D closeRect(Rectangle2D row) {
        double cs = row.getHeight() * 0.50;
        return new Rectangle2D.Double(row.getMaxX() - row.getHeight() * 0.45 - cs, row.getCenterY() - cs / 2, cs, cs);
    }

    public static Rectangle2D toggleRect(Rectangle2D card) {
        double h = card.getHeight();
        double tw = h * 0.95;
        double centerX = card.getMaxX() - h * 1.05;
        return new Rectangle2D.Double(centerX - tw / 2, card.getCenterY() - tw * 62 / 240.0,
                tw, tw * 62 / 120.0);
    }

    private static Rectangle2D grow(Rectangle2D r, double by) {
        return new Rectangle2D.Double(r.getX() - by, r.getY() - by, r.getWidth() + 2 * by, r.getHeight() + 2 * by);
    }

    public static boolean overTextField(Point2D logical) {
        return Layout.TEXT_FIELD.contains(logical);
    }

    public void paint(Surface s, ScreenModel m, TextInput input, InteractionState ui, boolean caretVisible) {
        paint(s, m, input, ui, caretVisible, null, null);
    }

    /**
     * @param fx    efeitos em andamento (null = nenhum)
     * @param focus item com foco de teclado (null = nenhum)
     */
    public void paint(Surface s, ScreenModel m, TextInput input, InteractionState ui, boolean caretVisible,
                      EffectLayer fx, UiAction focus) {
        paint(s, m, input, ui, caretVisible, fx, focus, PlayerModel.NONE);
    }

    public void paint(Surface s, ScreenModel m, TextInput input, InteractionState ui, boolean caretVisible,
                      EffectLayer fx, UiAction focus, PlayerModel player) {
        paint(s, m, input, input, ui, caretVisible, fx, focus, player);
    }

    /** Renderização com entradas independentes: tarefas no topo e edição contextual dos bilhetes. */
    public void paint(Surface s, ScreenModel m, TextInput taskInput, TextInput noteInput,
                      InteractionState ui, boolean caretVisible, EffectLayer fx, UiAction focus,
                      PlayerModel player) {
        this.player = player == null ? PlayerModel.NONE : player;
        this.ui = ui;
        this.fx = fx;
        this.focus = focus;
        Graphics2D g = s.logical();
        try {
            logo(s, g);
            navButton(s, g, ButtonId.NAV_LIST, Layout.NAV_LIST, Asset.BUTTON_LIST, Asset.TINY_HEART, "LIST",
                    Palette.OUTLINE_LIST, ui);
            navButton(s, g, ButtonId.NAV_ALERTS, Layout.NAV_ALERTS, Asset.BUTTON_ALERTS, Asset.TINY_STAR, "ALERTS",
                    Palette.OUTLINE_ALERTS, ui);
            navButton(s, g, ButtonId.NAV_RECORD, Layout.NAV_RECORD, Asset.BUTTON_RECORD, Asset.TINY_BOOK, "RECORD",
                    Palette.OUTLINE_RECORD, ui);
            textField(s, g, taskInput, caretVisible, m.feedback());
            addButton(s, g, ui);
            mainPanel(s, g, m, ui);
            summaryPanel(s, g, m, noteInput, caretVisible);
            footer(s, g, m);
            overlays(s, g, m);
            musicEffects(g);
        } finally {
            g.dispose();
        }
    }

    private boolean isFocused(UiAction a) {
        return focus != null && focus.equals(a);
    }

    private static Rectangle2D scaled(Rectangle2D r, double k) {
        double w = r.getWidth() * k;
        double h = r.getHeight() * k;
        return new Rectangle2D.Double(r.getCenterX() - w / 2, r.getCenterY() - h / 2, w, h);
    }

    private static Rectangle2D moved(Rectangle2D r, double dx, double dy) {
        return new Rectangle2D.Double(r.getX() + dx, r.getY() + dy, r.getWidth(), r.getHeight());
    }

    /**
     * Desenha um item pequeno (checkbox, X, interruptor) com hover (brilho + um pouco maior),
     * pressionado (menor) e anel de foco de teclado.
     */
    private void item(Surface s, Graphics2D g, Asset asset, Rectangle2D r, UiAction id, double extraScale) {
        double k = extraScale;
        if (ui != null && ui.isPressedItem(id)) {
            k *= 0.86;
        } else if (ui != null && ui.isHoverItem(id)) {
            k *= 1.06;
            if (id.type() != UiAction.Type.TOGGLE_TASK
                    && id.type() != UiAction.Type.TOGGLE_OBSERVER
                    && id.type() != UiAction.Type.DELETE_TASK
                    && id.type() != UiAction.Type.PLAYER_REMOVE) {
                ProceduralArt.hoverRing(g, scaled(r, 1.06), Math.min(r.getWidth(), r.getHeight()) * 0.5);
            }
        }
        s.sprite(asset, scaled(r, quantize(k)));
        if (isFocused(id)) {
            ProceduralArt.focusRing(g, r, Math.min(r.getWidth(), r.getHeight()) * 0.3);
        }
    }

    /** Escalas em passos de 0,04: limita quantos tamanhos de bitmap o cache precisa guardar. */
    private static double quantize(double k) {
        return Math.round(k / 0.04) * 0.04;
    }

    private static Tint tintOf(InteractionState ui, ButtonId id) {
        return ui.isPressed(id) ? Tint.PRESSED : ui.isHover(id) ? Tint.HOVER : Tint.NORMAL;
    }

    // ---------------------------------------------------------------- esquerda e topo

    private void logo(Surface s, Graphics2D g) {
        s.sprite(Asset.LOGO_HEART, Layout.LOGO);
        Rectangle2D r = Layout.LOGO;
        // A estrela ocupa o quadrante superior direito do PNG. As caixas abaixo seguem a área útil
        // livre do coração, não o retângulo transparente inteiro do asset.
        Rectangle2D line1 = new Rectangle2D.Double(r.getX() + r.getWidth() * 0.11, r.getY() + r.getHeight() * 0.29,
                r.getWidth() * 0.78, r.getHeight() * 0.25);
        Rectangle2D line2 = new Rectangle2D.Double(r.getX() + r.getWidth() * 0.23, r.getY() + r.getHeight() * 0.48,
                r.getWidth() * 0.56, r.getHeight() * 0.25);
        // As duas linhas formam um único bloco e preservam a proporção natural da Cloud Soft.
        // Cada tamanho é escolhido para respeitar a área útil do coração sem deformar os glifos.
        var observerFont = fonts.display(42);
        var notesFont = fonts.display(31);
        TextPainter.centered(g, "Observer", observerFont, line1, Color.WHITE, Palette.OUTLINE_LIST, 6);
        TextPainter.centered(g, "Notes", notesFont, line2, Color.WHITE, Palette.OUTLINE_LIST, 6);
    }

    private void navButton(Surface s, Graphics2D g, ButtonId id, Rectangle2D r, Asset base, Asset icon, String label,
                           Color outline, InteractionState ui) {
        s.sliced(base, PILL, r, tintOf(ui, id));
        if (isFocused(UiAction.of(id))) {
            ProceduralArt.focusRing(g, r, r.getHeight());
        }
        double h = r.getHeight();
        // O mockup centraliza ícone + palavra como um único grupo; palavras menores não ficam
        // artificialmente afastadas do ícone e cada label mantém sua própria escala óptica.
        double iconWidthShare = label.equals("LIST") ? 0.235 : label.equals("ALERTS") ? 0.192 : 0.280;
        double iconHeightShare = label.equals("RECORD") ? 0.523 : 0.513;
        Rectangle2D iconGuide = new Rectangle2D.Double(r.getX(),
                r.getCenterY() - h * iconHeightShare / 2,
                r.getWidth() * iconWidthShare, h * iconHeightShare);
        Rectangle2D fittedIcon = fitInside(s, icon, iconGuide);
        float labelSize = label.equals("LIST") ? 27.9f : label.equals("ALERTS") ? 26f : 24f;
        var labelFont = fonts.display(labelSize);
        // Centralização óptica: usa o contorno realmente desenhado da fonte e a área opaca de cada PNG.
        // O intervalo visível entre ícone e palavra é o mesmo nos três botões.
        double iconVisibleWidthRatio = label.equals("ALERTS") ? 47 / 48.0 : 1;
        double iconVisibleHeightRatio = label.equals("LIST") ? 45 / 46.0
                : label.equals("RECORD") ? 48 / 49.0 : 1;
        double iconVisibleWidth = fittedIcon.getWidth() * iconVisibleWidthRatio;
        double iconVisibleHeight = fittedIcon.getHeight() * iconVisibleHeightRatio;
        var glyphs = labelFont.createGlyphVector(g.getFontRenderContext(), label);
        Rectangle2D textVisual = glyphs.getVisualBounds();
        double outlineWidth = 5;
        double textVisibleWidth = textVisual.getWidth() + outlineWidth;
        double gap = 10;
        double groupWidth = iconVisibleWidth + gap + textVisibleWidth;
        double groupX = r.getCenterX() - groupWidth / 2;
        Rectangle2D iconBox = new Rectangle2D.Double(groupX, r.getCenterY() - iconVisibleHeight / 2,
                fittedIcon.getWidth(), fittedIcon.getHeight());
        s.sprite(icon, iconBox);
        double textLeft = groupX + iconVisibleWidth + gap;
        double textX = textLeft - textVisual.getX() + outlineWidth / 2;
        double baseline = r.getCenterY() - textVisual.getCenterY();
        TextPainter.draw(g, label, labelFont, textX, baseline, Color.WHITE, outline, outlineWidth);
    }

    private void textField(Surface s, Graphics2D g, TextInput input, boolean caretVisible, ScreenModel.Feedback fb) {
        boolean warn = fb.fieldHighlight();
        Rectangle2D r = Layout.TEXT_FIELD;
        if (warn) {
            g.setColor(new Color(0xE0, 0x3A, 0x5A, 120));
            g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new RoundRectangle2D.Double(r.getX() - 1, r.getY() - 1, r.getWidth() + 2, r.getHeight() + 2,
                    r.getHeight(), r.getHeight()));
        }
        s.sliced(Asset.TEXT_FIELD_CLEAN, FIELD, r);
        double h = r.getHeight();
        ProceduralArt.pencil(g, new Rectangle2D.Double(r.getX() + h * 0.36, r.getCenterY() - h * 0.36, h * 0.72, h * 0.72));
        double x = r.getX() + h * 1.25;
        var font = fonts.body(25);
        if (input.isEmpty()) {
            String hint = fb.placeholder().isEmpty() ? "Escreva uma nova tarefa..." : fb.placeholder();
            var hintFont = fonts.secondary(25);
            var fittedHint = TextPainter.fit(g, hint, hintFont, r.getMaxX() - h * 0.7 - x);
            TextPainter.draw(g, hint, fittedHint, x,
                    TextPainter.visualCenterBaseline(g, hint, fittedHint, r.getCenterY()),
                    warn ? new Color(0xB02A4A) : Palette.PLACEHOLDER, null, 0);
        } else {
            double max = r.getMaxX() - h * 0.7 - x;
            String txt = input.text();
            var f = TextPainter.fit(g, txt, font, max);
            TextPainter.draw(g, txt, f, x,
                    TextPainter.visualCenterBaseline(g, txt, f, r.getCenterY()), Palette.INK, null, 0);
            font = f;
        }
        if (input.focused() && caretVisible) {
            String before = input.text().substring(0, input.caret());
            double cx = x + (before.isEmpty() ? 0 : TextPainter.width(g, before, font));
            g.setColor(Palette.INK);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Line2D.Double(cx + 1, r.getCenterY() - h * 0.22, cx + 1, r.getCenterY() + h * 0.22));
        }
    }

    private void addButton(Surface s, Graphics2D g, InteractionState ui) {
        // A base do Canva tem proporção própria; cabe na área existente sem nine-slice para não
        // alargar o glossy. Layout.ADD continua sendo o hotspot, preservando clique e hover.
        Rectangle2D visual = fitInside(s, Asset.BUTTON_ADD, Layout.ADD);
        s.sprite(Asset.BUTTON_ADD, visual, tintOf(ui, ButtonId.ADD));
        if (isFocused(UiAction.of(ButtonId.ADD))) {
            ProceduralArt.focusRing(g, visual, visual.getHeight());
        }
        TextPainter.centered(g, "+ ADD", fonts.display(32), moved(scaled(visual, 0.86), 0, 3),
                Color.WHITE, Palette.OUTLINE_ADD, 5);
    }

    // ---------------------------------------------------------------- painel central

    private void mainPanel(Surface s, Graphics2D g, ScreenModel m, InteractionState ui) {
        ProceduralArt.panel(g, Layout.MAIN_PANEL, 30, false);
        boolean list = player.available() && player.panelOpen();
        ribbon(s, g, Asset.TITLE_PINK, m.mode() == PanelMode.OBSERVERS && !list ? Layout.MAIN_RIBBON_WIDE : Layout.MAIN_RIBBON,
                list ? "Playlist" : m.mode().title(), Palette.RIBBON_PINK_TEXT);
        if (player.available() && player.panelOpen()) {
            playlist(s, g);
            return;
        }
        switch (m.mode()) {
            case TASKS -> tasks(s, g, m);
            case OBSERVERS -> observers(s, g, m);
            case HISTORY -> history(s, g, m, ui);
        }
    }

    private void ribbon(Surface s, Graphics2D g, Asset asset, Rectangle2D r, String title, Color text) {
        s.sprite(asset, r);
        // As pontas dobradas e as margens transparentes não fazem parte da área útil do título.
        boolean playlistTitle = title.equals("Playlist");
        boolean accentedTitle = title.equals("Histórico");
        Rectangle2D box = new Rectangle2D.Double(r.getX() + r.getWidth() * 0.16,
                r.getY() + r.getHeight() * (playlistTitle ? 0.24 : accentedTitle ? 0.30 : 0.27),
                r.getWidth() * 0.68, r.getHeight() * 0.55);
        float size = title.length() > 10 ? 29 : playlistTitle ? 28 : accentedTitle ? 28 : 31;
        TextPainter.centered(g, title, fonts.display(size), box, text, new Color(255, 255, 255, 200), 5);
    }

    private void tasks(Surface s, Graphics2D g, ScreenModel m) {
        Rectangle2D b = Layout.MAIN_BADGE;
        s.sprite(Asset.BADGE_CLOUD, b);
        TextPainter.centered(g, (m.taskPages().current() + 1) + "/" + m.taskPages().count(),
                fonts.display(28), moved(scaled(b, 0.88), 0, 3), Palette.TIME_TEXT, null, 0);
        taskPageButton(g, ButtonId.TASK_PAGE_PREVIOUS, Layout.TASK_PAGE_PREVIOUS,
                true, m.taskPages().current() > 0);
        taskPageButton(g, ButtonId.TASK_PAGE_NEXT, Layout.TASK_PAGE_NEXT,
                false, m.taskPages().current() + 1 < m.taskPages().count());
        for (int i = 0; i < Layout.rowCount(); i++) {
            Rectangle2D r = Layout.row(i);
            if (i < m.tasks().size()) {
                TaskRowModel t = m.tasks().get(i);
                Effect created = fx == null ? null : fx.rowEffect(t.id(), EffectLayer.Kind.TASK_CREATED);
                Effect completed = fx == null ? null : fx.rowEffect(t.id(), EffectLayer.Kind.TASK_COMPLETED);
                double alpha = 1;
                double dy = 0;
                if (created != null) {
                    double e = EffectLayer.easeOutCubic(EffectLayer.progress(created, fx.now()));
                    alpha = e;
                    dy = (1 - e) * 14;
                }
                double pop = 1;
                if (completed != null) {
                    double p = EffectLayer.progress(completed, fx.now());
                    pop = 1 + 0.35 * Math.sin(Math.PI * Math.min(1, p * 2.2));
                }
                Rectangle2D rr = moved(r, 0, dy);
                s.setAlpha((float) alpha);
                var oldComposite = g.getComposite();
                g.setComposite(AlphaComposite.SrcOver.derive((float) alpha));
                taskRow(s, g, rr, t, pop);
                g.setComposite(oldComposite);
                s.setAlpha(1f);
                if (completed != null) {
                    completionBurst(g, r, EffectLayer.progress(completed, fx.now()));
                }
            } else {
                emptySlot(s, g, r, "espaço livre");
            }
        }
        if (fx != null) {
            for (Effect e : fx.active()) {
                if (e.kind() == EffectLayer.Kind.TASK_DELETED && e.slot() < Layout.rowCount()) {
                    double p = EffectLayer.progress(e, fx.now());
                    double ease = EffectLayer.easeOutCubic(p);
                    Rectangle2D rr = moved(Layout.row(e.slot()), ease * 44, 0);
                    float a = (float) (0.9 * (1 - p));
                    s.setAlpha(a);
                    var old = g.getComposite();
                    g.setComposite(AlphaComposite.SrcOver.derive(a));
                    taskRow(s, g, rr, e.ghost(), 1, false);
                    g.setComposite(old);
                    s.setAlpha(1f);
                }
            }
        }
    }

    private void taskPageButton(Graphics2D g, ButtonId id, Rectangle2D r, boolean left, boolean enabled) {
        boolean pressed = enabled && ui != null && ui.isPressed(id);
        boolean hovered = enabled && ui != null && ui.isHover(id);
        double diameter = pressed ? 22 : 23;
        double cx = r.getCenterX();
        double cy = r.getCenterY();
        var circle = new Ellipse2D.Double(cx - diameter / 2, cy - diameter / 2,
                diameter, diameter);
        Color top = new Color(enabled ? (hovered ? 0xFFF9D8 : 0xFFF2B4) : 0xFFF7E7);
        Color bottom = new Color(enabled ? (pressed ? 0xECA234 : 0xFFC354) : 0xF9E6BF);
        g.setPaint(new GradientPaint(0, (float) circle.getMinY(), top,
                0, (float) circle.getMaxY(), bottom));
        g.fill(circle);
        g.setColor(new Color(enabled ? 0xB96720 : 0xDDBA97));
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(circle);

        double inset = 2.5;
        g.setColor(new Color(255, 255, 255, enabled ? 225 : 170));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(circle.getX() + inset, circle.getY() + inset,
                diameter - 2 * inset, diameter - 2 * inset, 42, 101, Arc2D.OPEN));

        double dx = left ? 2.8 : -2.8;
        double dy = 4.5;
        java.awt.geom.Path2D chevron = new java.awt.geom.Path2D.Double();
        chevron.moveTo(cx + dx, cy - dy);
        chevron.lineTo(cx - dx, cy);
        chevron.lineTo(cx + dx, cy + dy);
        g.setColor(enabled ? Palette.TIME_TEXT : new Color(0xCBA982));
        g.setStroke(new BasicStroke(2.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(chevron);

        if (isFocused(UiAction.of(id))) {
            ProceduralArt.focusRing(g, circle.getBounds2D(), diameter);
        }
    }

    /** Brilho que atravessa a linha e faíscas saindo do checkbox. */
    private void completionBurst(Graphics2D g, Rectangle2D row, double p) {
        float glow = (float) (0.5 * (1 - p));
        g.setColor(new Color(1f, 1f, 1f, Math.max(0f, glow)));
        g.fill(new RoundRectangle2D.Double(row.getX(), row.getY(), row.getWidth(), row.getHeight(), row.getHeight(),
                row.getHeight()));
        Rectangle2D c = checkRect(row);
        double ease = EffectLayer.easeOutCubic(p);
        for (int k = 0; k < 8; k++) {
            double ang = k * Math.PI / 4 + 0.3;
            double dist = c.getWidth() * 0.5 + 8 + 38 * ease;
            Color col = k % 2 == 0 ? new Color(255, 214, 77) : new Color(255, 143, 184);
            ProceduralArt.sparkle(g, c.getCenterX() + Math.cos(ang) * dist, c.getCenterY() + Math.sin(ang) * dist,
                    Math.max(1.5, 7 * (1 - p)), withAlpha(col, 1 - p));
        }
    }

    private static Color withAlpha(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * Math.max(0, Math.min(1, a))));
    }

    /** Linha vazia: exatamente a mesma base geométrica da tarefa pendente, apenas esmaecida. */
    private void emptySlot(Surface s, Graphics2D g, Rectangle2D r, String text) {
        s.setAlpha(0.55f);
        s.sliced(Asset.TASK_PENDING, ROW, r);
        s.setAlpha(1f);
        g.setColor(withAlpha(new Color(0xEA5F93), 0.70));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                new float[] {6f, 5f}, 0f));
        double insetX = 8;
        double insetY = 7;
        g.draw(new RoundRectangle2D.Double(r.getX() + insetX, r.getY() + insetY,
                r.getWidth() - 2 * insetX, r.getHeight() - 2 * insetY,
                r.getHeight() - 2 * insetY, r.getHeight() - 2 * insetY));
        s.setAlpha(0.82f);
        s.sprite(Asset.HEART_OUTLINE_SMALL,
                fitInside(s, Asset.HEART_OUTLINE_SMALL, scaled(checkRect(r), 0.80)));
        s.setAlpha(1f);
        double x = r.getX() + r.getHeight() * 1.25;
        TextPainter.left(g, text, TextPainter.fit(g, text, fonts.secondary(24), r.getMaxX() - r.getHeight() * 0.6 - x), x,
                r.getCenterY(), Palette.PLACEHOLDER);
    }

    private void taskRow(Surface s, Graphics2D g, Rectangle2D r, TaskRowModel t, double checkPop) {
        taskRow(s, g, r, t, checkPop, true);
    }

    private void taskRow(Surface s, Graphics2D g, Rectangle2D r, TaskRowModel t, double checkPop, boolean interactive) {
        s.sliced(t.done() ? Asset.TASK_DONE : Asset.TASK_PENDING, ROW, taskBaseRect(r, t.done()));
        double h = r.getHeight();
        UiAction toggle = new UiAction(UiAction.Type.TOGGLE_TASK, t.id());
        UiAction delete = new UiAction(UiAction.Type.DELETE_TASK, t.id());
        Asset checkAsset = t.done() ? Asset.CHECK_MARK : Asset.CHECK_BOX;
        Rectangle2D checkVisual = fitInside(s, checkAsset, checkRect(r));
        if (interactive) {
            item(s, g, checkAsset, checkVisual, toggle, checkPop);
        } else {
            s.sprite(checkAsset, checkVisual);
        }
        double x = r.getX() + h * 1.25;
        double textWidth = taskTitleMax(r);
        var fit = taskTitleFit(g, fonts, t.title(), textWidth);
        Color c = t.done() ? Palette.INK_DONE : Palette.INK;
        // Durante o atraso inicial a linha conserva exatamente o estado normal com reticências.
        boolean marquee = interactive && fit.truncated() && t.id().equals(taskMarqueeId)
                && taskMarqueeElapsed() >= TASK_MARQUEE_START_PAUSE_MS;
        String paintedText = marquee ? t.title() : fit.text();
        double offset = marquee ? taskMarqueeOffset(fit.font(), g, paintedText, textWidth) : 0;
        Graphics2D textGraphics = (Graphics2D) g.create();
        try {
            // O recorte termina antes do X e começa depois do checkbox. Só os glifos se deslocam.
            textGraphics.clip(new Rectangle2D.Double(x, r.getY() + 3, textWidth, r.getHeight() - 6));
            TextPainter.left(textGraphics, paintedText, fit.font(), x - offset, r.getCenterY(), c);
            if (t.done()) {
                TextPainter.strike(textGraphics, x - offset, r.getCenterY(),
                        TextPainter.width(textGraphics, paintedText, fit.font()), c, 2.2);
            }
        } finally {
            textGraphics.dispose();
        }
        if (interactive) {
            item(s, g, Asset.CLOSE_BUTTON, closeRect(r), delete, 1);
        } else {
            s.sprite(Asset.CLOSE_BUTTON, closeRect(r));
        }
    }

    /** Movimento cíclico: pausa no início, ida lenta, pausa no fim e retorno lento. */
    private double taskMarqueeOffset(java.awt.Font font, Graphics2D g, String text, double availableWidth) {
        double overflow = Math.max(0, TextPainter.width(g, text, font) - availableWidth);
        if (overflow <= 0) {
            return 0;
        }
        long travel = Math.max(1, Math.round(overflow / TASK_MARQUEE_SPEED));
        long cycle = TASK_MARQUEE_START_PAUSE_MS + travel + TASK_MARQUEE_END_PAUSE_MS + travel;
        long elapsed = taskMarqueeElapsed() % cycle;
        if (elapsed < TASK_MARQUEE_START_PAUSE_MS) {
            return 0;
        }
        elapsed -= TASK_MARQUEE_START_PAUSE_MS;
        if (elapsed < travel) {
            return overflow * smoothStep(elapsed / (double) travel);
        }
        elapsed -= travel;
        if (elapsed < TASK_MARQUEE_END_PAUSE_MS) {
            return overflow;
        }
        elapsed -= TASK_MARQUEE_END_PAUSE_MS;
        return overflow * (1 - smoothStep(elapsed / (double) travel));
    }

    private long taskMarqueeElapsed() {
        return Math.max(0, (fx == null ? taskMarqueeSince : fx.now()) - taskMarqueeSince);
    }

    private static double smoothStep(double t) {
        double clamped = Math.max(0, Math.min(1, t));
        return clamped * clamped * (3 - 2 * clamped);
    }

    /**
     * Compensa somente as margens transparentes diferentes dos dois PNGs de tarefa. Assim a borda
     * aparente de pendente e concluída ocupa os mesmos pixels, sem deformar o desenho nem mudar o hitbox.
     */
    private static Rectangle2D taskBaseRect(Rectangle2D r, boolean done) {
        return done
                ? new Rectangle2D.Double(r.getX() - 2, r.getY() + 1, r.getWidth() + 1, r.getHeight() - 2)
                : r;
    }

    private void observers(Surface s, Graphics2D g, ScreenModel m) {
        for (int i = 0; i < Layout.rowCount() && i < m.observers().size(); i++) {
            Rectangle2D r = cardRect(i);
            ObserverCardModel c = m.observers().get(i);
            s.sliced(Asset.OBSERVER_CARD_CLEAN, CARD, r);
            double h = r.getHeight();
            s.sprite(c.icon(), fitInside(s, c.icon(), new Rectangle2D.Double(r.getX() + h * 0.28, r.getY() + h * 0.18,
                    h * 0.64, h * 0.64)));
            double dividerX = r.getX() + h * 1.08;
            g.setColor(new Color(0xE887AF));
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Line2D.Double(dividerX, r.getY() + h * 0.22,
                    dividerX, r.getY() + h * 0.78));
            double x = r.getX() + h * 1.28;
            double max = r.getMaxX() - x - h * 2.0;
            TextPainter.left(g, c.title(), TextPainter.fit(g, c.title(), fonts.display(22), max), x,
                    r.getY() + h * 0.40, Palette.INK);
            TextPainter.left(g, c.description(), TextPainter.fit(g, c.description(), fonts.body(18), max), x,
                    r.getY() + h * 0.66, Palette.INK_MUTED);
            item(s, g, c.enabled() ? Asset.TOGGLE_ON : Asset.TOGGLE_OFF, toggleRect(r),
                    new UiAction(UiAction.Type.TOGGLE_OBSERVER, c.key()), 1);
        }
    }

    /** Três cartões iguais, com 34 px de respiro, centralizados no espaço útil do painel. */
    public static Rectangle2D cardRect(int index) {
        Rectangle2D first = Layout.row(0);
        double h = 74;
        double gap = 34;
        double contentTop = 248;
        double contentHeight = 334;
        double top = contentTop + (contentHeight - (3 * h + 2 * gap)) / 2.0;
        return new Rectangle2D.Double(first.getX(), top + index * (h + gap), first.getWidth(), h);
    }

    private void history(Surface s, Graphics2D g, ScreenModel m, InteractionState ui) {
        Rectangle2D b = Layout.CLEAR;
        s.sliced(Asset.BUTTON_CLEAR, PILL, b, tintOf(ui, ButtonId.CLEAR));
        if (isFocused(UiAction.of(ButtonId.CLEAR))) {
            ProceduralArt.focusRing(g, b, b.getHeight());
        }
        TextPainter.centered(g, "CLEAR", fonts.display(24), scaled(b, 0.86), new Color(0xF0447D), Color.WHITE, 4);
        if (m.history().isEmpty() || !m.views().historyOn()) {
            Rectangle2D area = Layout.row(2);
            TextPainter.centered(g, m.views().historyOn() ? "Nenhum registro ainda" : "Histórico desligado",
                    fonts.secondary(26),
                    new Rectangle2D.Double(Layout.MAIN_PANEL.getX(), area.getY(), Layout.MAIN_PANEL.getWidth(), area.getHeight()),
                    Palette.PLACEHOLDER, null, 0);
            return;
        }
        for (int i = 0; i < Layout.rowCount() && m.historyOffset() + i < m.history().size(); i++) {
            Rectangle2D r = Layout.row(i);
            HistoryRowModel h = m.history().get(m.historyOffset() + i);
            s.sliced(Asset.TASK_PENDING, ROW, r);
            double rh = r.getHeight();
            Rectangle2D pill = new Rectangle2D.Double(r.getX() + rh * 0.35, r.getCenterY() - rh * 0.26, rh * 1.45, rh * 0.52);
            ProceduralArt.timePill(g, pill);
            TextPainter.centered(g, h.time(), fonts.body(18), pill, Palette.TIME_TEXT, null, 0);
            double x = pill.getMaxX() + rh * 0.35;
            double max = r.getMaxX() - x - rh * 0.4;
            TextPainter.left(g, h.text(), TextPainter.fit(g, h.text(), fonts.body(22), max), x, r.getCenterY(), Palette.INK);
        }
        scrollBar(g, m);
    }

    /** Barra de rolagem fina, só quando há mais entradas do que linhas. */
    private void scrollBar(Graphics2D g, ScreenModel m) {
        int total = m.history().size();
        if (total <= ScreenModel.HISTORY_ROWS) {
            return;
        }
        Rectangle2D top = Layout.row(0);
        Rectangle2D bottom = Layout.row(Layout.rowCount() - 1);
        double x = top.getMaxX() + 9;
        double y0 = top.getY();
        double h = bottom.getMaxY() - y0;
        g.setColor(withAlpha(Palette.BORDER_SOFT, 0.55));
        g.fill(new RoundRectangle2D.Double(x, y0, 6, h, 6, 6));
        double thumb = Math.max(26, h * ScreenModel.HISTORY_ROWS / total);
        double maxOff = total - ScreenModel.HISTORY_ROWS;
        double ty = y0 + (h - thumb) * (m.historyOffset() / maxOff);
        g.setColor(Palette.BORDER);
        g.fill(new RoundRectangle2D.Double(x, ty, 6, thumb, 6, 6));
    }

    // ---------------------------------------------------------------- resumo

    private void summaryPanel(Surface s, Graphics2D g, ScreenModel m, TextInput input, boolean caretVisible) {
        ProceduralArt.panel(g, Layout.SUMMARY_PANEL, 30, true);
        ribbon(s, g, Asset.TITLE_PURPLE, Layout.SUMMARY_RIBBON, "Resumo", Palette.RIBBON_PURPLE_TEXT);
        Rectangle2D hr = fitInside(s, Asset.SUMMARY_HEART, Layout.SUMMARY_HEART);
        s.sprite(Asset.SUMMARY_HEART, hr);
        var v = m.views();
        TextPainter.centered(g, v.counterOn() ? v.done() + " de " + v.total() : "–", fonts.display(28),
                new Rectangle2D.Double(hr.getX(), hr.getY() + hr.getHeight() * 0.26, hr.getWidth(), hr.getHeight() * 0.5),
                Color.WHITE, new Color(0xD8387A), 6);
        Rectangle2D t = Layout.SUMMARY_TEXT;
        TextPainter.left(g, "tarefas", fonts.body(26), t.getX(), t.getY() + t.getHeight() * 0.28, Palette.INK);
        TextPainter.left(g, "concluídas", fonts.body(26), t.getX(), t.getY() + t.getHeight() * 0.74, Palette.INK);
        double tw = TextPainter.width(g, "concluídas", fonts.body(26));
        ProceduralArt.sparkleCluster(g, t.getX() + tw + 26, t.getCenterY() + 6);

        double fraction = !v.counterOn() || v.total() == 0 ? 0 : v.done() / (double) v.total();
        Rectangle2D bar = Layout.PROGRESS_BAR;
        ProceduralArt.progressBar(g, bar, fraction);
        if (fraction > 0) {
            double sx = bar.getX() + Math.max(bar.getHeight() * 0.9, bar.getWidth() * fraction) - bar.getHeight() * 0.25;
            s.sprite(Asset.TINY_STAR, new Rectangle2D.Double(sx - 20, bar.getCenterY() - 20, 40, 38));
        }

        littleNotes(s, g, m.littleNotes(), input, caretVisible);
        Rectangle2D at = Layout.ACHIEVEMENT_TILE;
        Asset medal = m.achievementTier().asset();
        double mw = m.achievementTier() == AchievementTier.MAX ? at.getWidth() * 1.2 : at.getWidth() * 1.6;
        mw *= quantize(medalBounce());
        s.spriteByWidth(medal, at.getCenterX(), at.getCenterY(), mw, v.achievementsOn() ? 1f : 0.35f);
        label(g, Layout.ACHIEVEMENT_LABEL, m.achievementLabel(), v.achievementsOn() ? m.achievementState() : "desligadas");
    }

    private void littleNotes(Surface s, Graphics2D g, LittleNotesModel notes, TextInput input, boolean caretVisible) {
        if (notes.items().isEmpty() && !notes.adding()) {
            Rectangle2D empty = emptyNoteRect();
            g.setColor(new Color(0xFFF9F8));
            g.fill(new RoundRectangle2D.Double(empty.getX(), empty.getY(), empty.getWidth(), empty.getHeight(), 8, 8));
            g.setColor(new Color(0xE471A4));
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10, new float[] {4, 4}, 0));
            g.draw(new RoundRectangle2D.Double(empty.getX(), empty.getY(), empty.getWidth(), empty.getHeight(), 8, 8));
            TextPainter.centered(g, "Deixe um bilhete", fonts.secondary(18), empty,
                    Palette.INK_MUTED, null, 0);
        }

        for (int i = 0; i < notes.items().size(); i++) {
            drawNote(g, noteRect(i), i, notes.items().get(i).id(), notes.items().get(i).text(),
                    notes.editingId().equals(notes.items().get(i).id()), input, caretVisible);
        }
        if (notes.adding()) {
            drawNote(g, noteRect(notes.items().size()), notes.items().size(), LittleNotesModel.NEW_ID, "",
                    true, input, caretVisible);
        }

        if (notes.canAdd()) {
            Rectangle2D add = noteAddRect(notes);
            g.setColor(new Color(0xF16A9D));
            g.fill(new Ellipse2D.Double(add.getX(), add.getY(), add.getWidth(), add.getHeight()));
            g.setColor(new Color(0xA92462));
            g.setStroke(new BasicStroke(1.6f));
            g.draw(new Ellipse2D.Double(add.getX(), add.getY(), add.getWidth(), add.getHeight()));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Line2D.Double(add.getCenterX() - 6, add.getCenterY(),
                    add.getCenterX() + 6, add.getCenterY()));
            g.draw(new java.awt.geom.Line2D.Double(add.getCenterX(), add.getCenterY() - 6,
                    add.getCenterX(), add.getCenterY() + 6));
            if (isFocused(UiAction.of(UiAction.Type.NOTE_ADD))) {
                ProceduralArt.focusRing(g, add, add.getHeight());
            }
        }

        Rectangle2D label = Layout.NOTES_LABEL;
        Rectangle2D achievementLabel = Layout.ACHIEVEMENT_LABEL;
        // Mantém os dois blocos do rodapé do Resumo na mesma linha visual.
        double titleCenterY = achievementLabel.getY() + achievementLabel.getHeight() * 0.25;
        double subtitleCenterY = achievementLabel.getY() + achievementLabel.getHeight() * 0.75;
        var titleFont = fonts.body(25);
        String titleText = "Little Notes";
        double titleWidth = TextPainter.width(g, titleText, titleFont);
        double heartRadius = 7;
        double titleGap = 2;
        double groupWidth = titleWidth + titleGap + 2 * heartRadius;
        double tx = label.getCenterX() - groupWidth / 2;
        TextPainter.draw(g, titleText, titleFont, tx,
                TextPainter.centerBaseline(g, titleFont, titleCenterY), Palette.INK, null, 0);
        ProceduralArt.heart(g, tx + titleWidth + titleGap + heartRadius, titleCenterY,
                heartRadius, new Color(255, 120, 170));
        String count = notes.items().isEmpty() ? "nenhum ainda"
                : notes.items().size() + (notes.items().size() == 1 ? " bilhetinho" : " bilhetinhos");
        Rectangle2D sub = new Rectangle2D.Double(label.getX(), subtitleCenterY - 11, label.getWidth(), 22);
        TextPainter.centered(g, count, fonts.secondary(20), sub, Palette.INK_MUTED, null, 0);
    }

    private void drawNote(Graphics2D g, Rectangle2D card, int index, String id, String text,
                          boolean editing, TextInput input, boolean caretVisible) {
        Color[] fills = {new Color(0xFFC7DC), new Color(0xFFF0A8), new Color(0xDCCBFF)};
        Color[] borders = {new Color(0xE96196), new Color(0xD8A52B), new Color(0x9273DC)};
        // Inclinações medidas no mockup: rosa levemente horária, amarela quase reta para o
        // outro lado e lilás com uma variação mínima. O conjunto parece colado à mão sem tombar.
        double[] angles = {2.2, -1.1, 0.8};
        AffineTransform old = g.getTransform();
        g.rotate(Math.toRadians(angles[index % angles.length]), card.getCenterX(), card.getCenterY());
        drawNoteShadow(g, card);
        g.setColor(fills[index % fills.length]);
        g.fill(new RoundRectangle2D.Double(card.getX(), card.getY(), card.getWidth(), card.getHeight(), 7, 7));
        g.setColor(borders[index % borders.length]);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new RoundRectangle2D.Double(card.getX(), card.getY(), card.getWidth(), card.getHeight(), 7, 7));

        double tapeW = 26, tapeH = 9;
        Rectangle2D tape = new Rectangle2D.Double(card.getCenterX() - tapeW / 2,
                card.getY() - tapeH / 2, tapeW, tapeH);
        g.setColor(new Color(255, 255, 255, 170));
        g.fill(tape);
        java.awt.Shape tapeClip = g.getClip();
        g.clip(tape);
        Color border = borders[index % borders.length];
        g.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), 110));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double x = tape.getX() - tapeH; x < tape.getMaxX(); x += 6) {
            g.draw(new java.awt.geom.Line2D.Double(x, tape.getY(), x + tapeH, tape.getMaxY()));
        }
        g.setClip(tapeClip);

        String shown = editing ? input.text() : text;
        var font = fonts.body(17);
        boolean controlsVisible = editing || hoveredNote(id) || isFocused(new UiAction(UiAction.Type.NOTE_DELETE, id));
        double textX = card.getX() + 9;
        double max = card.getWidth() - (controlsVisible ? 39 : 18);
        String display = editing ? shown : TextPainter.ellipsize(g, shown, font, max);
        double drawX;
        if (editing) {
            double caretWidth = TextPainter.width(g, shown.substring(0, input.caret()), font);
            drawX = textX - Math.max(0, caretWidth - max + 4);
        } else {
            drawX = textX + (max - TextPainter.width(g, display, font)) / 2;
        }
        java.awt.Shape oldClip = g.getClip();
        g.clip(new Rectangle2D.Double(textX, card.getY() + 3, max, card.getHeight() - 6));
        TextPainter.draw(g, display, font, drawX,
                TextPainter.visualCenterBaseline(g, display, font, card.getCenterY() + 1), Palette.INK, null, 0);
        if (editing && caretVisible) {
            String before = shown.substring(0, input.caret());
            double cx = drawX + TextPainter.width(g, before, font);
            g.setColor(Palette.INK);
            g.setStroke(new BasicStroke(1.7f));
            g.draw(new java.awt.geom.Line2D.Double(cx + 1, card.getCenterY() - 9, cx + 1, card.getCenterY() + 9));
        }
        g.setClip(oldClip);

        if (controlsVisible) {
            Rectangle2D close = noteCloseVisualRect(card);
            g.setColor(new Color(255, 255, 255, 220));
            g.fill(new Ellipse2D.Double(close.getX(), close.getY(), close.getWidth(), close.getHeight()));
            g.setColor(Palette.BORDER);
            g.setStroke(new BasicStroke(1.3f));
            g.draw(new Ellipse2D.Double(close.getX(), close.getY(), close.getWidth(), close.getHeight()));
            g.draw(new java.awt.geom.Line2D.Double(close.getX() + 4.5, close.getY() + 4.5,
                    close.getMaxX() - 4.5, close.getMaxY() - 4.5));
            g.draw(new java.awt.geom.Line2D.Double(close.getMaxX() - 4.5, close.getY() + 4.5,
                    close.getX() + 4.5, close.getMaxY() - 4.5));
        }
        g.setTransform(old);

        UiAction editAction = new UiAction(UiAction.Type.NOTE_EDIT, id);
        UiAction deleteAction = new UiAction(UiAction.Type.NOTE_DELETE, id);
        if (isFocused(editAction)) {
            ProceduralArt.focusRing(g, card, 8);
        } else if (isFocused(deleteAction)) {
            ProceduralArt.focusRing(g, noteCloseVisualRect(card), 8);
        }
    }

    /** Sombra do papel: 2 px de desfoque, 2 px para baixo, no vinho translúcido do mockup. */
    private static void drawNoteShadow(Graphics2D g, Rectangle2D card) {
        int pad = 6;
        int w = (int) Math.ceil(card.getWidth()) + pad * 2;
        int h = (int) Math.ceil(card.getHeight()) + pad * 2;
        BufferedImage source = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = source.createGraphics();
        sg.setColor(new Color(120, 50, 90, 60));
        sg.fill(new RoundRectangle2D.Double(pad, pad + 2, card.getWidth(), card.getHeight(), 7, 7));
        sg.dispose();
        float[] one = {1f, 4f, 6f, 4f, 1f};
        float[] kernel = new float[25];
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                kernel[y * 5 + x] = one[y] * one[x] / 256f;
            }
        }
        BufferedImage blurred = new ConvolveOp(new Kernel(5, 5, kernel), ConvolveOp.EDGE_NO_OP, null)
                .filter(source, null);
        g.drawImage(blurred, (int) Math.round(card.getX()) - pad, (int) Math.round(card.getY()) - pad, null);
    }

    private boolean hoveredNote(String id) {
        if (ui == null) {
            return false;
        }
        return ui.isHoverItem(new UiAction(UiAction.Type.NOTE_EDIT, id))
                || ui.isHoverItem(new UiAction(UiAction.Type.NOTE_DELETE, id));
    }

    private void label(Graphics2D g, Rectangle2D box, String title, String sub) {
        Rectangle2D top = new Rectangle2D.Double(box.getX(), box.getY(), box.getWidth(), box.getHeight() * 0.5);
        Rectangle2D bottom = new Rectangle2D.Double(box.getX(), box.getCenterY(), box.getWidth(), box.getHeight() * 0.5);
        TextPainter.centered(g, title, fonts.body(25), scaled(top, 0.94), Palette.INK, null, 0);
        TextPainter.centered(g, sub, fonts.secondary(20), scaled(bottom, 0.94), Palette.INK_MUTED, null, 0);
    }

    // ---------------------------------------------------------------- rodapé

    private void footer(Surface s, Graphics2D g, ScreenModel m) {
        ProceduralArt.controlPill(g, Layout.PLAYER_PILL, Palette.PILL_TOP, Palette.PILL_BOTTOM, Palette.PILL_BORDER);
        if (player.available()) {
            playerButton(s, g, ButtonId.PLAYER_PREVIOUS, Layout.PLAYER_PREVIOUS, Asset.PLAYER_PREVIOUS);
            playerButton(s, g, ButtonId.PLAYER_TOGGLE, Layout.PLAYER_PAUSE, player.playing() ? Asset.PLAYER_PAUSE : Asset.PLAYER_PLAY);
            playerButton(s, g, ButtonId.PLAYER_NEXT, Layout.PLAYER_NEXT, Asset.PLAYER_NEXT);
        } else {
            s.sprite(Asset.PLAYER_PREVIOUS, fitInside(s, Asset.PLAYER_PREVIOUS, Layout.PLAYER_PREVIOUS));
            s.sprite(Asset.PLAYER_PAUSE, fitInside(s, Asset.PLAYER_PAUSE, Layout.PLAYER_PAUSE));
            s.sprite(Asset.PLAYER_NEXT, fitInside(s, Asset.PLAYER_NEXT, Layout.PLAYER_NEXT));
        }

        ProceduralArt.pill(g, Layout.FOOTER_MESSAGE, new Color(0xFFFDFB), new Color(0xFFECEA), Palette.BORDER);
        FooterRotation.View v = footerView != null ? footerView : staticFooter(m);
        double footerTextX = Layout.FOOTER_MESSAGE.getX() + Layout.FOOTER_MESSAGE.getHeight() * 0.55;
        double footerTextWidth = Layout.FOOTER_MESSAGE.getWidth() - Layout.FOOTER_MESSAGE.getHeight() * 1.1;
        Rectangle2D footerClip = new Rectangle2D.Double(footerTextX,
                Layout.FOOTER_MESSAGE.getY() + 3, footerTextWidth,
                Layout.FOOTER_MESSAGE.getHeight() - 6);
        var oldClip = g.getClip();
        g.clip(footerClip);
        long now = fx == null ? 0 : fx.now();
        if (v.fading()) {
            footerLine(g, v.previous(), -v.mix() * footerClip.getHeight(), v.previousSince(), now);
            footerLine(g, v.current(), (1 - v.mix()) * footerClip.getHeight(), v.currentSince(), now);
        } else {
            footerLine(g, v.current(), 0, v.currentSince(), now);
        }
        g.setClip(oldClip);

        if (player.available()) {
            double k = ui != null && ui.isPressed(ButtonId.PLAYER_PANEL) ? 0.94
                    : ui != null && ui.isHover(ButtonId.PLAYER_PANEL) ? 1.06 : 1;
            Rectangle2D nb = scaled(Layout.NEW_BADGE, quantize(k));
            Rectangle2D playlistButton = fitInside(s, Asset.BUTTON_PLAYLIST, nb);
            s.sprite(Asset.BUTTON_PLAYLIST, playlistButton);
            if (isFocused(UiAction.of(UiAction.Type.PLAYER_PANEL))) {
                ProceduralArt.focusRing(g, Layout.NEW_BADGE, Layout.NEW_BADGE.getHeight() * 0.4);
            }
        } else if (m.newAchievement()) {
            s.sprite(Asset.BUTTON_NEW, Layout.NEW_BADGE);
            TextPainter.centered(g, "NOVA!", fonts.display(25), scaled(Layout.NEW_BADGE, 0.90), Palette.NOVA_TEXT, null, 0);
        }
        s.sprite(Asset.HEART_PINK_DETAIL, Layout.FOOTER_HEART);
    }

    /** Sem alternância (pré-visualizações e testes): aviso ou última mensagem. */
    private static FooterRotation.View staticFooter(ScreenModel m) {
        var fb = m.feedback();
        FooterRotation.Line line = !fb.notice().isEmpty()
                ? new FooterRotation.Line(fb.notice(), fb.warning() ? FooterRotation.Kind.WARNING : FooterRotation.Kind.EVENT)
                : m.footerMessage().isEmpty() ? new FooterRotation.Line(FooterRotation.EMPTY, FooterRotation.Kind.PLACEHOLDER)
                : new FooterRotation.Line(m.footerMessage(), FooterRotation.Kind.EVENT);
        return new FooterRotation.View(line, null, 1);
    }

    /** Uma linha da área de informação do rodapé, com elevador vertical e letreiro horizontal. */
    private void footerLine(Graphics2D g, FooterRotation.Line line, double yOffset, long visibleSince, long now) {
        Rectangle2D f = Layout.FOOTER_MESSAGE;
        Color c = switch (line.kind()) {
            case WARNING -> new Color(0xB02A4A);
            case PLACEHOLDER -> Palette.PLACEHOLDER;
            default -> Palette.INK;
        };
        double x = f.getX() + f.getHeight() * 0.55;
        double max = f.getWidth() - f.getHeight() * 1.1;
        var font = fonts.body(23);
        String displayText = footerDisplayText(line);
        double noteSpace = line.kind() == FooterRotation.Kind.PLAYING ? 23 : 0;
        double contentWidth = noteSpace + TextPainter.width(g, displayText, font);
        double overflow = Math.max(0, contentWidth - max);
        double offset = marqueeOffset(Math.max(0, now - visibleSince - FooterRotation.SLIDE_MS), overflow);
        double drawX = x - offset;
        double centerY = f.getCenterY() + yOffset;
        if (line.kind() == FooterRotation.Kind.PLAYING) {
            musicNote(g, drawX + 7, centerY, c);
            drawX += noteSpace;
        }
        TextPainter.left(g, displayText, font, drawX, centerY, c);
    }

    /** Duração que mantém a linha atual antes do próximo elevador. */
    public long footerMinimumVisibleMillis(Graphics2D g, FooterRotation.Line line) {
        if (line == null) {
            return FooterRotation.PERIOD_MS;
        }
        Rectangle2D f = Layout.FOOTER_MESSAGE;
        double max = f.getWidth() - f.getHeight() * 1.1;
        var font = fonts.body(23);
        double noteSpace = line.kind() == FooterRotation.Kind.PLAYING ? 23 : 0;
        double overflow = Math.max(0, noteSpace + TextPainter.width(g, footerDisplayText(line), font) - max);
        if (overflow <= 0.5) {
            return FooterRotation.PERIOD_MS;
        }
        long cycle = marqueeCycleMillis(overflow);
        long afterElevator = FooterRotation.PERIOD_MS - FooterRotation.SLIDE_MS;
        if (cycle >= afterElevator) {
            return FooterRotation.SLIDE_MS + cycle;
        }
        long remainder = afterElevator % cycle;
        return remainder <= MARQUEE_DELAY_MS ? FooterRotation.PERIOD_MS
                : FooterRotation.SLIDE_MS + ((afterElevator / cycle) + 1) * cycle;
    }

    private static String footerDisplayText(FooterRotation.Line line) {
        String text = line.text().replace('\u2713', '\u2714');
        return line.kind() == FooterRotation.Kind.PLAYING && text.startsWith("♪ ") ? text.substring(2) : text;
    }

    private static long marqueeCycleMillis(double overflow) {
        long travel = Math.max(900, Math.round(overflow / MARQUEE_SPEED));
        return MARQUEE_DELAY_MS + travel + MARQUEE_END_PAUSE_MS + MARQUEE_RETURN_MS + MARQUEE_START_PAUSE_MS;
    }

    private static double marqueeOffset(long elapsed, double overflow) {
        if (overflow <= 0.5) {
            return 0;
        }
        long travel = Math.max(900, Math.round(overflow / MARQUEE_SPEED));
        long cycle = MARQUEE_DELAY_MS + travel + MARQUEE_END_PAUSE_MS + MARQUEE_RETURN_MS + MARQUEE_START_PAUSE_MS;
        long phase = elapsed % cycle;
        if (phase < MARQUEE_DELAY_MS) {
            return 0;
        }
        phase -= MARQUEE_DELAY_MS;
        if (phase < travel) {
            return overflow * phase / travel;
        }
        phase -= travel;
        if (phase < MARQUEE_END_PAUSE_MS) {
            return overflow;
        }
        phase -= MARQUEE_END_PAUSE_MS;
        if (phase < MARQUEE_RETURN_MS) {
            double t = phase / (double) MARQUEE_RETURN_MS;
            double smooth = t * t * (3 - 2 * t);
            return overflow * (1 - smooth);
        }
        return 0;
    }

    /** Nota musical vetorial mais encorpada, alinhada ao texto da Cloud Soft. */
    private static void musicNote(Graphics2D g, double x, double centerY, Color color) {
        g.setColor(color);
        g.fill(new java.awt.geom.Ellipse2D.Double(x - 5, centerY + 3, 9, 6));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(x + 3, centerY + 5, x + 3, centerY - 9));
        java.awt.geom.Path2D flag = new java.awt.geom.Path2D.Double();
        flag.moveTo(x + 3, centerY - 9);
        flag.curveTo(x + 11, centerY - 8, x + 12, centerY - 3, x + 8, centerY - 1);
        flag.curveTo(x + 9, centerY - 5, x + 6, centerY - 6, x + 3, centerY - 6);
        flag.closePath();
        g.fill(flag);
    }

    private static final Color PLAY_FILL = new Color(0xFF7FB0);
    private static final Color PLAY_LINE = new Color(0xB43C72);
    private static final Color NOTE_COLOR = new Color(0xA55BC9);

    /** Botão do player com hover maior, pressionado menor e foco de teclado. */
    private void playerButton(Surface s, Graphics2D g, ButtonId id, Rectangle2D r, Asset asset) {
        double k = 1;
        if (ui != null && ui.isPressed(id)) {
            k = 0.88;
        } else if (ui != null && ui.isHover(id)) {
            k = 1.06;
        }
        s.sprite(asset, fitInside(s, asset, scaled(r, quantize(k))));
        if (isFocused(UiAction.of(id))) {
            ProceduralArt.focusRing(g, r, r.getHeight() * 0.3);
        }
    }

    /** Pequeno botão redondo com uma seta (subir/descer faixa) com hover, pressionado e foco. */
    private void arrowButton(Graphics2D g, Rectangle2D r, UiAction id, boolean up) {
        double k = ui != null && ui.isPressedItem(id) ? 0.86 : ui != null && ui.isHoverItem(id) ? 1.06 : 1;
        Rectangle2D rr = scaled(r, quantize(k));
        g.setColor(new Color(0xFFE4EF));
        g.fill(new java.awt.geom.Ellipse2D.Double(rr.getX(), rr.getY(), rr.getWidth(), rr.getHeight()));
        g.setColor(Palette.BORDER);
        g.setStroke(new BasicStroke(1.7f));
        g.draw(new java.awt.geom.Ellipse2D.Double(rr.getX(), rr.getY(), rr.getWidth(), rr.getHeight()));
        ProceduralArt.arrow(g, rr, up, Palette.INK);
        if (isFocused(id)) {
            ProceduralArt.focusRing(g, r, r.getHeight() * 0.5);
        }
    }

    /** Fechar da playlist com a mesma geometria visual dos botões de subir/descer. */
    private void playlistRemoveButton(Graphics2D g, Rectangle2D r, UiAction id) {
        double k = ui != null && ui.isPressedItem(id) ? 0.86 : ui != null && ui.isHoverItem(id) ? 1.06 : 1;
        Rectangle2D rr = scaled(r, quantize(k));
        g.setColor(new Color(0xFFE4EF));
        g.fill(new java.awt.geom.Ellipse2D.Double(rr.getX(), rr.getY(), rr.getWidth(), rr.getHeight()));
        g.setColor(Palette.BORDER);
        g.setStroke(new BasicStroke(1.7f));
        g.draw(new java.awt.geom.Ellipse2D.Double(rr.getX(), rr.getY(), rr.getWidth(), rr.getHeight()));

        double inset = rr.getWidth() * 0.32;
        g.setColor(new Color(0xEA5F93));
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(rr.getX() + inset, rr.getY() + inset,
                rr.getMaxX() - inset, rr.getMaxY() - inset));
        g.draw(new java.awt.geom.Line2D.Double(rr.getMaxX() - inset, rr.getY() + inset,
                rr.getX() + inset, rr.getMaxY() - inset));
        if (isFocused(id)) {
            ProceduralArt.focusRing(g, r, r.getHeight() * 0.5);
        }
    }

    /** Painel pequeno da playlist, sobre o painel central (a música não para ao abrir ou fechar). */
    private void playlist(Surface s, Graphics2D g) {
        for (int slot = 0; slot < PlayerModel.PANEL_ROWS; slot++) {
            Rectangle2D r = Layout.row(slot);
            int idx = player.panelOffset() + slot;
            if (idx >= player.rows().size()) {
                emptySlot(s, g, r, player.rows().isEmpty() && slot == 0 ? "Nenhuma música: use + Música" : "espaço livre");
                continue;
            }
            PlayerModel.Row row = player.rows().get(idx);
            boolean hover = ui != null && ui.isHoverItem(new UiAction(UiAction.Type.PLAYER_PLAY, row.path()));
            s.sliced(row.current() ? Asset.TASK_DONE : Asset.TASK_PENDING, ROW, r);
            if (hover) {
                g.setColor(new Color(255, 255, 255, 70));
                g.fill(new RoundRectangle2D.Double(r.getX() + 3, r.getY() + 3, r.getWidth() - 6, r.getHeight() - 6, 18, 18));
            }
            double h = r.getHeight();
            Rectangle2D icon = new Rectangle2D.Double(r.getX() + h * 0.25, r.getCenterY() - h * 0.30, h * 0.60, h * 0.60);
            if (row.current() && player.playing()) {
                s.sprite(Asset.PLAYER_PLAY, scaled(icon, 1.05));
            } else {
                ProceduralArt.note(g, icon.getCenterX(), icon.getCenterY(), h * 0.55, row.available() ? NOTE_COLOR : Palette.PLACEHOLDER);
            }
            String title = row.available() ? row.title() : row.title() + " (indisponível)";
            double x = r.getX() + h * 1.25;
            double max = playlistUpRect(slot).getX() - 8 - x;
            Color c = row.available() ? Palette.INK : Palette.PLACEHOLDER;
            var tf = TextPainter.fitEllipsis(g, title, fonts.body(25), max, 0.86);
            TextPainter.left(g, tf.text(), tf.font(), x, r.getCenterY(), c);
            arrowButton(g, playlistUpRect(slot), new UiAction(UiAction.Type.PLAYER_UP, row.path()), true);
            arrowButton(g, playlistDownRect(slot), new UiAction(UiAction.Type.PLAYER_DOWN, row.path()), false);
            playlistRemoveButton(g, playlistRemoveRect(slot),
                    new UiAction(UiAction.Type.PLAYER_REMOVE, row.path()));
        }
        addMusicButton(g);
        playlistControls(s, g);
        sfxControls(s, g);
        playlistScrollBar(g);
    }

    /** "+ Música": adiciona arquivos de áudio; fica dentro do painel da playlist. */
    private void addMusicButton(Graphics2D g) {
        Rectangle2D r = Layout.ADD_MUSIC;
        double k = ui != null && ui.isPressed(ButtonId.NEW_MUSIC) ? 0.94 : ui != null && ui.isHover(ButtonId.NEW_MUSIC) ? 1.05 : 1;
        Rectangle2D rr = scaled(r, quantize(k));
        ProceduralArt.thinPill(g, rr, new Color(0xFFE98A), new Color(0xFFD24F), Palette.BORDER);
        TextPainter.centered(g, "+ Música", fonts.display(22), scaled(rr, 0.86),
                Palette.NOVA_TEXT, new Color(0xFFF7DD), 4);
        if (isFocused(UiAction.of(UiAction.Type.PLAYER_ADD))) {
            ProceduralArt.focusRing(g, r, r.getHeight() * 0.5);
        }
    }

    private void playlistControls(Surface s, Graphics2D g) {
        Rectangle2D rep = repeatRect();
        UiAction repeatId = UiAction.of(UiAction.Type.PLAYER_REPEAT);
        double k = ui != null && ui.isPressedItem(repeatId) ? 0.95 : ui != null && ui.isHoverItem(repeatId) ? 1.04 : 1;
        Rectangle2D rr = scaled(rep, quantize(k));
        boolean repeatOn = player.repeat();
        ProceduralArt.thinPill(g, rr,
                repeatOn ? new Color(0xFFA8D0) : new Color(0xF8E8F0),
                repeatOn ? new Color(0xFF5F9D) : new Color(0xE8C5D5),
                repeatOn ? new Color(0xA6205A) : new Color(0x945272));
        var repeatFont = fonts.display(19);
        double textWidth = TextPainter.width(g, "Repetir", repeatFont);
        double iconHeight = 20.7;
        double iconWidth = iconHeight * 34.0 / 38.0;
        double gap = 6.2;
        double groupWidth = iconWidth + gap + textWidth;
        double groupX = rr.getCenterX() - groupWidth / 2;
        Rectangle2D repeatIcon = new Rectangle2D.Double(groupX,
                rr.getCenterY() - iconHeight / 2, iconWidth, iconHeight);
        s.spriteAlpha(Asset.ICON_REPEAT, repeatIcon, repeatOn ? 1f : 0.48f);
        double textX = groupX + iconWidth + gap;
        TextPainter.draw(g, "Repetir", repeatFont, textX,
                TextPainter.centerBaseline(g, repeatFont, rr.getCenterY()),
                repeatOn ? Color.WHITE : new Color(0x8E5B75),
                repeatOn ? new Color(0xB8205F) : null, repeatOn ? 3 : 0);
        if (isFocused(repeatId)) {
            ProceduralArt.focusRing(g, rep, rep.getHeight() * 0.5);
        }
        TextPainter.centered(g, "Vol. " + Math.round(player.volume() * 100) + "%", fonts.secondary(17),
                audioVolumeLabelRect(CONTROL_ROW), Palette.INK_MUTED, null, 0);
        arrowlessButton(g, volumeMinusRect(), new UiAction(UiAction.Type.PLAYER_VOLUME, "-"), "−");
        arrowlessButton(g, volumePlusRect(), new UiAction(UiAction.Type.PLAYER_VOLUME, "+"), "+");
        volumeBar(g, volumeBarRect(), player.volume(), true);
    }

    /** Linha discreta e independente para ativação e volume dos efeitos sonoros. */
    private void sfxControls(Surface s, Graphics2D g) {
        Rectangle2D row = Layout.row(SFX_ROW);
        g.setColor(withAlpha(Palette.BORDER_SOFT, 0.65));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new java.awt.geom.Line2D.Double(row.getX() + 4, row.getY() + 1, row.getMaxX() - 4, row.getY() + 1));

        UiAction toggleId = UiAction.of(UiAction.Type.SFX_TOGGLE);
        Rectangle2D toggle = sfxToggleRect();
        double tk = ui != null && ui.isPressedItem(toggleId) ? 0.94
                : ui != null && ui.isHoverItem(toggleId) ? 1.04 : 1;
        Rectangle2D tr = scaled(toggle, quantize(tk));
        ProceduralArt.thinPill(g, tr,
                player.sfxEnabled() ? new Color(0xFFA8D0) : new Color(0xF8E8F0),
                player.sfxEnabled() ? new Color(0xFF5F9D) : new Color(0xE8C5D5),
                player.sfxEnabled() ? Palette.BORDER : new Color(0x945272));
        sfxToggleLabel(s, g, tr, player.sfxEnabled());
        if (isFocused(toggleId)) {
            ProceduralArt.focusRing(g, toggle, toggle.getHeight() * 0.5);
        }

        Color sfxText = player.sfxEnabled() ? Palette.INK_MUTED : withAlpha(Palette.INK_MUTED, 0.48);
        TextPainter.centered(g, "Vol. " + Math.round(player.sfxVolume() * 100) + "%", fonts.secondary(17),
                audioVolumeLabelRect(SFX_ROW), sfxText, null, 0);
        arrowlessButton(g, sfxVolumeMinusRect(), new UiAction(UiAction.Type.SFX_VOLUME, "-"), "−",
                player.sfxEnabled());
        arrowlessButton(g, sfxVolumePlusRect(), new UiAction(UiAction.Type.SFX_VOLUME, "+"), "+",
                player.sfxEnabled());
        volumeBar(g, sfxVolumeBarRect(), player.sfxVolume(), player.sfxEnabled());
    }

    /** Alto-falante e texto formam um único conjunto, centralizado pela área visível. */
    private void sfxToggleLabel(Surface s, Graphics2D g, Rectangle2D button, boolean enabled) {
        var font = fonts.display(21);
        double textWidth = TextPainter.width(g, "SFX", font);
        double iconWidth = 24.5;
        double iconHeight = iconWidth * 32.0 / 45.0;
        double gap = 7.0;
        double groupX = button.getCenterX() - (iconWidth + gap + textWidth) / 2;
        double textCenterY = button.getCenterY() + 2;         // compensação visual da Cloud Soft dentro da cápsula
        double iconCenterY = textCenterY - 1.0;
        Rectangle2D iconBox = new Rectangle2D.Double(groupX, iconCenterY - iconHeight / 2, iconWidth, iconHeight);
        s.spriteAlpha(Asset.ICON_SFX, iconBox, enabled ? 1f : 0.48f);

        Color fill = enabled ? Color.WHITE : new Color(0x9A6B83);
        Color outline = enabled ? new Color(0xB8205F) : null;
        TextPainter.draw(g, "SFX", font, groupX + iconWidth + gap,
                TextPainter.centerBaseline(g, font, textCenterY), fill, outline, enabled ? 3 : 0);
    }

    private void volumeBar(Graphics2D g, Rectangle2D bar, double value, boolean enabled) {
        g.setColor(withAlpha(Palette.BORDER_SOFT, enabled ? 0.7 : 0.32));
        g.fill(new RoundRectangle2D.Double(bar.getX(), bar.getY(), bar.getWidth(), bar.getHeight(), 14, 14));
        g.setColor(enabled ? new Color(0xFF8DB9) : new Color(0xE7B9CD));
        g.fill(new RoundRectangle2D.Double(bar.getX(), bar.getY(), Math.max(5, bar.getWidth() * value),
                bar.getHeight(), 14, 14));
        double x = bar.getX() + bar.getWidth() * value;
        g.setColor(enabled ? Color.WHITE : new Color(0xF8EFF3));
        g.fill(new java.awt.geom.Ellipse2D.Double(x - 8, bar.getCenterY() - 8, 16, 16));
        g.setColor(enabled ? Palette.BORDER : withAlpha(Palette.BORDER, 0.42));
        g.setStroke(new BasicStroke(1.7f));
        g.draw(new java.awt.geom.Ellipse2D.Double(x - 8, bar.getCenterY() - 8, 16, 16));
    }

    private void arrowlessButton(Graphics2D g, Rectangle2D r, UiAction id, String glyph) {
        arrowlessButton(g, r, id, glyph, true);
    }

    private void arrowlessButton(Graphics2D g, Rectangle2D r, UiAction id, String glyph, boolean enabled) {
        double k = enabled && ui != null && ui.isPressedItem(id) ? 0.86
                : enabled && ui != null && ui.isHoverItem(id) ? 1.12 : 1;
        Rectangle2D rr = scaled(r, quantize(k));
        g.setColor(enabled ? new Color(0xFFE4EF) : new Color(0xF6EAF0));
        g.fill(new java.awt.geom.Ellipse2D.Double(rr.getX(), rr.getY(), rr.getWidth(), rr.getHeight()));
        g.setColor(enabled ? Palette.BORDER : withAlpha(Palette.BORDER, 0.38));
        g.setStroke(new BasicStroke(1.7f));
        g.draw(new java.awt.geom.Ellipse2D.Double(rr.getX(), rr.getY(), rr.getWidth(), rr.getHeight()));
        g.setColor(enabled ? Palette.INK : withAlpha(Palette.INK, 0.38));
        double cx = rr.getCenterX();
        double cy = rr.getCenterY();
        double a = rr.getWidth() * 0.22;
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(cx - a, cy, cx + a, cy));
        if (glyph.equals("+")) {
            g.draw(new java.awt.geom.Line2D.Double(cx, cy - a, cx, cy + a));
        }
        if (enabled && isFocused(id)) {
            ProceduralArt.focusRing(g, r, r.getHeight() * 0.5);
        }
    }

    private void playlistScrollBar(Graphics2D g) {
        int total = player.rows().size();
        if (total <= PlayerModel.PANEL_ROWS) {
            return;
        }
        Rectangle2D top = Layout.row(0);
        Rectangle2D bottom = Layout.row(PlayerModel.PANEL_ROWS - 1);
        double x = top.getMaxX() + 9;
        double y0 = top.getY();
        double h = bottom.getMaxY() - y0;
        g.setColor(withAlpha(Palette.BORDER_SOFT, 0.55));
        g.fill(new RoundRectangle2D.Double(x, y0, 6, h, 6, 6));
        double thumb = Math.max(26, h * PlayerModel.PANEL_ROWS / total);
        double ty = y0 + (h - thumb) * (player.panelOffset() / (double) player.maxOffset());
        g.setColor(Palette.BORDER);
        g.fill(new RoundRectangle2D.Double(x, ty, 6, thumb, 6, 6));
    }

    /** Pulso discreto no botão de tocar/pausar. O nome da faixa aparece somente no rodapé. */
    private void musicEffects(Graphics2D g) {
        if (fx == null) {
            return;
        }
        for (Effect e : fx.active()) {
            double p = EffectLayer.progress(e, fx.now());
            if (e.kind() == EffectLayer.Kind.MUSIC_PLAY || e.kind() == EffectLayer.Kind.MUSIC_PAUSE) {
                Rectangle2D r = scaled(Layout.PLAYER_PAUSE, 1 + 0.9 * EffectLayer.easeOutCubic(p));
                Color base = e.kind() == EffectLayer.Kind.MUSIC_PLAY ? new Color(255, 105, 160) : new Color(155, 120, 220);
                g.setColor(withAlpha(base, 0.85 * (1 - p)));
                g.setStroke(new BasicStroke(3.2f));
                g.draw(new java.awt.geom.Ellipse2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight()));
            }
        }
    }

    // ---------------------------------------------------------------- comemorações

    /** Fator de "pulinho" do ícone de conquistas enquanto uma medalha é comemorada (1 = normal). */
    private double medalBounce() {
        if (fx == null) {
            return 1;
        }
        double k = 1;
        for (Effect e : fx.active()) {
            if (e.kind() == EffectLayer.Kind.MEDAL) {
                double p = EffectLayer.progress(e, fx.now());
                k = Math.max(k, 0.55 + 0.45 * EffectLayer.easeOutBack(Math.min(1, p / 0.3)));
                if (p < 0.3) {
                    k = 0.55 + 0.45 * EffectLayer.easeOutBack(p / 0.3);
                }
            }
        }
        return k;
    }

    private static String medalName(AchievementTier t) {
        return switch (t) {
            case BRONZE -> "Medalha de bronze!";
            case SILVER -> "Medalha de prata!";
            case GOLD -> "Medalha de ouro!";
            default -> "Conquista máxima!";
        };
    }

    private void overlays(Surface s, Graphics2D g, ScreenModel m) {
        if (fx == null || !m.views().achievementsOn()) {
            return;
        }
        Rectangle2D tile = Layout.ACHIEVEMENT_TILE;
        double cx = tile.getCenterX();
        double cy = tile.getCenterY();
        for (Effect e : fx.active()) {
            double p = EffectLayer.progress(e, fx.now());
            if (e.kind() == EffectLayer.Kind.STAR) {
                starCelebration(s, g, cx, cy, p, e.number());
            } else if (e.kind() == EffectLayer.Kind.MEDAL) {
                medalCelebration(s, g, cx, cy, p, e);
            }
        }
    }

    /** Fundo claro atrás do título da comemoração, para o texto ler bem sobre o resumo. */
    private void banner(Graphics2D g, Rectangle2D r, double widthShare, double alpha) {
        double w = r.getWidth() * (0.55 + widthShare * 0.45);
        RoundRectangle2D pill = new RoundRectangle2D.Double(r.getCenterX() - w / 2, r.getY() - 2, w, r.getHeight() + 4,
                r.getHeight() + 4, r.getHeight() + 4);
        g.setColor(withAlpha(new Color(0xFFF7FB), 0.88 * alpha));
        g.fill(pill);
        g.setColor(withAlpha(Palette.BORDER, alpha));
        g.setStroke(new java.awt.BasicStroke(2f));
        g.draw(pill);
    }

    /** Estrela: uma estrela dourada surge com um pulinho, faíscas orbitam e ela se acomoda no ícone. */
    private void starCelebration(Surface s, Graphics2D g, double cx, double cy, double p, int number) {
        double grow = EffectLayer.easeOutBack(Math.min(1, p / 0.4));
        double size = quantize(0.25 + 0.95 * grow) * 62;
        double fade = p < 0.7 ? 1 : 1 - (p - 0.7) / 0.3;
        s.spriteAlpha(Asset.ACH_STAR_ON, new Rectangle2D.Double(cx - size / 2, cy - size / 2 - 10 * p, size, size),
                (float) fade);
        double ease = EffectLayer.easeOutCubic(p);
        for (int k = 0; k < 8; k++) {
            double ang = k * Math.PI / 4 + p * 1.6;
            double dist = 30 + 42 * ease;
            Color col = k % 2 == 0 ? new Color(255, 214, 77) : new Color(255, 170, 205);
            ProceduralArt.sparkle(g, cx + Math.cos(ang) * dist, cy + Math.sin(ang) * dist,
                    Math.max(1.5, 8 * (1 - p * 0.6)), withAlpha(col, fade));
        }
        float blink = (float) Math.sin(Math.PI * p);
        s.spriteAlpha(Asset.ACH_GLINT_LEFT, new Rectangle2D.Double(cx - 80, cy - 52, 28, 28), blink);
        s.spriteAlpha(Asset.ACH_GLINT_RIGHT, new Rectangle2D.Double(cx + 52, cy - 52, 28, 28), blink);
        Rectangle2D label = new Rectangle2D.Double(cx - 110, cy - 96 - 8 * ease, 220, 30);
        banner(g, label, 0.55, fade);
        TextPainter.centered(g, "Estrela " + number + " de 3!", fonts.display(22), label,
                withAlpha(new Color(0xFFF2B0), fade), withAlpha(new Color(0xB8205F), fade), 5);
    }

    /** Medalha / conquista máxima: brilho, raios girando, onda, confete de estrelas e corações e um título. */
    private void medalCelebration(Surface s, Graphics2D g, double cx, double cy, double p, Effect e) {
        boolean max = e.tier() == AchievementTier.MAX;
        double bell = Math.sin(Math.PI * p);
        s.spriteByWidth(Asset.ACH_SPARKLES, cx, cy, 250, (float) Math.min(1, bell * 1.1));
        ProceduralArt.rays(g, cx, cy, 20, max ? 170 : 140, max ? 18 : 12, p * 1.3,
                new Color(255, 214, 77, (int) (110 * bell)));
        double wave = Math.min(1, p / 0.55);
        if (wave < 1) {
            g.setColor(new Color(255, 143, 184, (int) (220 * (1 - wave))));
            g.setStroke(new java.awt.BasicStroke((float) (7 * (1 - wave) + 1)));
            double r = 24 + 130 * EffectLayer.easeOutCubic(wave);
            g.draw(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        }
        if (max) {
            double grow = EffectLayer.easeOutBack(Math.min(1, p / 0.25));
            float a = (float) (p < 0.75 ? 1 : 1 - (p - 0.75) / 0.25);
            s.spriteByWidth(Asset.ACH_MAX, cx, cy - 10, quantize(grow) * 170, a);
        }
        // confete determinístico: mesma comemoração sempre, sem aleatoriedade
        int n = max ? 44 : 26;
        double secs = p * e.duration() / 1000.0;
        Asset[] kinds = {Asset.TINY_STAR, Asset.TINY_HEART, Asset.HEART_PINK_DETAIL};
        long seed = 12345;
        for (int i = 0; i < n; i++) {
            seed = (seed * 1103515245L + 12345L) & 0x7fffffffL;
            double ang = (seed % 3600) / 3600.0 * 2 * Math.PI;
            seed = (seed * 1103515245L + 12345L) & 0x7fffffffL;
            double speed = 120 + (seed % 200);
            double x = cx + Math.cos(ang) * speed * secs;
            double y = cy + Math.sin(ang) * speed * secs - 90 * secs + 240 * secs * secs;
            float a = (float) (p < 0.7 ? 1 : 1 - (p - 0.7) / 0.3);
            s.spriteAlpha(kinds[i % 3], new Rectangle2D.Double(x - 11, y - 11, 22, 22), a);
        }
        double rise = EffectLayer.easeOutCubic(Math.min(1, p / 0.3));
        float ta = (float) (p < 0.75 ? 1 : 1 - (p - 0.75) / 0.25);
        Rectangle2D title = new Rectangle2D.Double(cx - 160, cy - 150 - 14 * rise, 320, 40);
        banner(g, title, 0.62, ta);
        TextPainter.centered(g, medalName(e.tier()), fonts.display(max ? 34 : 30), title,
                withAlpha(new Color(0xFFF2B0), ta), withAlpha(new Color(0xB8205F), ta), 7);
    }

    /** Menor retângulo com a proporção do sprite que cabe dentro de {@code box}, centralizado. */
    private static Rectangle2D fitInside(Surface s, Asset a, Rectangle2D box) {
        var o = s.spriteSize(a);
        double k = Math.min(box.getWidth() / o.getWidth(), box.getHeight() / o.getHeight());
        double w = o.getWidth() * k;
        double h = o.getHeight() * k;
        return new Rectangle2D.Double(box.getCenterX() - w / 2, box.getCenterY() - h / 2, w, h);
    }
}
