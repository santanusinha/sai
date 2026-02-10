package io.appform.sai.session.internal;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileOps {
    public static boolean atomicWriteJson(Path target, Object data, ObjectMapper mapper) {
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try (OutputStream os = Files.newOutputStream(tmp)) {
            mapper.writeValue(os, data);
        } catch (IOException e) {
            return false;
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            // Fallback if atomic move not supported
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException ex) {
                return false;
            }
        }
    }

    public static String readLineAtOffset(Path file, long offset) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(offset);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int b;
            while ((b = raf.read()) != -1) {
                if (b == 10) break; // newline character\n
                baos.write(b);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
