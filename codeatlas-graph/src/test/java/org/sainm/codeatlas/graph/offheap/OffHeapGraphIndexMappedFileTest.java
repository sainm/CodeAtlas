package org.sainm.codeatlas.graph.offheap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OffHeapGraphIndexMappedFileTest {
    @TempDir
    Path tempDir;

    @Test
    void mapsPersistentGraphCacheFileWithArena() throws Exception {
        Path cacheFile = tempDir.resolve("graph-index.bin");
        byte[] bytes = new byte[8];
        java.nio.ByteBuffer.wrap(bytes)
            .order(ByteOrder.nativeOrder())
            .putInt(4)
            .putInt(3);
        Files.write(cacheFile, bytes);

        try (Arena arena = Arena.ofConfined()) {
            var segment = OffHeapGraphIndexMappedFile.mapReadOnly(cacheFile, arena);

            assertEquals(8, segment.byteSize());
            assertEquals(4, segment.get(ValueLayout.JAVA_INT, 0));
            assertEquals(3, segment.get(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT.byteSize()));
        }
    }
}
