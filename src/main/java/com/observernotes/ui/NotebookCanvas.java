package com.observernotes.ui;

import com.observernotes.ui.anim.EffectLayer;
import com.observernotes.ui.assets.Asset;
import com.observernotes.ui.assets.AssetCatalog;
import com.observernotes.ui.assets.SpriteCache;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.render.Surface;
import com.observernotes.ui.scene.MainScene;
import com.observernotes.ui.widget.ButtonId;
import com.observernotes.ui.widget.FocusRing;
import com.observernotes.ui.widget.InteractionState;
import com.observernotes.ui.widget.TextInput;
import com.observernotes.ui.widget.UiAction;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Superfície principal do app. Trabalha em um espaço lógico fixo
 * ({@link Layout#WIDTH} x {@link Layout#HEIGHT}) que é escalado, com a
 * proporção preservada, para o tamanho real da janela. Assim a composição
 * do mockup nunca se deforma.
 *
 * <p>Na Etapa 4A o canvas desenha a cena, mostra hover/pressionado nos botões, e o campo de
 * texto aceita digitação (foco, cursor, colar). Nenhuma ação de negócio é executada: quem
 * quiser reagir a cliques registra um {@link #setButtonListener(Consumer)} (a 4B fará isso).
 */
public final class NotebookCanvas extends JPanel {

    private final SpriteCache sprites;
    private final Fonts fonts;
    private final MainScene scene;
    private final TextInput input = new TextInput();
    private final InteractionState interaction = new InteractionState();
    private final Timer caretTimer;
    private final Timer frameTimer;
    private final FooterRotation footer = new FooterRotation();
    private final java.awt.image.BufferedImage metricsImage = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    private com.observernotes.ui.model.PlayerModel player = com.observernotes.ui.model.PlayerModel.NONE;
    private final EffectLayer effects;
    private final FocusRing focus;

    private ScreenModel model = ScreenModel.empty();
    private boolean caretVisible = true;
    private UiAction pressedItem;
    private String hoveredTaskId;
    private Consumer<UiAction> actionListener = a -> { };

    public NotebookCanvas(AssetCatalog assets) {
        this(assets, () -> System.nanoTime() / 1_000_000L);
    }

    /** @param clockMillis relógio das animações e confirmações (injetável para testes e pré-visualizações) */
    public NotebookCanvas(AssetCatalog assets, java.util.function.LongSupplier clockMillis) {
        this.effects = new EffectLayer(clockMillis);
        this.focus = new FocusRing(clockMillis);
        this.sprites = new SpriteCache(assets);
        this.fonts = Fonts.load();
        this.scene = new MainScene(fonts);
        setOpaque(true);
        setBackground(Palette.DESK);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);       // Tab é nosso: percorre os controles do app

        // Quadros de animação só rodam enquanto há efeito (≈60 por segundo); parados, custo zero.
        frameTimer = new Timer(16, e -> {
            if (effects.isActive() || player.playing() || footer.animating(effects.now()) || taskMarqueeTarget() != null) {
                ((Timer) e.getSource()).setDelay(effects.isActive() || footer.animating(effects.now())
                        || taskMarqueeTarget() != null ? 16 : 60);   // marquee usa quadros suaves; só a notinha pode economizar
                repaint();
            } else {
                ((Timer) e.getSource()).stop();
                repaint();
            }
        });
        effects.setOnActivate(() -> {
            if (!frameTimer.isRunning()) {
                frameTimer.start();
            }
        });

        caretTimer = new Timer(530, e -> {
            if (input.focused()) {
                caretVisible = !caretVisible;
                repaint();
            }
        });
        caretTimer.setRepeats(true);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                hover(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                hover(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Point2D p = toLogical(e.getX(), e.getY());
                updateHoveredTask(p);
                boolean field = MainScene.overTextField(p);
                focus.set(field ? FocusRing.FIELD : null);        // clique esconde o foco de teclado
                boolean changed = setInputFocus(field);
                ButtonId id = MainScene.hitTest(p, model, player);
                changed |= interaction.setHover(id);
                if (id != null) {
                    changed |= interaction.press(id);
                    pressedItem = null;
                } else {
                    pressedItem = MainScene.actionAt(p, model, player);
                    if (pressedItem != null && pressedItem.isItem()) {
                        interaction.pressItem(pressedItem);
                    }
                }
                changed = true;
                if (changed) {
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Point2D p = toLogical(e.getX(), e.getY());
                ButtonId over = MainScene.hitTest(p, model, player);
                ButtonId was = interaction.release();
                interaction.releaseItem();
                if (was != null && was == over) {
                    actionListener.accept(UiAction.of(was));
                } else if (was == null && e.getButton() == MouseEvent.BUTTON1) {
                    // itens (checkbox, X, interruptor) agem ao soltar sobre o mesmo item pressionado
                    UiAction item = MainScene.actionAt(p, model, player);
                    if (item != null && item.equals(pressedItem)) {
                        actionListener.accept(item);
                    }
                }
                pressedItem = null;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoveredTaskId = null;
                if (interaction.setHover(null) | interaction.setHoverItem(null)) {
                    repaint();
                }
            }

            private void hover(MouseEvent e) {
                Point2D p = toLogical(e.getX(), e.getY());
                updateHoveredTask(p);
                ButtonId id = MainScene.hitTest(p, model, player);
                setCursor(Cursor.getPredefinedCursor(MainScene.overClickable(p, model, player) ? Cursor.HAND_CURSOR
                        : MainScene.overTextField(p) ? Cursor.TEXT_CURSOR : Cursor.DEFAULT_CURSOR));
                UiAction a = MainScene.actionAt(p, model, player);
                setToolTipText(truncatedTitleTooltip(p));
                boolean changed = interaction.setHover(id);
                changed |= interaction.setHoverItem(a != null && a.isItem() ? a : null);
                if (changed) {
                    repaint();
                }
            }
        };
        addMouseWheelListener(e -> {
            Point2D wp = toLogical(e.getX(), e.getY());
            if (player.available() && Layout.PLAYER_PILL.contains(wp)) {
                actionListener.accept(new UiAction(UiAction.Type.PLAYER_VOLUME, e.getWheelRotation() < 0 ? "+" : "-"));
                return;
            }
            if (player.available() && player.panelOpen() && Layout.MAIN_PANEL.contains(wp)) {
                int rows = (int) Math.signum(e.getWheelRotation());
                if (rows != 0) {
                    actionListener.accept(new UiAction(UiAction.Type.PLAYER_SCROLL, Integer.toString(rows)));
                }
                return;
            }
            if (model.mode() == com.observernotes.ui.model.PanelMode.HISTORY
                    && Layout.MAIN_PANEL.contains(toLogical(e.getX(), e.getY()))) {
                int rows = e.getUnitsToScroll() != 0 ? e.getUnitsToScroll() / 3 : (int) Math.signum(e.getWheelRotation());
                if (rows == 0) {
                    rows = (int) Math.signum(e.getWheelRotation());
                }
                actionListener.accept(new UiAction(UiAction.Type.SCROLL_HISTORY, Integer.toString(rows)));
            }
        });
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (input.focused() && !e.isControlDown() && !e.isMetaDown() && input.type(e.getKeyChar())) {
                    edited();
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
                handleKey(e.getKeyCode(), e.isShiftDown(), e.isControlDown() || e.isMetaDown());
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                sprites.clear();     // tamanhos antigos não serão mais usados
            }
        });
    }

    /**
     * Teclado. Tab / Shift+Tab percorrem os controles; no campo de texto as teclas editam e Enter
     * adiciona; fora dele, Espaço/Enter acionam o item focado (as regras de segurança estão em
     * {@link FocusRing}) e as setas rolam o histórico.
     */
    void handleKey(int code, boolean shift, boolean command) {
        if (code == KeyEvent.VK_TAB) {
            focus.move(model, !shift);
            setInputFocus(focus.isField());
            repaint();
            return;
        }
        if (focus.isField()) {
            boolean changed = switch (code) {
                case KeyEvent.VK_BACK_SPACE -> input.backspace();
                case KeyEvent.VK_DELETE -> input.delete();
                case KeyEvent.VK_LEFT -> input.left();
                case KeyEvent.VK_RIGHT -> input.right();
                case KeyEvent.VK_HOME -> input.home();
                case KeyEvent.VK_END -> input.end();
                case KeyEvent.VK_ESCAPE -> {
                    focus.clear();
                    setInputFocus(false);
                    yield true;
                }
                case KeyEvent.VK_ENTER -> {
                    actionListener.accept(UiAction.of(UiAction.Type.SUBMIT));
                    yield true;
                }
                case KeyEvent.VK_V -> command && pasteFromClipboard();
                default -> false;
            };
            if (changed) {
                edited();
            }
            return;
        }
        switch (code) {
            case KeyEvent.VK_SPACE, KeyEvent.VK_ENTER -> {
                var result = focus.activate(model, code == KeyEvent.VK_SPACE,
                        hint -> actionListener.accept(new UiAction(UiAction.Type.HINT, hint)));
                if (!result.isNone()) {
                    actionListener.accept(result.action());
                }
            }
            case KeyEvent.VK_ESCAPE -> {
                if (focus.current() == null && player.available() && player.panelOpen()) {
                    actionListener.accept(UiAction.of(UiAction.Type.PLAYER_PANEL));      // Esc fecha a playlist
                }
                focus.clear();
            }
            case KeyEvent.VK_UP -> scrollHistory("-1");
            case KeyEvent.VK_DOWN -> scrollHistory("1");
            case KeyEvent.VK_PAGE_UP -> scrollHistory("-" + ScreenModel.HISTORY_ROWS);
            case KeyEvent.VK_PAGE_DOWN -> scrollHistory("" + ScreenModel.HISTORY_ROWS);
            case KeyEvent.VK_HOME -> scrollHistory("home");
            case KeyEvent.VK_END -> scrollHistory("end");
            default -> focus.cancelPending();
        }
        repaint();
    }

    private void scrollHistory(String delta) {
        focus.cancelPending();
        if (player.available() && player.panelOpen()) {            // com a playlist aberta, as setas rolam a playlist
            actionListener.accept(new UiAction(UiAction.Type.PLAYER_SCROLL, delta));
            return;
        }
        if (model.mode() == com.observernotes.ui.model.PanelMode.HISTORY) {
            actionListener.accept(new UiAction(UiAction.Type.SCROLL_HISTORY, delta));
        }
    }

    /** Efeitos visuais em andamento; o controlador recebe esta camada como {@code EffectSink}. */
    public EffectLayer effects() {
        return effects;
    }

    public FocusRing focusRing() {
        return focus;
    }

    /** Tela que será desenhada (na 4B, montada a partir dos observadores). */
    public void setModel(ScreenModel model) {
        this.model = model;
        focus.retain(model);
        repaint();
    }

    public ScreenModel model() {
        return model;
    }

    /** Tooltip com o título completo quando o da tarefa aparece cortado com "…"; senão nenhum. */
    private String truncatedTitleTooltip(Point2D logical) {
        Graphics2D g = metricsImage.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            String full = MainScene.truncatedTaskTitleAt(g, fonts, logical, model);
            if (full == null) {
                return null;
            }
            String esc = full.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            return "<html><body style='width:280px'>" + esc + "</body></html>";
        } finally {
            g.dispose();
        }
    }

    private void updateHoveredTask(Point2D logical) {
        String next = MainScene.taskIdAt(logical, model);
        if (!java.util.Objects.equals(hoveredTaskId, next)) {
            hoveredTaskId = next;
            if (next != null && !frameTimer.isRunning()) {
                frameTimer.start();
            }
        }
    }

    /** Hover tem prioridade; o foco em checkbox ou excluir ativa a mesma linha pelo teclado. */
    private String taskMarqueeTarget() {
        boolean tasksPanel = model.mode() == com.observernotes.ui.model.PanelMode.TASKS;
        boolean hoveredStillExists = tasksPanel && hoveredTaskId != null
                && model.tasks().stream().anyMatch(t -> t.id().equals(hoveredTaskId));
        if (hoveredStillExists) {
            return hoveredTaskId;
        }
        UiAction current = focus.current();
        if (current != null && (current.type() == UiAction.Type.TOGGLE_TASK
                || current.type() == UiAction.Type.DELETE_TASK)) {
            return current.key();
        }
        return null;
    }

    /** Estado do player (rodapé e painel da playlist). {@code NONE} = sem player. */
    public void setPlayer(com.observernotes.ui.model.PlayerModel pm) {
        this.player = pm == null ? com.observernotes.ui.model.PlayerModel.NONE : pm;
        focus.setPlayer(this.player);
        focus.retain(model);
        if (this.player.playing() && !frameTimer.isRunning()) {
            frameTimer.start();
        }
        repaint();
    }

    public com.observernotes.ui.model.PlayerModel player() {
        return player;
    }

    public TextInput input() {
        return input;
    }

    /**
     * Recebe as ações do usuário (clique completo em botão ou item, e Enter no campo).
     * Sem ouvinte, nada acontece.
     */
    public void setActionListener(Consumer<UiAction> listener) {
        this.actionListener = listener == null ? a -> { } : listener;
    }

    /** Fator de escala do espaço lógico para a tela. */
    double scale() {
        return Math.min(getWidth() / (double) Layout.WIDTH, getHeight() / (double) Layout.HEIGHT);
    }

    /** Converte pixels da tela em coordenadas lógicas (descontando o letterbox). */
    Point2D toLogical(int x, int y) {
        double s = scale();
        double offX = (getWidth() - Layout.WIDTH * s) / 2.0;
        double offY = (getHeight() - Layout.HEIGHT * s) / 2.0;
        return new Point2D.Double((x - offX) / s, (y - offY) / s);
    }

    private boolean setInputFocus(boolean focus) {
        if (input.focused() == focus) {
            return false;
        }
        input.setFocused(focus);
        caretVisible = true;
        if (focus) {
            caretTimer.start();
        } else {
            caretTimer.stop();
        }
        return true;
    }

    private void edited() {
        caretVisible = true;
        caretTimer.restart();
        repaint();
    }

    private boolean pasteFromClipboard() {
        try {
            var clip = getToolkit().getSystemClipboard();
            if (clip.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return input.paste((String) clip.getData(DataFlavor.stringFlavor));
            }
        } catch (Exception ignored) {
            // área de transferência indisponível: simplesmente não cola
        }
        return false;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            paintScene(g, getWidth(), getHeight());
        } finally {
            g.dispose();
        }
    }

    /** Desenha tudo em {@code width x height}; usado também pelo renderizador de pré-visualização. */
    public void paintScene(Graphics2D g, int width, int height) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double s = Math.min(width / (double) Layout.WIDTH, height / (double) Layout.HEIGHT);
        int w = (int) Math.round(Layout.WIDTH * s);
        int h = (int) Math.round(Layout.HEIGHT * s);
        int offX = (width - w) / 2;
        int offY = (height - h) / 2;

        g.setColor(Palette.DESK);
        g.fillRect(0, 0, width, height);
        // A moldura é pré-escalada para o tamanho exato em pixels: nítida e rápida.
        g.drawImage(sprites.catalog().scaled(Asset.NOTEBOOK_FRAME, w, h), offX, offY, null);
        long footerHold = scene.footerMinimumVisibleMillis(g, footer.shownLine());
        scene.setFooterView(footer.update(effects.now(), model, player, footerHold));
        String marqueeTask = taskMarqueeTarget();
        scene.setTaskMarqueeTarget(marqueeTask, effects.now());
        if (marqueeTask != null && !frameTimer.isRunning()) {
            frameTimer.start();
        }
        if ((footer.animating(effects.now()) || player.playing()) && !frameTimer.isRunning()) {
            frameTimer.start();
        }
        scene.paint(new Surface(g, s, offX, offY, sprites), model, input, interaction, caretVisible, effects,
                focus.current(), player);
    }

    /** Cor de fundo da mesa, exposta para ferramentas. */
    static Color desk() {
        return Palette.DESK;
    }
}
