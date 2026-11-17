package com.observernotes.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.observernotes.sfx.SfxPreferences;
import com.observernotes.support.TempFiles;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonSfxSettingsRepositoryTest {

    private Path dir;
    private JsonSfxSettingsRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        dir = TempFiles.create();
        repo = new JsonSfxSettingsRepository(dir, new AtomicFileWriter(),
                Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws Exception {
        TempFiles.deleteRecursively(dir);
    }

    @Test
    void defaultsEnabledAtThirtyPercentAndRoundTripsIndependently() {
        assertEquals(SfxPreferences.defaults(), repo.load().value());
        SfxPreferences changed = new SfxPreferences(false, 0.65);
        repo.save(changed);
        assertEquals(changed, new JsonSfxSettingsRepository(dir).load().value());
    }

    @Test
    void invalidSettingsFallBackWithoutCrashing() throws Exception {
        TempFiles.write(repo.file(), "{\"formatVersion\":1,\"enabled\":true,\"volume\":4}");
        LoadResult<SfxPreferences> result = repo.load();
        assertEquals(LoadResult.Status.DISCARDED, result.status());
        assertEquals(SfxPreferences.defaults(), result.value());
    }
}
