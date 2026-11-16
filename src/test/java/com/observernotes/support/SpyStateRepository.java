package com.observernotes.support;

import com.observernotes.persistence.AppSnapshot;
import com.observernotes.persistence.LoadResult;
import com.observernotes.persistence.PersistenceException;
import com.observernotes.persistence.StateRepository;

/** Decora um repositório: conta gravações e pode ser forçado a falhar. */
public final class SpyStateRepository implements StateRepository {

    private final StateRepository delegate;
    public volatile boolean failing;
    public int saves;

    public SpyStateRepository(StateRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public LoadResult<AppSnapshot> load() {
        return delegate.load();
    }

    @Override
    public void save(AppSnapshot snapshot) {
        if (failing) {
            throw new PersistenceException("disco cheio (simulado)");
        }
        saves++;
        delegate.save(snapshot);
    }
}
