package com.observernotes.tools;

import com.observernotes.ui.model.HistoryRowModel;
import com.observernotes.ui.model.LittleNotesModel;
import com.observernotes.ui.model.PanelMode;
import com.observernotes.ui.model.ScreenModel;
import com.observernotes.ui.model.TaskRowModel;
import java.util.List;

/** Dados de exemplo iguais aos do mockup. Só para testes e pré-visualização; nunca vão para o app. */
public final class MockupSample {

    private MockupSample() { }

    public static ScreenModel screen(PanelMode mode) {
        return new ScreenModel(mode,
                List.of(new TaskRowModel("Estudar o Observer", true),
                        new TaskRowModel("Desenhar o diagrama", true),
                        new TaskRowModel("Escrever o README", false),
                        new TaskRowModel("Ensaiar a apresentação", false)),
                ScreenModel.defaultObservers(),
                List.of(new HistoryRowModel("21:40", "Concluída: Desenhar o diagrama"),
                        new HistoryRowModel("21:32", "Concluída: Estudar o Observer"),
                        new HistoryRowModel("21:20", "Criada: Ensaiar a apresentação"),
                        new HistoryRowModel("21:15", "Criada: Escrever o README")),
                LittleNotesModel.empty(), "Conquistas", "nenhuma ainda",
                com.observernotes.ui.model.AchievementTier.NONE,
                "21:40 · ✓ Concluída: Desenhar o diagrama", true);
    }
}
