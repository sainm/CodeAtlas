package org.sainm.codeatlas.graph.offheap;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class OffHeapGraphIndexMappedFile {
    private OffHeapGraphIndexMappedFile() {
    }

    public static MemorySegment mapReadOnly(Path path, Arena arena) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path is required");
        }
        if (arena == null) {
            throw new IllegalArgumentException("arena is required");
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
        }
    }
}
