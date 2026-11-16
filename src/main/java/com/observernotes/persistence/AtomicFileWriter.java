package com.observernotes.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Escreve em um arquivo temporário ao lado do destino, força a gravação no disco e só
 * então substitui o destino (movimento atômico quando o sistema suporta). Se algo falhar
 * antes da troca, o arquivo original permanece exatamente como estava.
 */
public final class AtomicFileWriter implements AtomicWriter {

    @Override
    public void write(Path target, byte[] content) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = dir.resolve(target.getFileName() + ".tmp");
        try {
            try (FileChannel ch = FileChannel.open(tmp,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer buf = ByteBuffer.wrap(content);
                while (buf.hasRemaining()) {
                    ch.write(buf);
                }
                ch.force(true);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // melhor esforço: o temporário é descartável
            }
            throw e;
        }
    }
}
