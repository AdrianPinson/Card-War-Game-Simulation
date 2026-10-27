package war;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Write a complete export before replacing its destination. */
final class FilesOut {
    @FunctionalInterface interface Write { void accept(BufferedWriter writer) throws IOException; }
    private FilesOut() {}
    static void write(Path target, Write write) throws IOException {
        Path absolute = target.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".war-export-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { write.accept(writer); }
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
