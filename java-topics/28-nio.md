# 28. NIO (New I/O)

## Theory

Java NIO (New I/O), introduced in Java 1.4 and enhanced in Java 7 (NIO.2), provides a modern alternative to the classic `java.io` package. NIO offers:

1. **Non-blocking I/O** — Threads don't block waiting for data (useful for servers)
2. **Buffers** — Data read into/written from buffer objects (not stream-at-a-time)
3. **Channels** — Bidirectional data connections (unlike one-way streams)
4. **Selectors** — Single thread monitors multiple channels (event-driven I/O)
5. **NIO.2 (Java 7)** — `Path`, `Files`, `FileSystem` — modern file operations

### NIO vs Classic I/O

| Feature | Classic I/O | NIO |
|---------|------------|-----|
| Model | Stream-oriented | Buffer-oriented |
| Direction | One-way (input OR output) | Bidirectional (channels) |
| Blocking | Always blocking | Blocking or non-blocking |
| Multiplexing | One thread per connection | One thread, many connections (selector) |
| File operations | File class (limited) | Path + Files (powerful) |

---

## Internal Working

### NIO Architecture

```
Application
     ↓
  Buffer (ByteBuffer, CharBuffer, etc.)
     ↓
  Channel (FileChannel, SocketChannel, etc.)
     ↓
  OS Kernel (file system, network stack)
     ↓
  Hardware (disk, NIC)

Selector model:
  Selector
  ├── monitors Channel 1 (OP_READ ready)
  ├── monitors Channel 2 (OP_WRITE ready)
  ├── monitors Channel 3 (not ready)
  └── monitors Channel N (OP_ACCEPT ready)
  
  Single thread handles all ready channels!
```

### Buffer Internal Structure

```
Buffer has 4 key properties:
┌─────────────────────────────────────────────────────┐
│ 0   1   2   3   4   5   6   7   8   9  10  11  12  │
│[H] [e] [l] [l] [o] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] │
└─────────────────────────────────────────────────────┘
  ↑                   ↑                               ↑
  mark              position                       capacity
                      ↑
                    limit (after flip)

capacity = 13 (total size, never changes)
position = 5  (next read/write index)
limit    = 13 (write mode) or 5 (read mode after flip)
mark     = 0  (saved position for reset)

Invariant: 0 ≤ mark ≤ position ≤ limit ≤ capacity
```

### Buffer State Transitions

```
1. WRITE MODE (after allocate or clear):
   position = 0, limit = capacity
   [  _  _  _  _  _  _  _  _  _  ]
    ↑pos                          ↑limit=capacity

2. After writing 5 bytes:
   [  H  e  l  l  o  _  _  _  _  ]
                      ↑pos         ↑limit

3. After flip() (switch to READ MODE):
   position = 0, limit = old position
   [  H  e  l  l  o  _  _  _  _  ]
    ↑pos              ↑limit       ↑capacity

4. After reading 3 bytes:
   [  H  e  l  l  o  _  _  _  _  ]
             ↑pos     ↑limit

5. After compact() (shift unread data to beginning):
   [  l  o  _  _  _  _  _  _  _  ]
          ↑pos                     ↑limit=capacity
   (ready for more writing)
```

---

## Diagram

### Channel Types

```
Channel (interface)
├── FileChannel          — file I/O (always blocking for file ops)
├── SocketChannel        — TCP client connection
├── ServerSocketChannel  — TCP server (accepts connections)
├── DatagramChannel      — UDP communication
├── Pipe.SinkChannel     — write end of pipe
└── Pipe.SourceChannel   — read end of pipe
```

### NIO.2 File API

```
Path                     — represents a file path (replacement for java.io.File)
Files                    — static utility methods for file operations
FileSystem               — represents a file system
FileSystems              — factory for FileSystem instances
FileStore                — represents a storage pool

Path operations:
  Path.of("src", "main", "java")  → src/main/java
  path.resolve("file.txt")        → src/main/java/file.txt
  path.getParent()                 → src/main
  path.getFileName()               → java
  path.toAbsolutePath()            → /home/user/project/src/main/java
```

---

## Code

### Path and Files (NIO.2 — Most Common Usage)

