import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;

class SimpleServer {
    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.fromArgs(args);
        if (config == null)
            return;

        JobRegistry registry = new JobRegistry();
        try (ServerSocket serverSocket = new ServerSocket(config.port)) {
            if (config.verbose) {
                System.out.println("Server started on port " + config.port);
            }
            while (true) {
                Socket socket = serverSocket.accept();
                Thread connection = new Thread(new ClientHandler(socket, registry, config));
                connection.start();
            }
        } catch (Exception e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// ClientHandler owns a single client connection. It should read one line at a
// time, validate commands, and return exactly one response line for each
// request. It is responsible for:
// - Parsing the command and arguments.
// - Enforcing usage errors and bad input errors.
// - Delegating to the job registry to create, lookup, or cancel jobs.
// - Returning the protocol strings (JOB, STATUS, CANCELLED, NOTCANCELLED, BYE).
// - Keeping all protocol output deterministic and single-line.
//
class ClientHandler implements Runnable {
    private final Socket socket;
    private final JobRegistry registry;
    private final ServerConfig config;

    ClientHandler(Socket socket, JobRegistry registry, ServerConfig config) {
        this.socket = socket;
        this.registry = registry;
        this.config = config;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
                PrintWriter out = new PrintWriter(this.socket.getOutputStream(), true)) {
            while (true) {
                String line = in.readLine();
                if (line == null) {
                    break;
                }
                if (config.verbose) {
                    System.out.println("recv: " + line);
                }
                String response = handle(line);
                out.println(response);
                if (config.verbose) {
                    System.out.println("send: " + response);
                }
                if ("BYE".equals(response)) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading from socket: " + e.getMessage());
            try {
                this.socket.close();
            } catch (Exception ex) {
                System.err.println("Error closing socket: " + ex.getMessage());
            }
        }
    }

    private String handle(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return "ERR EMPTY";
        }
        String[] parts = trimmed.split("\\s+");
        String command = parts[0];
        if (command.equals("SUBMIT")) {
            if (parts.length != 2) return usageError("SUBMIT", "<ms>");
            return handleSubmit(parts[1]);
        }
        if (command.equals("STATUS")) {
            if (parts.length != 2) return usageError("STATUS", "<id>");
            return handleStatus(parts[1]);
        }
        if (command.equals("CANCEL")) {
            if (parts.length != 2) return usageError("CANCEL", "<id>");
            return handleCancel(parts[1]);
        }
        if (command.equals("QUIT")) {
            return "BYE";
        }
        return "ERR UNKNOWN_COMMAND";
    }

    private String handleSubmit(String ms) {
        try {
            long duration = Long.parseLong(ms);
            if (duration <= 0) return badMsError();
            Job job = registry.createJob(duration);
            return "JOB " + job.getId();
        } catch (NumberFormatException e) {
            return badMsError();
        }
    }

    private String handleStatus(String idStr) {
        Long id = parseId(idStr);
        if (id == null) return badIdError();
        Job job = registry.find(id);
        if (job == null) return "STATUS " + id + " UNKNOWN";
        return "STATUS " + id + " " + job.getState();
    }

    private String handleCancel(String idStr) {
        Long id = parseId(idStr);
        if (id == null) return badIdError();
        Job job = registry.find(id);
        if (job == null) return "NOTCANCELLED " + id + " UNKNOWN";
        if (job.cancel()) return "CANCELLED " + id;
        return "NOTCANCELLED " + id + " " + job.getState();
    }

    private Long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String usageError(String command, String args) {
        return "ERR USAGE " + command + " " + args;
    }

    private String badIdError() {
        return "ERR BAD_ID";
    }

    private String badMsError() {
        return "ERR BAD_MS";
    }
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// ServerConfig parses command-line args and holds server settings. It should:
// - Accept an optional "-v" for verbose logging.
// - Require a <port> argument.
// - Print usage and return null if args are invalid.
// - Expose the parsed port and verbose flag.
//
class ServerConfig {
    final int port;
    final boolean verbose;

    private ServerConfig(int port, boolean verbose) {
        this.port = port;
        this.verbose = verbose;
    }

    static ServerConfig fromArgs(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: server.java [-v] <port>");
            return null;
        }
        boolean verbose = "-v".equals(args[0]);
        int portIndex = verbose ? 1 : 0;
        if (args.length <= portIndex) {
            System.err.println("Usage: server.java [-v] <port>");
            return null;
        }
        try {
            int port = Integer.parseInt(args[portIndex]);
            return new ServerConfig(port, verbose);
        } catch (NumberFormatException e) {
            System.err.println("Usage: server.java [-v] <port>");
            return null;
        }
    }
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// JobRegistry stores jobs and assigns unique ids. It should:
// - Keep a shared map of id -> Job.
// - Generate monotonically increasing ids (1, 2, 3, ...).
// - Think about which methods need synchronization for thread safety.
//
class JobRegistry {
    private final Map<Long, Job> jobs = new HashMap<>();
    private long nextId = 1;

    synchronized Job createJob(long durationMs) {
        Job job = new Job(nextId, durationMs);
        jobs.put(nextId, job);
        nextId++;
        new Thread(new JobWorker(job)).start();
        return job;
    }

    synchronized Job find(long id) {
        return jobs.get(id);
    }
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// JobWorker performs the simulated job work on its own thread and updates
// the Job state as it starts, completes, or is cancelled.
//
// This class is provided, but you are expected to read it and understand how it
// drives the Job lifecycle.
//
class JobWorker implements Runnable {
    private final Job job;

    JobWorker(Job job) {
        this.job = job;
    }

    @Override
    public void run() {
        job.markRunning();

        long remaining = job.getDurationMs();
        long chunk = 25;
        while (remaining > 0 && !job.isCancelled()) {
            long sleepFor = Math.min(chunk, remaining);
            sleepQuietly(sleepFor);
            remaining -= sleepFor;
        }

        if (job.isCancelled()) {
            job.markCancelled();
        } else {
            job.markDone();
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// JobState provides a small set of named states that describe a job's
// lifecycle. Think about how JobState is used as work begins, completes,
// or is cancelled.
//
enum JobState {
    QUEUED,
    RUNNING,
    DONE,
    CANCELLED
}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// Job represents a unit of work. It should:
// - Store id and duration.
// - Track state transitions (QUEUED -> RUNNING -> DONE or CANCELLED).
// - Track cancellation requests and report whether cancel succeeded.
// - Decide which methods need synchronization for thread safety.
//
class Job {
    private final long id;
    private final long durationMs;
    private JobState state = JobState.QUEUED;
    private volatile boolean cancelRequested;

    Job(long id, long durationMs) {
        this.id = id;
        this.durationMs = durationMs;
    }

    long getId() {
        return id;
    }

    long getDurationMs() {
        return durationMs;
    }

    synchronized JobState getState() {
        return state;
    }

    boolean isCancelled() {
        return cancelRequested;
    }

    synchronized void markRunning() {
        state = JobState.RUNNING;
    }

    synchronized void markDone() {
        state = JobState.DONE;
    }

    synchronized void markCancelled() {
        state = JobState.CANCELLED;
    }

    synchronized boolean cancel() {
        if (state == JobState.CANCELLED || state == JobState.DONE) {
            return false;
        }
        cancelRequested = true;
        return true;
    }
}
