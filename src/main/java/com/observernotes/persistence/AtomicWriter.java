package com.observernotes.persistence;

import java.io.IOException;
import java.nio.file.Path;

/** Grava o conteúdo completo de um arquivo de forma segura. Existe para poder simular falhas. */
@FunctionalInterface
public interface AtomicWriter {

    void write(Path target, byte[] content) throws IOException;
}