```java
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.List;

public class NioFilesDemo {
    
    public static void main(String[] args) throws IOException {
        // Creating Path objects
        Path path = Path.of("data", "users.txt");       // Java 11+
        Path path2 = Paths.get("data", "users.txt");    // Java 7+
        Path absolute = path.toAbsolutePath();
        
        // Path operations
        System.out.println("File name: " + path.getFileName());   // users.txt
        System.out.println("Parent: " + path.getParent());        // data
        System.out.println("Name count: " + path.getNameCount()); // 2
        
        // Resolve (join paths)
        Path base = Path.of("/home/user");
        Path full = base.resolve("documents/file.txt");
        // /home/user/documents/file.txt
        
        // Relativize
        Path p1 = Path.of("/home/user/docs");
        Path p2 = Path.of("/home/user/photos");
        Path relative = p1.relativize(p2); // ../photos
        
        // Normalize
        Path messy = Path.of("/home/user/../user/./docs");
        Path clean = messy.normalize(); // /home/user/docs
    }
}
```

### Reading and Writing Files (Simple Operations)

```java
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SimpleFileOps {
    
    public static void main(String[] args) throws IOException {
        Path file = Path.of("example.txt");
        
        // Write string to file (Java 11+)
        Files.writeString(file, "Hello, NIO!\nSecond line\n",
            StandardCharsets.UTF_8);
        
        // Read entire file as string (Java 11+)
        String content = Files.readString(file, StandardCharsets.UTF_8);
        System.out.println(content);
        
        // Read all lines as List
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        lines.forEach(System.out::println);
        
        // Read all bytes
        byte[] bytes = Files.readAllBytes(file);
        
        // Write lines
        List<String> data = List.of("Line 1", "Line 2", "Line 3");
        Files.write(Path.of("output.txt"), data, StandardCharsets.UTF_8);
        
        // Append to file
        Files.writeString(file, "Appended line\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.APPEND);
        
        // Write with options
        Files.write(Path.of("log.txt"), 
            List.of("log entry"),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
    }
}
```

### Stream-Based File Reading (Large Files)

```java
import java.nio.file.*;
import java.util.stream.Stream;

public class StreamFileReading {
    
    // Process large file line by line (lazy - doesn't load entire file)
    public static void processLargeFile(Path path) throws IOException {
        try (Stream<String> lines = Files.lines(path)) {
            lines.filter(line -> !line.isBlank())
                 .map(String::trim)
                 .filter(line -> line.startsWith("ERROR"))
                 .forEach(System.out::println);
        }
    }
    
    // Count lines matching a pattern
    public static long countErrors(Path logFile) throws IOException {
        try (Stream<String> lines = Files.lines(logFile)) {
            return lines.filter(line -> line.contains("ERROR")).count();
        }
    }
    
    // Find files recursively
    public static void findJavaFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(System.out::println);
        }
    }
    
    // Find files matching a pattern
    public static void findByGlob(Path dir) throws IOException {
        try (Stream<Path> paths = Files.find(dir, Integer.MAX_VALUE,
                (path, attrs) -> attrs.isRegularFile() 
                    && path.toString().endsWith(".log")
                    && attrs.size() > 1024)) {
            paths.forEach(System.out::println);
        }
    }
}
```

### File and Directory Operations

```java
import java.nio.file.*;
import java.nio.file.attribute.*;

public class FileDirectoryOps {
    
    public static void main(String[] args) throws IOException {
        // Create directory
        Path dir = Files.createDirectories(Path.of("a", "b", "c"));
        
        // Create file
        Path file = Files.createFile(dir.resolve("test.txt"));
        
        // Check existence
        boolean exists = Files.exists(Path.of("data.txt"));
        boolean isDir = Files.isDirectory(Path.of("src"));
        boolean isFile = Files.isRegularFile(Path.of("pom.xml"));
        
        // Copy file
        Files.copy(Path.of("source.txt"), Path.of("dest.txt"),
            StandardCopyOption.REPLACE_EXISTING);
        
        // Move/Rename file
        Files.move(Path.of("old.txt"), Path.of("new.txt"),
            StandardCopyOption.ATOMIC_MOVE);
        
        // Delete
        Files.delete(Path.of("temp.txt"));           // throws if not exists
        Files.deleteIfExists(Path.of("maybe.txt"));  // returns boolean
        
        // File attributes
        BasicFileAttributes attrs = Files.readAttributes(
            Path.of("data.txt"), BasicFileAttributes.class);
        System.out.println("Size: " + attrs.size());
        System.out.println("Created: " + attrs.creationTime());
        System.out.println("Modified: " + attrs.lastModifiedTime());
        System.out.println("Is directory: " + attrs.isDirectory());
        
        // List directory contents
        try (var entries = Files.list(Path.of("."))) {
            entries.filter(Files::isRegularFile)
                   .forEach(System.out::println);
        }
        
        // Create temp file/directory
        Path tempFile = Files.createTempFile("prefix_", ".tmp");
        Path tempDir = Files.createTempDirectory("myapp_");
    }
}
```

