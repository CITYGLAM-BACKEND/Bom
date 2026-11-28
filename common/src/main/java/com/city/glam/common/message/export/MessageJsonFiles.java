package com.city.glam.common.message.export;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * Writes one language catalog as a JSON file, safely:
 * <ol>
 *   <li>the JSON (UTF-8, sorted keys, 2-space indent) is written to a temporary file in the SAME folder,</li>
 *   <li>the temporary file is read back and must equal the catalog (else it is deleted and the old file stays),</li>
 *   <li>then it replaces the file in one atomic rename (a plain replace where the file system cannot do that).</li>
 * </ol>
 * A file whose content would not change is not touched.
 */
public final class MessageJsonFiles {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Map<String, String>>> CATALOG = new TypeReference<>() {
    };

    private MessageJsonFiles() {
    }

    /** Outcome for one file. */
    public enum Outcome { WRITTEN, UNCHANGED }

    /** The catalog as pretty JSON text (keys in the catalog's order: sorted when it comes from {@link MessageCatalog}). */
    public static String toJson(Map<String, Map<String, String>> catalog) {
        StringBuilder out = new StringBuilder("{");
        boolean firstModule = true;
        for (Map.Entry<String, Map<String, String>> module : catalog.entrySet()) {
            out.append(firstModule ? "\n" : ",\n").append("  ").append(quote(module.getKey())).append(": {");
            firstModule = false;
            boolean firstCode = true;
            for (Map.Entry<String, String> code : module.getValue().entrySet()) {
                out.append(firstCode ? "\n" : ",\n").append("    ").append(quote(code.getKey())).append(": ").append(quote(code.getValue()));
                firstCode = false;
            }
            out.append(firstCode ? "}" : "\n  }");
        }
        return out.append(firstModule ? "}\n" : "\n}\n").toString();
    }

    /** Writes {@code directory/fileName} as described above. */
    public static Outcome publish(Path directory, String fileName, Map<String, Map<String, String>> catalog) {
        byte[] bytes = toJson(catalog).getBytes(StandardCharsets.UTF_8);
        Path target = directory.resolve(fileName);
        Path temp = directory.resolve("." + fileName + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(directory);
            if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), bytes)) {
                return Outcome.UNCHANGED;
            }
            Files.write(temp, bytes);
            Map<String, Map<String, String>> readBack = JSON.readValue(Files.readString(temp, StandardCharsets.UTF_8), CATALOG);
            if (!readBack.equals(catalog)) {
                throw new IllegalStateException(fileName + ": the written JSON does not match the messages");
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return Outcome.WRITTEN;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + target, e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // best effort: a left-over temporary file is harmless (starts with a dot, never served as msg_*.json)
            }
        }
    }

    /** JSON string with the required escapes; every other character (Unicode, Arabic, accents) is written as is. */
    static String quote(String value) {
        StringBuilder s = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> s.append("\\\"");
                case '\\' -> s.append("\\\\");
                case '\n' -> s.append("\\n");
                case '\r' -> s.append("\\r");
                case '\t' -> s.append("\\t");
                case '\b' -> s.append("\\b");
                case '\f' -> s.append("\\f");
                default -> {
                    if (c < 0x20) {
                        s.append(String.format("\\u%04x", (int) c));
                    } else {
                        s.append(c);
                    }
                }
            }
        }
        return s.append('"').toString();
    }
}
