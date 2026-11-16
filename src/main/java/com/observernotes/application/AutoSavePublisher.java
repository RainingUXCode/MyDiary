package com.observernotes.application;

import com.observernotes.domain.event.DomainEvent;
import com.observernotes.domain.event.MilestoneUnlocked;
import com.observernotes.observer.EventPublisher;
import com.observernotes.persistence.AppSnapshot;
import com.observernotes.persistence.PersistenceException;
import com.observernotes.persistence.StateRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Decorator de {@link EventPublisher}: entrega o evento a quem estiver inscrito e,
 * <em>depois que todos os observadores terminaram</em>, grava o estado. Assim o histórico
 * gravado já inclui o evento atual, não importa a ordem de inscrição dos observadores.
 *
 * <p>{@link MilestoneUnlocked} não dispara gravação própria: o ledger já foi atualizado
 * antes de qualquer evento, então a gravação do {@code TaskCompleted} que o precede já
 * contém o ponto.
 *
 * <p>Falha ao gravar nunca chega ao domínio: o estado em memória continua válido, o erro é
 * informado e a próxima alteração tenta gravar de novo (o arquivo anterior fica intacto).
 */
public final class AutoSavePublisher implements EventPublisher<DomainEvent> {

    private final EventPublisher<DomainEvent> delegate;
    private final Supplier<AppSnapshot> source;
    private final StateRepository repository;
    private final Consumer<PersistenceException> onFailure;
    private volatile PersistenceException lastError;

    public AutoSavePublisher(EventPublisher<DomainEvent> delegate, Supplier<AppSnapshot> source,
                             StateRepository repository, Consumer<PersistenceException> onFailure) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.source = Objects.requireNonNull(source, "source");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
    }

    @Override
    public void publish(DomainEvent event) {
        delegate.publish(event);
        if (!(event instanceof MilestoneUnlocked)) {
            saveNow();
        }
    }

    /** Grava agora (também usado quando algo muda sem evento, como limpar o histórico). */
    public boolean saveNow() {
        try {
            repository.save(source.get());
            lastError = null;
            return true;
        } catch (PersistenceException e) {
            return fail(e);
        } catch (RuntimeException e) {
            return fail(new PersistenceException("Falha inesperada ao salvar", e));
        }
    }

    /** Último erro de gravação ainda não resolvido por uma gravação bem-sucedida. */
    public Optional<PersistenceException> lastError() {
        return Optional.ofNullable(lastError);
    }

    private boolean fail(PersistenceException e) {
        lastError = e;
        try {
            onFailure.accept(e);
        } catch (RuntimeException ignored) {
            // quem recebe o aviso não pode afetar o domínio
        }
        return false;
    }
}