### ByteBuffer Operations

```java
import java.nio.ByteBuffer;

public class ByteBufferDemo {
    
    public static void main(String[] args) {
        // Allocate buffer (heap)
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        
        // Allocate direct buffer (off-heap, faster I/O)
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
        
        // Wrap existing array
        byte[] data = "Hello".getBytes();
        ByteBuffer wrapped = ByteBuffer.wrap(data);
        
        // Writing to buffer
        buffer.put((byte) 72);        // 'H'
        buffer.put("ello".getBytes());
        buffer.putInt(42);
        buffer.putDouble(3.14);
        
        // Switch to read mode
        buffer.flip();
        // Now: position=0, limit=old_position
        
        // Reading from buffer
        byte b = buffer.get();           // 'H'
        byte[] bytes = new byte[4];
        buffer.get(bytes);               // "ello"
        int intVal = buffer.getInt();    // 42
        double dblVal = buffer.getDouble(); // 3.14
        
        // Reset for re-reading
        buffer.rewind();  // position=0, limit unchanged
        
        // Clear for re-writing
        buffer.clear();   // position=0, limit=capacity
        
        // Compact: shift unread data to beginning
        buffer.compact(); // copies remaining to start, position after last copied
    }
}
```

### FileChannel Operations

```java
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.io.*;

public class FileChannelDemo {
    
    // Read file using FileChannel
    public static String readFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            channel.read(buffer);
            buffer.flip();
            return new String(buffer.array(), 0, buffer.limit());
        }
    }
    
    // Write file using FileChannel
    public static void writeFile(Path path, String content) throws IOException {
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(content.getBytes());
            channel.write(buffer);
        }
    }
    
    // Copy file using channel transfer (kernel-level, very fast)
    public static void copyFile(Path src, Path dest) throws IOException {
        try (FileChannel source = FileChannel.open(src, StandardOpenOption.READ);
             FileChannel target = FileChannel.open(dest, 
                 StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            source.transferTo(0, source.size(), target);
            // Uses OS-level DMA transfer when possible (zero-copy)
        }
    }
    
    // Memory-mapped file (for large files)
    public static void memoryMappedRead(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            MappedByteBuffer mmap = channel.map(
                FileChannel.MapMode.READ_ONLY, 0, channel.size());
            
            // mmap is backed by virtual memory
            // OS handles page faults and disk reads transparently
            while (mmap.hasRemaining()) {
                byte b = mmap.get(); // Reads directly from mapped memory
            }
        }
    }
}
```

### WatchService (File System Monitoring)

```java
import java.nio.file.*;

public class FileWatcherDemo {
    
    public static void watchDirectory(Path dir) throws IOException, InterruptedException {
        WatchService watcher = FileSystems.getDefault().newWatchService();
        
        dir.register(watcher,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY);
        
        System.out.println("Watching " + dir + " for changes...");
        
        while (true) {
            WatchKey key = watcher.take(); // Blocks until events available
            
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                Path fileName = (Path) event.context();
                
                System.out.printf("Event: %s → %s%n", kind.name(), fileName);
            }
            
            boolean valid = key.reset(); // Must reset key to receive further events
            if (!valid) break; // Directory no longer accessible
        }
    }
}
```

### Walking File Trees

```java
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.IOException;

public class FileTreeWalker {
    
    // Delete directory recursively using FileVisitor
    public static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) 
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) 
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    // Calculate directory size
    public static long calculateSize(Path dir) throws IOException {
        long[] size = {0};
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                size[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return size[0];
    }
    
    // Copy directory recursively
    public static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) 
                    throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) 
                    throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)),
                    StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
```

