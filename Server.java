import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Server {
    private static final int THREAD_POOL_SIZE = 10;
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static long startTimeMillis;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int port = promptForPort(scanner);
        startTimeMillis = System.currentTimeMillis();

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            System.out.println("Server started on port " + port);
            System.out.println("Waiting for client connections...");

            while (running.get()) {
                try {
                    Socket socket = serverSocket.accept();
                    pool.execute(new ClientHandler(socket));
                } catch (SocketException e) {
                    if (running.get()) {
                        System.out.println("Socket error: " + e.getMessage());
                    }
                } catch (IOException e) {
                    System.out.println("Error accepting connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to start server: " + e.getMessage());
        } finally {
            pool.shutdown();
            scanner.close();
            System.out.println("Server stopped.");
        }
    }

    private static int promptForPort(Scanner scanner) {
        while (true) {
            System.out.print("Enter port number to listen on (1024-65535): ");
            String input = scanner.nextLine().trim();

            try {
                int port = Integer.parseInt(input);
                if (port >= 1024 && port <= 65535) {
                    return port;
                }
            } catch (NumberFormatException ignored) {
            }

            System.out.println("Invalid port. Please enter a number between 1024 and 65535.");
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            String clientInfo = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
            System.out.println("Connected: " + clientInfo);

            try (
                Socket autoCloseSocket = socket;
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(autoCloseSocket.getInputStream(), StandardCharsets.UTF_8)
                );
                PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(autoCloseSocket.getOutputStream(), StandardCharsets.UTF_8),
                    true
                )
            ) {
                autoCloseSocket.setSoTimeout(10000);

                String request = reader.readLine();
                if (request == null || request.isBlank()) {
                    writer.println("ERROR: Empty request.");
                    return;
                }

                String response = processRequest(request.trim().toUpperCase());
                writer.println(response);

            } catch (SocketTimeoutException e) {
                System.out.println("Client timed out: " + clientInfo);
            } catch (IOException e) {
                System.out.println("I/O error with client " + clientInfo + ": " + e.getMessage());
            } finally {
                System.out.println("Disconnected: " + clientInfo);
            }
        }

        private String processRequest(String request) {
            return switch (request) {
                case "DATETIME" -> getDateTime();
                case "UPTIME" -> getUptime();
                case "MEMORY" -> getMemoryUsage();
                case "NETSTAT" -> runSystemCommand(getNetstatCommand());
                case "USERS" -> runSystemCommand(getCurrentUsersCommand());
                case "PROCESSES" -> runSystemCommand(getProcessesCommand());
                case "EXIT" -> "Server remains running. Client disconnected.";
                case "SHUTDOWN" -> {
                    running.set(false);
                    yield "Server shutdown requested.";
                }
                default -> "ERROR: Unsupported command.";
            };
        }

        private String getDateTime() {
            return "Current date/time: " + LocalDateTime.now();
        }

        private String getUptime() {
            long uptimeMillis = System.currentTimeMillis() - startTimeMillis;
            long seconds = uptimeMillis / 1000;
            return "Server uptime: " + seconds + " seconds";
        }

        private String getMemoryUsage() {
            Runtime runtime = Runtime.getRuntime();
            long total = runtime.totalMemory();
            long free = runtime.freeMemory();
            long used = total - free;

            return "Memory usage:\n" +
                   "Used: " + used + " bytes\n" +
                   "Free: " + free + " bytes\n" +
                   "Total: " + total + " bytes";
        }

        private String runSystemCommand(String[] command) {
            if (command == null) {
                return "ERROR: Command not supported on this operating system.";
            }

            List<String> output = new ArrayList<>();

            try {
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectErrorStream(true);
                Process process = builder.start();

                try (BufferedReader processReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = processReader.readLine()) != null) {
                        output.add(line);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    return "Command failed with exit code " + exitCode;
                }

                if (output.isEmpty()) {
                    return "Command executed successfully, but no output was returned.";
                }

                return String.join("\n", output);

            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                return "ERROR running system command: " + e.getMessage();
            }
        }

        private String[] getNetstatCommand() {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) return new String[]{"netstat"};
            if (os.contains("nix") || os.contains("nux") || os.contains("mac")) return new String[]{"netstat"};
            return null;
        }

        private String[] getCurrentUsersCommand() {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) return new String[]{"query", "user"};
            if (os.contains("nix") || os.contains("nux") || os.contains("mac")) return new String[]{"who"};
            return null;
        }

        private String[] getProcessesCommand() {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) return new String[]{"tasklist"};
            if (os.contains("nix") || os.contains("nux") || os.contains("mac")) return new String[]{"ps", "-ef"};
            return null;
        }
    }
}