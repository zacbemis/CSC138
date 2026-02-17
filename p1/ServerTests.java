import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ServerTests {
    private static int testsRun = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) throws Exception {
        testServerConfig();
        testClientHandlerProtocol();
        testJobAndWorker();

        System.out.println("Tests run: " + testsRun + ", failures: " + testsFailed);
        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Simple assertion helpers
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static void assertTrue(boolean condition, String message) {
        testsRun++;
        if (!condition) {
            testsFailed++;
            System.err.println("FAIL: " + message);
        }
    }

    private static void assertEquals(String expected, String actual, String message) {
        testsRun++;
        if ((expected == null && actual != null) ||
                (expected != null && !expected.equals(actual))) {
            testsFailed++;
            System.err.println("FAIL: " + message + " (expected=\"" + expected + "\" actual=\"" + actual + "\")");
        }
    }

    private static void assertEquals(long expected, long actual, String message) {
        testsRun++;
        if (expected != actual) {
            testsFailed++;
            System.err.println("FAIL: " + message + " (expected=" + expected + " actual=" + actual + ")");
        }
    }

    private static void assertNotNull(Object value, String message) {
        testsRun++;
        if (value == null) {
            testsFailed++;
            System.err.println("FAIL: " + message + " (was null)");
        }
    }

    private static void assertNull(Object value, String message) {
        testsRun++;
        if (value != null) {
            testsFailed++;
            System.err.println("FAIL: " + message + " (expected null, got " + value + ")");
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Tests for ServerConfig.fromArgs
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static void testServerConfig() {
        // No args
        ServerConfig cfg1 = ServerConfig.fromArgs(new String[] {});
        assertNull(cfg1, "ServerConfig.fromArgs should return null when no args are provided");

        // Just a port
        ServerConfig cfg2 = ServerConfig.fromArgs(new String[] { "8080" });
        assertNotNull(cfg2, "ServerConfig.fromArgs should parse a single port argument");
        if (cfg2 != null) {
            assertEquals(8080L, cfg2.port, "ServerConfig.port should match the provided port");
            assertTrue(!cfg2.verbose, "ServerConfig.verbose should default to false without -v");
        }

        // -v and a port
        ServerConfig cfg3 = ServerConfig.fromArgs(new String[] { "-v", "9090" });
        assertNotNull(cfg3, "ServerConfig.fromArgs should parse -v <port>");
        if (cfg3 != null) {
            assertEquals(9090L, cfg3.port, "ServerConfig.port should parse after -v");
            assertTrue(cfg3.verbose, "ServerConfig.verbose should be true when -v is present");
        }

        // -v without a port
        ServerConfig cfg4 = ServerConfig.fromArgs(new String[] { "-v" });
        assertNull(cfg4, "ServerConfig.fromArgs should reject -v without a port");

        // Bad port
        ServerConfig cfg5 = ServerConfig.fromArgs(new String[] { "not-a-port" });
        assertNull(cfg5, "ServerConfig.fromArgs should reject a non-numeric port");
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Tests for ClientHandler protocol behavior using a fake Socket
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static void testClientHandlerProtocol() throws Exception {
        testSubmitAndQuit();
        testSubmitBadMs();
        testEmptyLine();
        testUnknownCommand();
        testStatusUnknownId();
        testCancelUnknownId();
    }

    private static void testSubmitAndQuit() throws Exception {
        JobRegistry registry = new JobRegistry();
        ServerConfig config = ServerConfig.fromArgs(new String[] { "1234" });
        assertNotNull(config, "ServerConfig should parse a basic port for ClientHandler tests");

        FakeSocket socket = new FakeSocket("SUBMIT 50\nQUIT\n");
        ClientHandler handler = new ClientHandler(socket, registry, config);
        handler.run();

        String[] lines = linesOf(socket.getOutputAsString());
        assertTrue(lines.length >= 2, "SUBMIT + QUIT should produce at least two response lines");
        if (lines.length >= 2) {
            assertTrue(lines[0].startsWith("JOB "), "First response to SUBMIT should start with \"JOB \"");
            assertEquals("BYE", lines[1], "Response to QUIT should be BYE");
        }
    }

    private static void testSubmitBadMs() throws Exception {
        JobRegistry registry = new JobRegistry();
        ServerConfig config = ServerConfig.fromArgs(new String[] { "1234" });
        FakeSocket socket = new FakeSocket("SUBMIT -10\nQUIT\n");
        ClientHandler handler = new ClientHandler(socket, registry, config);
        handler.run();

        String[] lines = linesOf(socket.getOutputAsString());
        assertTrue(lines.length >= 2, "Bad SUBMIT + QUIT should produce at least two response lines");
        if (lines.length >= 2) {
            assertEquals("ERR BAD_MS", lines[0], "Negative ms should be rejected with ERR BAD_MS");
            assertEquals("BYE", lines[1], "QUIT should still return BYE after an error");
        }
    }

    private static void testEmptyLine() throws Exception {
        JobRegistry registry = new JobRegistry();
        ServerConfig config = ServerConfig.fromArgs(new String[] { "1234" });
        FakeSocket socket = new FakeSocket("\nQUIT\n");
        ClientHandler handler = new ClientHandler(socket, registry, config);
        handler.run();

        String[] lines = linesOf(socket.getOutputAsString());
        assertTrue(lines.length >= 2, "Empty line + QUIT should produce at least two response lines");
        if (lines.length >= 2) {
            assertEquals("ERR EMPTY", lines[0], "Empty line should produce ERR EMPTY");
            assertEquals("BYE", lines[1], "QUIT should return BYE");
        }
    }

    private static void testUnknownCommand() throws Exception {
        JobRegistry registry = new JobRegistry();
        ServerConfig config = ServerConfig.fromArgs(new String[] { "1234" });
        FakeSocket socket = new FakeSocket("FOO\nQUIT\n");
        ClientHandler handler = new ClientHandler(socket, registry, config);
        handler.run();

        String[] lines = linesOf(socket.getOutputAsString());
        assertTrue(lines.length >= 2, "Unknown command + QUIT should produce at least two response lines");
        if (lines.length >= 2) {
            assertEquals("ERR UNKNOWN_COMMAND", lines[0], "Unknown command should produce ERR UNKNOWN_COMMAND");
            assertEquals("BYE", lines[1], "QUIT should return BYE");
        }
    }

    private static void testStatusUnknownId() throws Exception {
        JobRegistry registry = new JobRegistry();
        ServerConfig config = ServerConfig.fromArgs(new String[] { "1234" });
        FakeSocket socket = new FakeSocket("STATUS 999\nQUIT\n");
        ClientHandler handler = new ClientHandler(socket, registry, config);
        handler.run();

        String[] lines = linesOf(socket.getOutputAsString());
        assertTrue(lines.length >= 2, "STATUS unknown id + QUIT should produce at least two response lines");
        if (lines.length >= 2) {
            assertEquals("STATUS 999 UNKNOWN", lines[0], "Unknown id should result in STATUS <id> UNKNOWN");
            assertEquals("BYE", lines[1], "QUIT should return BYE");
        }
    }

    private static void testCancelUnknownId() throws Exception {
        JobRegistry registry = new JobRegistry();
        ServerConfig config = ServerConfig.fromArgs(new String[] { "1234" });
        FakeSocket socket = new FakeSocket("CANCEL 123\nQUIT\n");
        ClientHandler handler = new ClientHandler(socket, registry, config);
        handler.run();

        String[] lines = linesOf(socket.getOutputAsString());
        assertTrue(lines.length >= 2, "CANCEL unknown id + QUIT should produce at least two response lines");
        if (lines.length >= 2) {
            assertEquals("NOTCANCELLED 123 UNKNOWN", lines[0],
                    "Cancelling an unknown id should result in NOTCANCELLED <id> UNKNOWN");
            assertEquals("BYE", lines[1], "QUIT should return BYE");
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Tests for Job and JobWorker behavior
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static void testJobAndWorker() throws Exception {
        testJobCompletes();
        testJobCancellation();
    }

    private static void testJobCompletes() throws Exception {
        Job job = new Job(1L, 50L);
        Thread t = new Thread(new JobWorker(job));
        t.start();
        t.join(500L); // plenty of time for a 50ms job

        JobState state = job.getState();
        assertTrue(state == JobState.DONE || state == JobState.CANCELLED,
                "Job should end in a terminal state (DONE or CANCELLED)");
    }

    private static void testJobCancellation() throws Exception {
        Job job = new Job(2L, 200L);
        Thread t = new Thread(new JobWorker(job));
        t.start();

        // Give the worker a moment to start running
        Thread.sleep(30L);

        boolean cancelled = job.cancel();
        assertTrue(cancelled, "Cancelling a running/queued job should return true");

        t.join(1000L);
        JobState state = job.getState();
        assertEquals(JobState.CANCELLED.name(), state.name(), "Cancelled job should end in CANCELLED state");
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Helpers
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static String[] linesOf(String s) {
        String normalized = s.replace("\r\n", "\n");
        if (normalized.isEmpty()) {
            return new String[0];
        }
        return normalized.split("\n");
    }

    /**
     * A very small fake Socket implementation that feeds pre-defined input to
     * ClientHandler and captures all output written by the handler.
     */
    private static class FakeSocket extends Socket {
        private final InputStream in;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        FakeSocket(String inputScript) {
            this.in = new ByteArrayInputStream(inputScript.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return in;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return out;
        }

        @Override
        public synchronized void close() throws IOException {
            // No-op. Streams are owned by this fake socket and will be closed
            // when the handler's writers/readers are closed.
        }

        String getOutputAsString() {
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}