---

## Dry Run

### ByteBuffer flip() Operation

```
Initial state after allocate(10):
position=0, limit=10, capacity=10
[  _  _  _  _  _  _  _  _  _  _  ]
 ↑pos                              ↑limit=cap

After buffer.putInt(42):  (writes 4 bytes)
position=4, limit=10
[  0  0  0  42  _  _  _  _  _  _  ]
              ↑pos                  ↑limit

After buffer.putShort((short)7):  (writes 2 bytes)
position=6, limit=10
[  0  0  0  42  0  7  _  _  _  _  ]
                    ↑pos            ↑limit

After buffer.flip():
position=0, limit=6, capacity=10
[  0  0  0  42  0  7  _  _  _  _  ]
 ↑pos               ↑limit         ↑cap

Now reading:
buffer.getInt() → reads 4 bytes → 42, position=4
buffer.getShort() → reads 2 bytes → 7, position=6
buffer.hasRemaining() → false (position == limit)
```

---

## Complexity

| Operation | Time | Notes |
|-----------|------|-------|
| Files.readString() | O(n) | Reads entire file |
| Files.lines() | O(1) lazy | Each line processed on demand |
| Files.walk() | O(n) | n = total files/dirs |
| FileChannel.transferTo() | O(n) | Kernel-level, zero-copy when possible |
| Memory-mapped I/O | O(1) access | Page faults are transparent |
| WatchService.take() | Blocking | OS notification-based |

### Memory-Mapped vs Standard I/O

| File Size | Standard I/O | Memory-Mapped |
|-----------|-------------|---------------|
| < 1 MB | Faster (setup overhead) | Slower (mmap overhead) |
| 1-100 MB | Similar | Slightly faster |
| > 100 MB | Slower (many system calls) | Much faster (random access) |
| > 2 GB | Must chunk reads | Need multiple mappings or 64-bit |

---

## Real Project Usage

### 1. Efficient Log File Search

```java
public class LogSearcher {
    
    public List<String> searchLogs(Path logDir, String pattern, LocalDate date) 
            throws IOException {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        
        try (Stream<Path> logFiles = Files.list(logDir)) {
            return logFiles
                .filter(p -> p.getFileName().toString().contains(dateStr))
                .flatMap(p -> {
                    try { return Files.lines(p); } 
                    catch (IOException e) { return Stream.empty(); }
                })
                .filter(line -> line.contains(pattern))
                .collect(Collectors.toList());
        }
    }
}
```

### 2. Atomic File Write (Safe Configuration Update)

```java
public class SafeConfigWriter {
    
    public void writeConfig(Path configPath, String content) throws IOException {
        // Write to temp file first
        Path tempFile = Files.createTempFile(
            configPath.getParent(), "config_", ".tmp");
        
        try {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            // Atomic move: either fully succeeds or config is unchanged
            Files.move(tempFile, configPath, 
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }
}
```

### 3. File Upload Handler

```java
public class FileUploadHandler {
    private final Path uploadDir;
    private final long maxSize;
    
    public FileUploadHandler(Path uploadDir, long maxSizeBytes) throws IOException {
        this.uploadDir = Files.createDirectories(uploadDir);
        this.maxSize = maxSizeBytes;
    }
    
    public Path saveUpload(InputStream input, String originalName) throws IOException {
        String safeName = sanitizeFileName(originalName);
        String uniqueName = UUID.randomUUID() + "_" + safeName;
        Path target = uploadDir.resolve(uniqueName);
        
        // Copy with size limit
        try (var limited = new LimitedInputStream(input, maxSize)) {
            Files.copy(limited, target);
        }
        
        return target;
    }
    
    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
```

---

## Interview Questions and Answers

### Q1: What is the difference between NIO and classic I/O?
**A**: Classic I/O is stream-oriented (process one byte/char at a time), always blocking, and one-directional. NIO is buffer-oriented (read into buffers), supports non-blocking mode, uses bidirectional channels, and supports multiplexing via selectors. NIO.2 adds modern file operations (Path, Files) that are superior to java.io.File.

