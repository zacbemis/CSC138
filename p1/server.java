import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

class SimpleServer {
    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.fromArgs(args);
        if (config == null) {
            return;
        }

        JobRegistry registry = new JobRegistry();
        try (ServerSocket serverSocket = new ServerSocket(config.port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                Thread connection = new Thread(new ClientHandler(socket, registry, config));
                connection.start();
            }
        }
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  ClientHandler owns a single client connection. It should read one line at a
//  time, validate commands, and return exactly one response line for each
//  request. It is responsible for:
//  - Parsing the command and arguments.
//  - Enforcing usage errors and bad input errors.
//  - Delegating to the job registry to create, lookup, or cancel jobs.
//  - Returning the protocol strings (JOB, STATUS, CANCELLED, NOTCANCELLED, BYE).
//  - Keeping all protocol output deterministic and single-line.
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
        // TODO: open the socket input and output streams
        // TODO: read one line at a time from the client
        // TODO: if verbose, print "recv: <line>"
        // TODO: pass the line to handle(...) to get a response
        // TODO: if verbose, print "send: <response>"
        // TODO: send the response back to the client
        // TODO: if the response is "BYE", exit the loop and close the socket
        // TODO: handle exceptions without crashing the server
        // TODO: make sure resources are closed even on errors
    }

    private String handle(String line) {
        return null;
    }

    private String handleSubmit(String value) {
        return null;
    }

    private String handleStatus(String value) {
        return null;
    }

    private String handleCancel(String value) {
        return null;
    }

    private Long parseId(String value) {
        return null;
    }

    private String usageError(String command, String args) {
        return null;
    }

    private String badIdError() {
        return null;
    }

    private String badMsError() {
        return null;
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  ServerConfig parses command-line args and holds server settings. It should:
//  - Accept an optional "-v" for verbose logging.
//  - Require a <port> argument.
//  - Print usage and return null if args are invalid.
//  - Expose the parsed port and verbose flag.
//
class ServerConfig {
    final int port;
    final boolean verbose;

    private ServerConfig(int port, boolean verbose) {
        this.port = port;
        this.verbose = verbose;
    }

    static ServerConfig fromArgs(String[] args) {
        return null;
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  JobRegistry stores jobs and assigns unique ids. It should:
//  - Keep a shared map of id -> Job.
//  - Generate monotonically increasing ids (1, 2, 3, ...).
//  - Think about which methods need synchronization for thread safety.
//
class JobRegistry {
    private final Map<Long, Job> jobs = new HashMap<>();
    private long nextId = 1;

    Job createJob(long durationMs) {
        return null;
    }

    Job find(long id) {
        return null;
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  JobWorker performs the simulated job work on its own thread and updates
//  the Job state as it starts, completes, or is cancelled.
//
//  This class is provided, but you are expected to read it and understand how it
//  drives the Job lifecycle.
//
class JobWorker implements Runnable {
    private final Job job;

    JobWorker(Job job) {
        this.job = job;
    }

    @Override
    public void run() {
        job.markRunning();

        long remaining = job.durationMs();
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

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  JobState provides a small set of named states that describe a job's
//  lifecycle. Think about how JobState is used as work begins, completes,
//  or is cancelled.
//
enum JobState {
    QUEUED,
    RUNNING,
    DONE,
    CANCELLED
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  Job represents a unit of work. It should:
//  - Store id and duration.
//  - Track state transitions (QUEUED -> RUNNING -> DONE or CANCELLED).
//  - Track cancellation requests and report whether cancel succeeded.
//  - Decide which methods need synchronization for thread safety.
//
class Job {
    Job(long id, long durationMs) {
    }

    long id() {
        return 0;
    }

    long durationMs() {
        return 0;
    }

    JobState state() {
        return null;
    }

    boolean isCancelled() {
        return false;
    }

    void markRunning() {
    }

    void markDone() {
    }

    void markCancelled() {
    }

    boolean cancel() {
        return false;
    }
}
