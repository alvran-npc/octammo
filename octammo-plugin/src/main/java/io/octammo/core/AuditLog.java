package io.octammo.core;

import io.octammo.OctaMMOPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;

/** logs/<name> written on the I/O thread only, rotated at {@code maxBytes} (keeps .1 to .3). */
public final class AuditLog {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OctaMMOPlugin plugin;
    private final ExecutorService io;
    private final Path path;
    private final long maxBytes;

    public AuditLog(OctaMMOPlugin plugin, ExecutorService io, String fileName, long maxBytes) {
        this.plugin = plugin;
        this.io = io;
        this.path = plugin.getDataFolder().toPath().resolve("logs").resolve(fileName);
        this.maxBytes = maxBytes;
    }

    public void log(String line) {
        String text = TS.format(LocalDateTime.now()) + " | " + line + System.lineSeparator();
        try {
            io.execute(() -> write(text));
        } catch (RejectedExecutionException ignored) {
            // shutting down
        }
    }

    private void write(String text) {
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path) && Files.size(path) > maxBytes) rotate();
            Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write " + path.getFileName(), e);
        }
    }

    private void rotate() throws IOException {
        for (int i = 3; i >= 1; i--) {
            Path from = i == 1 ? path : path.resolveSibling(path.getFileName() + "." + (i - 1));
            if (Files.exists(from)) Files.move(from, path.resolveSibling(path.getFileName() + "." + i), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