### Q2: What is a ByteBuffer? Explain flip().
**A**: ByteBuffer is a container for bytes with position, limit, and capacity markers. After writing to a buffer, you call `flip()` to switch from write mode to read mode. `flip()` sets limit = current position, then sets position = 0. This means reads will start from the beginning and stop at what was written.

### Q3: What is the difference between direct and non-direct ByteBuffer?
**A**: Non-direct buffers (`allocate()`) are stored on the Java heap. Direct buffers (`allocateDirect()`) are stored in native OS memory, avoiding one copy during I/O operations (no heap → kernel copy). Direct buffers are faster for I/O but slower to allocate/deallocate. Use them for long-lived buffers with heavy I/O.

### Q4: What is a memory-mapped file?
**A**: Memory-mapped files (via `FileChannel.map()`) map a file region directly into process address space. Reading/writing the buffer reads/writes the file transparently through virtual memory page faults. Extremely fast for random access on large files (databases use this extensively).

### Q5: Path vs File — which to prefer?
**A**: Always prefer `Path` (NIO.2). `java.io.File` has many design issues: returns boolean instead of throwing exceptions, limited operation support, inconsistent behavior across platforms. `Path` + `Files` provides richer API, better error handling, and supports WatchService, attributes, and symbolic links properly.

---

## Follow-up Questions and Answers

### Q: When would you use FileChannel.transferTo()?
**A**: For file-to-file or file-to-socket copies. It enables zero-copy transfer where the OS moves data directly between kernel buffers without copying to user space. Ideal for static file servers and file backup utilities. Much faster than reading into a buffer and writing out.

### Q: What is a Selector and when is it used?
**A**: A Selector monitors multiple channels for readiness (readable, writable, connectable). A single thread can manage thousands of connections by checking which channels are ready for I/O. Used in high-performance servers (Netty, NIO-based HTTP servers) to handle many concurrent connections without a thread per connection.

---

## Common Mistakes

| Mistake | Problem | Fix |
|---------|---------|-----|
| Forgetting `flip()` before reading | Reads from wrong position (empty data) | Always flip() after writing, before reading |
| Not closing streams from `Files.lines()` | Resource leak (open file handle) | Use try-with-resources |
| Using `Files.readAllBytes()` on large files | OutOfMemoryError | Use `Files.lines()` or memory-mapped I/O |
| Direct buffer for short-lived operations | GC pressure, slow allocation | Use heap buffers for small/temp operations |
| Not calling `key.reset()` in WatchService | No more events received | Always reset after processing |
| Assuming `Path.of("file.txt")` is absolute | Platform-dependent behavior | Use `toAbsolutePath()` when needed |

---

## Best Practices

1. **Use `Files.readString()`/`Files.writeString()` for small files** — Simple, one-liner operations
2. **Use `Files.lines()` for large files** — Lazy streaming, constant memory
3. **Always close stream-returning methods** — `Files.lines()`, `Files.list()`, `Files.walk()` return streams that must be closed
4. **Use `StandardCharsets.UTF_8` explicitly** — Don't rely on system default encoding
5. **Prefer `Files.copy()` with `REPLACE_EXISTING`** — Clear semantics
6. **Use `Path.resolve()` for joining paths** — Not string concatenation
7. **Use atomic moves for config files** — Write to temp, then atomic rename

---

## Production Considerations

- **Memory-mapped files**: Don't map multi-GB files on 32-bit JVMs. On 64-bit, it's fine but unmapping is not deterministic (no explicit unmap in public API until Java 19 MemorySegment).
- **WatchService reliability**: On macOS, WatchService uses polling (not native events). On Linux/Windows, it uses native file system notifications (inotify/ReadDirectoryChangesW).
- **DirectByteBuffer deallocation**: GC manages direct buffer deallocation. Under high allocation rates, you may need to trigger GC or use `sun.misc.Cleaner` (internal API).
- **File locking**: `FileChannel.lock()` is advisory on most Unix systems — other processes can ignore it. Only reliable for Java-to-Java coordination.
- **Large directory listing**: `Files.list()` returns a lazy stream but may still block on very large directories (millions of entries). Consider `Files.newDirectoryStream()` with glob patterns.

---

## Related Topics

- [27. I/O](./27-io.md)
- [29. Serialization](./29-serialization.md)
- [32. Modern Java](./32-modern-java.md)
