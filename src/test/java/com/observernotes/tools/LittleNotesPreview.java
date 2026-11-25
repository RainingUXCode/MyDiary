package com.observernotes.tools;

import com.observernotes.ui.Layout;
import com.observernotes.ui.NotebookCanvas;
import com.observernotes.ui.assets.AssetCatalog;
import com.observernotes.ui.model.LittleNotesModel;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.ScreenModel;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/** Gera somente as prévias localizadas pedidas para o mural. */
public final class LittleNotesPreview {

    private LittleNotesPreview() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Path out = Path.of(args.length == 0 ? "target/little-notes-preview" : args[0]);
        Files.createDirectories(out);
        ScreenModel base = MockupSample.screen(PanelMode.TASKS);
        saveSummary(out.resolve("resumo-0-bilhetes.png"), withNotes(base, LittleNotesModel.empty()));
        saveSummary(out.resolve("resumo-1-bilhete.png"), withNotes(base, new LittleNotesModel(List.of(
                new LittleNotesModel.Item("a", "Beber água")), "")));
        ScreenModel editing = withNotes(base, new LittleNotesModel(List.of(
                new LittleNotesModel.Item("a", "Beber água")), "a"));
        saveSummary(out.resolve("resumo-1-bilhete-editando.png"), editing, "Beber água");
        ImageIO.write(render(editing, "Beber água"), "png", out.resolve("tela-1-bilhete-editando.png").toFile());
        saveSummary(out.resolve("resumo-3-bilhetes.png"), withNotes(base, new LittleNotesModel(List.of(
                new LittleNotesModel.Item("a", "Beber água"),
                new LittleNotesModel.Item("b", "Ligar pra vovó"),
                new LittleNotesModel.Item("c", "Você consegue!")), "")));
        saveObservers(out.resolve("observadores-3-cartoes.png"),
                withNotes(MockupSample.screen(PanelMode.OBSERVERS), new LittleNotesModel(List.of(
                        new LittleNotesModel.Item("a", "Beber água"),
                        new LittleNotesModel.Item("b", "Ligar pra vovó"),
                        new LittleNotesModel.Item("c", "Você consegue!")), "")));
    }

    private static ScreenModel withNotes(ScreenModel base, LittleNotesModel notes) {
        return new ScreenModel(base.mode(), base.tasks(), base.observers(), base.history(), notes,
                base.achievementLabel(), base.achievementState(), base.achievementTier(), base.footerMessage(),
                base.newAchievement(), base.views(), base.feedback(), base.historyOffset(), base.taskPages());
    }

    private static BufferedImage render(ScreenModel model) {
        return render(model, null);
    }

    private static BufferedImage render(ScreenModel model, String editingText) {
        NotebookCanvas canvas = new NotebookCanvas(new AssetCatalog());
        canvas.setSize(Layout.WIDTH, Layout.HEIGHT);
        canvas.setModel(model);
        if (editingText != null) {
            canvas.noteInput().setText(editingText);
            canvas.noteInput().setFocused(true);
        }
        BufferedImage image = new BufferedImage(Layout.WIDTH, Layout.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        canvas.paintScene(g, image.getWidth(), image.getHeight());
        g.dispose();
        return image;
    }

    private static void saveSummary(Path path, ScreenModel model) throws Exception {
        saveSummary(path, model, null);
    }

    private static void saveSummary(Path path, ScreenModel model, String editingText) throws Exception {
        BufferedImage full = render(model, editingText);
        int x = (int) Layout.SUMMARY_PANEL.getX() - 8;
        int y = (int) Layout.SUMMARY_PANEL.getY() - 28;
        int w = (int) Layout.SUMMARY_PANEL.getWidth() + 16;
        int h = (int) Layout.SUMMARY_PANEL.getHeight() + 36;
        ImageIO.write(full.getSubimage(x, y, w, h), "png", path.toFile());
    }

    private static void saveObservers(Path path, ScreenModel model) throws Exception {
        BufferedImage full = render(model);
        int x = (int) Layout.MAIN_PANEL.getX() - 8;
        int y = (int) Layout.MAIN_PANEL.getY() - 28;
        int w = (int) Layout.MAIN_PANEL.getWidth() + 16;
        int h = (int) Layout.MAIN_PANEL.getHeight() + 36;
        ImageIO.write(full.getSubimage(x, y, w, h), "png", path.toFile());
    }
}
