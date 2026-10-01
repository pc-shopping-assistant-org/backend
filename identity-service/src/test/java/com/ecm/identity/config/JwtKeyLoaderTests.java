package com.ecm.identity.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtKeyLoaderTests {

    @Test
    void generatesAndReloadsTheSameDevelopmentKeyPair(@org.junit.jupiter.api.io.TempDir Path tempDirectory) throws Exception {
        JwtKeyLoader loader = new JwtKeyLoader(tempDirectory.resolve("keys").toString());
        JwtProperties properties = new JwtProperties();

        var first = loader.load(properties);
        var second = loader.load(properties);

        assertEquals(first.getPrivate(), second.getPrivate());
        assertEquals(first.getPublic(), second.getPublic());
    }

    @Test
    void refusesToReplaceAnIncompleteDevelopmentKeyPair(@org.junit.jupiter.api.io.TempDir Path tempDirectory) throws Exception {
        Path directory = tempDirectory.resolve("keys");
        Files.createDirectories(directory);
        Files.write(directory.resolve("jwt-private.pk8"), new byte[]{1, 2, 3});
        JwtKeyLoader loader = new JwtKeyLoader(directory.toString());

        assertThrows(IllegalStateException.class, () -> loader.load(new JwtProperties()));
    }
}
