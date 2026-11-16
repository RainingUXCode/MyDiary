package com.observernotes.support;

import com.observernotes.persistence.AppSnapshot;

/** Cria {@link AppSnapshot} a partir de um {@link Fixture} em memória. */
public final class Snapshots {

    private Snapshots() { }

    public static AppSnapshot of(Fixture f) {
        return new AppSnapshot(f.service.domainState(), f.history.entries());
    }

    /** Estado com três tarefas (uma concluída), uma excluída e histórico. */
    public static AppSnapshot sample() {
        Fixture f = new Fixture();
        var a = f.create("Estudar o Observer");
        var b = f.create("Desenhar o diagrama");
        f.create("Escrever o README");
        var gone = f.create("Rascunho");
        f.service.complete(a.id());
        f.service.complete(b.id());
        f.service.complete(gone.id());
        f.service.delete(gone.id());
        f.service.uncomplete(b.id());
        return of(f);
    }
}
