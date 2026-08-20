# Inter-Process Communication (IPC)

## Why IPC?
- Processes are isolated (separate address spaces)
- Sometimes processes need to share data or coordinate
- IPC provides mechanisms for communication and synchronization between processes

---

## IPC Mechanisms Overview

| Mechanism | Direction | Speed | Relationship | Use Case |
|-----------|-----------|-------|--------------|----------|
| Pipes | Unidirectional | Fast | Parent-child | Simple data streaming |
| Named Pipes (FIFO) | Unidirectional/Both | Fast | Any processes | Inter-process streaming |
| Shared Memory | Bidirectional | Fastest | Any processes | Large data sharing |
| Message Queues | Bidirectional | Medium | Any processes | Structured messages |
| Sockets | Bidirectional | Slower | Any (even network) | Network communication |
| Signals | Unidirectional | Fast | Any processes | Notifications |
| Memory-mapped files | Bidirectional | Fast | Any processes | File-based sharing |

---

## Pipes

### Anonymous Pipes
- Unidirectional communication channel
- Only between related processes (parent-child)
- Exists only while processes are alive
- FIFO ordering

```c
int fd[2];
pipe(fd);      // fd[0] = read end, fd[1] = write end

if (fork() == 0) {
    // Child process
    close(fd[1]);              // close write end
    read(fd[0], buffer, size); // read from pipe
    close(fd[0]);
} else {
    // Parent process
    close(fd[0]);              // close read end
    write(fd[1], data, size);  // write to pipe
    close(fd[1]);
}
```

### Linux Shell Pipes
```bash
ls -l | grep ".txt" | wc -l
# Creates two pipes:
# ls stdout → pipe1 → grep stdin
# grep stdout → pipe2 → wc stdin
```

### Named Pipes (FIFOs)
- Have a name in the filesystem
- Can be used by unrelated processes
- Persist until explicitly deleted

```bash
mkfifo /tmp/myfifo

# Process 1 (writer)
echo "Hello" > /tmp/myfifo

# Process 2 (reader)
cat /tmp/myfifo
```

### Pipe Limitations
- Unidirectional (need two pipes for bidirectional)
- Limited buffer size (typically 64KB on Linux)
- Blocking by default (reader blocks if empty, writer blocks if full)
- Data is byte stream (no message boundaries)

---

## Shared Memory

### Concept
- Multiple processes map the same physical memory region into their address spaces
- Fastest IPC (no kernel involvement after setup)
- Requires synchronization (semaphores, mutexes)

### How it Works
```
Process A address space:      Physical Memory:       Process B address space:
[code]                                               [code]
[data]                                               [data]
[shared region] --------→ [Shared Memory] ←--------- [shared region]
[heap]                                               [heap]
[stack]                                              [stack]
```

### POSIX Shared Memory
```c
// Process 1: Create shared memory
int fd = shm_open("/my_shm", O_CREAT | O_RDWR, 0666);
ftruncate(fd, SIZE);
void *ptr = mmap(NULL, SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);

// Write data
sprintf(ptr, "Hello from Process 1");

// Process 2: Open existing shared memory
int fd = shm_open("/my_shm", O_RDONLY, 0666);
void *ptr = mmap(NULL, SIZE, PROT_READ, MAP_SHARED, fd, 0);

// Read data
printf("%s\n", (char*)ptr);
```

### Pros & Cons
- ✅ Fastest IPC (direct memory access, no copying)
- ✅ Large data transfer
- ❌ Requires explicit synchronization
- ❌ Complex to manage
- ❌ No built-in notification when data is ready

---

## Message Queues

### Concept
- Linked list of messages stored in kernel
- Processes send/receive structured messages
- Messages have type and priority
- Persist until explicitly deleted or system reboot

### Properties
- Asynchronous: Sender doesn't need to wait for receiver
- Typed messages: Can selectively receive by type
- Bounded: Fixed queue capacity
- Kernel-managed: Automatic synchronization

### POSIX Message Queue
```c
// Sender
mqd_t mq = mq_open("/my_queue", O_CREAT | O_WRONLY, 0666, &attr);
mq_send(mq, message, strlen(message), priority);

// Receiver
mqd_t mq = mq_open("/my_queue", O_RDONLY);
mq_receive(mq, buffer, MAX_SIZE, &priority);
```

### Message Passing Models
| Model | Description |
|-------|-------------|
| **Direct** | Sender names receiver explicitly |
| **Indirect** | Communication through mailbox/port |
| **Synchronous** | Sender blocks until receiver gets message |
| **Asynchronous** | Sender continues immediately |
| **Buffered** | Queue holds messages |
| **Unbuffered** | Sender blocks until receiver ready (rendezvous) |

---

## Sockets

### Concept
- Endpoint for communication
- Can be used for IPC (Unix domain sockets) or network communication (TCP/UDP)
- Bidirectional

### Types
| Type | Protocol | Characteristics |
|------|----------|----------------|
| Stream (SOCK_STREAM) | TCP | Reliable, ordered, connection-oriented |
| Datagram (SOCK_DGRAM) | UDP | Unreliable, connectionless, fast |
| Unix Domain | Local | Fast local IPC, no network overhead |

---

## Signals

### Concept
- Software interrupts sent to a process
- Asynchronous notification mechanism
- Limited information (just the signal number)

### Common Signals
| Signal | Number | Description | Default Action |
|--------|--------|-------------|----------------|
| SIGTERM | 15 | Termination request | Terminate |
| SIGKILL | 9 | Force kill (cannot be caught) | Terminate |
| SIGINT | 2 | Interrupt (Ctrl+C) | Terminate |
| SIGSEGV | 11 | Segmentation fault | Core dump |
| SIGSTOP | 19 | Stop process (cannot be caught) | Stop |
| SIGCONT | 18 | Continue stopped process | Continue |
| SIGCHLD | 17 | Child process terminated | Ignore |

### Signal Handling
```c
void handler(int sig) {
    printf("Caught signal %d\n", sig);
}

signal(SIGINT, handler);  // Register custom handler
signal(SIGTERM, SIG_IGN); // Ignore signal
signal(SIGKILL, handler); // ERROR: SIGKILL cannot be caught!
```

---

## Comparison: When to Use What

| Scenario | Best IPC |
|----------|----------|
| Parent-child simple data flow | Pipe |
| Large data sharing, high speed | Shared Memory |
| Structured messages between services | Message Queue |
| Network communication | Socket |
| Process notification/control | Signal |
| File-based data sharing | Memory-mapped file |
| Unrelated processes, simple data | Named Pipe (FIFO) |

---

## Key Interview Questions

**Q: What's the fastest IPC mechanism?**
> Shared memory, because after initial setup, processes access memory directly without kernel involvement. No data copying between user and kernel space.

**Q: Why do we need synchronization with shared memory?**
> Because multiple processes access the same memory region concurrently. Without synchronization, race conditions occur (one process reads while another writes, getting partial/corrupted data).

**Q: What happens when you write to a pipe with no reader?**
> The kernel sends SIGPIPE signal to the writer, and write() returns -1 with errno set to EPIPE.

**Q: Pipes vs Message Queues?**
> Pipes: byte stream, no boundaries, typically parent-child. Message queues: structured messages with types/priorities, any processes, persist independently of processes.

**Q: How does a Unix domain socket differ from a TCP socket?**
> Unix domain sockets are for local IPC only (same machine). They're faster because they bypass the network stack (no TCP/IP overhead, no checksums, no routing). They use filesystem paths instead of IP:port.
