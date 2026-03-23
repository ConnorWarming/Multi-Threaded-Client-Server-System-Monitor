import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Client {
    private static final String[] MENU_OPTIONS = {
        "1. Date and Time",
        "2. Uptime",
        "3. Memory Use",
        "4. Netstat",
        "5. Current Users",
        "6. Running Processes",
        "7. Exit Client"
    };

    private static final int[] REQUEST_COUNTS = {1, 5, 10, 15, 20, 25, 50};

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        String serverAddress = promptForAddress(scanner);
        int serverPort = promptForPort(scanner);

        boolean running = true;

        while (running) {
            printMenu();
            int operation = promptForMenuChoice(scanner, 1, 7);

            if (operation == 7) {
                System.out.println("Client closed.");
                running = false;
                continue;
            }

            printRequestCounts();
            int requestChoice = promptForMenuChoice(scanner, 1, REQUEST_COUNTS.length);
            int totalRequests = REQUEST_COUNTS[requestChoice - 1];

            String command = mapOperationToCommand(operation);

            List<RequestThread> requestThreads = new ArrayList<>();

            System.out.println("\nSending " + totalRequests + " request(s) to the server...\n");

            for (int i = 0; i < totalRequests; i++) {
                RequestThread thread = new RequestThread(serverAddress, serverPort, command, i + 1);
                requestThreads.add(thread);
                thread.start();
            }

            long totalTurnaroundTime = 0;
            int completedRequests = 0;

            for (RequestThread thread : requestThreads) {
                try {
                    thread.join();
                    if (thread.wasSuccessful()) {
                        totalTurnaroundTime += thread.getTurnaroundTime();
                        completedRequests++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Thread interrupted.");
                }
            }

            System.out.println("\n========== PERFORMANCE SUMMARY ==========");
            System.out.println("Completed requests: " + completedRequests + "/" + totalRequests);

            if (completedRequests > 0) {
                double average = (double) totalTurnaroundTime / completedRequests;
                System.out.println("Total turnaround time: " + totalTurnaroundTime + " ms");
                System.out.printf("Average turnaround time: %.2f ms%n", average);
            } else {
                System.out.println("No successful requests completed.");
            }
            System.out.println("=========================================\n");
        }

        scanner.close();
    }

    private static void printMenu() {
        System.out.println("========== CLIENT MENU ==========");
        for (String option : MENU_OPTIONS) {
            System.out.println(option);
        }
    }

    private static void printRequestCounts() {
        System.out.println("\nSelect number of requests:");
        for (int i = 0; i < REQUEST_COUNTS.length; i++) {
            System.out.println((i + 1) + ". " + REQUEST_COUNTS[i]);
        }
    }

    private static String promptForAddress(Scanner scanner) {
        while (true) {
            System.out.print("Enter server address: ");
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println("Server address cannot be empty.");
        }
    }

    private static int promptForPort(Scanner scanner) {
        while (true) {
            System.out.print("Enter server port: ");
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

    private static int promptForMenuChoice(Scanner scanner, int min, int max) {
        while (true) {
            System.out.print("Enter your choice: ");
            String input = scanner.nextLine().trim();

            try {
                int choice = Integer.parseInt(input);
                if (choice >= min && choice <= max) {
                    return choice;
                }
            } catch (NumberFormatException ignored) {
            }

            System.out.println("Invalid selection. Please choose a number between " + min + " and " + max + ".");
        }
    }

    private static String mapOperationToCommand(int operation) {
        return switch (operation) {
            case 1 -> "DATETIME";
            case 2 -> "UPTIME";
            case 3 -> "MEMORY";
            case 4 -> "NETSTAT";
            case 5 -> "USERS";
            case 6 -> "PROCESSES";
            default -> "EXIT";
        };
    }

    private static class RequestThread extends Thread {
        private final String serverAddress;
        private final int serverPort;
        private final String command;
        private final int requestNumber;

        private long turnaroundTime;
        private boolean successful;

        public RequestThread(String serverAddress, int serverPort, String command, int requestNumber) {
            this.serverAddress = serverAddress;
            this.serverPort = serverPort;
            this.command = command;
            this.requestNumber = requestNumber;
            this.successful = false;
        }

        public long getTurnaroundTime() {
            return turnaroundTime;
        }

        public boolean wasSuccessful() {
            return successful;
        }

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();

            try (
                Socket socket = new Socket()
            ) {
                socket.connect(new InetSocketAddress(serverAddress, serverPort), 5000);
                socket.setSoTimeout(10000);

                try (
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                    );
                    PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                        true
                    )
                ) {
                    writer.println(command);

                    StringBuilder responseBuilder = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line).append(System.lineSeparator());
                    }

                    long endTime = System.currentTimeMillis();
                    turnaroundTime = endTime - startTime;
                    successful = true;

                    synchronized (System.out) {
                        System.out.println("----- Response for Request #" + requestNumber + " -----");
                        System.out.println("Command: " + command);
                        System.out.println(responseBuilder.toString().trim());
                        System.out.println("Turnaround Time: " + turnaroundTime + " ms");
                        System.out.println("-------------------------------------------\n");
                    }
                }

            } catch (SocketTimeoutException e) {
                synchronized (System.out) {
                    System.out.println("Request #" + requestNumber + " timed out.");
                }
            } catch (IOException e) {
                synchronized (System.out) {
                    System.out.println("Request #" + requestNumber + " failed: " + e.getMessage());
                }
            }
        }
    }
}