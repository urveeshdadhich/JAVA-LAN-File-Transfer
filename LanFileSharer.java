import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class LanFileSharer {

    private JFrame mainFrame;
    private JTextArea logArea;
    private File fileToSend; // --- DISCOVERY: Made a member variable to be accessible by discovery thread

    // --- DISCOVERY: We now have two ports. One for TCP (file) and one for UDP (discovery)
    private static final int TCP_PORT = 6789;
    private static final int DISCOVERY_PORT = 6790;

    // --- DISCOVERY: Special messages for our discovery protocol
    private static final String LFS_DISCOVER_REQUEST = "LFS_DISCOVER_REQUEST";
    private static final String LFS_DISCOVER_RESPONSE = "LFS_DISCOVER_RESPONSE";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LanFileSharer sharer = new LanFileSharer();
            String[] options = {"Send File (Server)", "Receive File (Client)"};
            int choice = JOptionPane.showOptionDialog(
                    null, "What would you like to do?", "Lan File Sharer",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0]
            );

            if (choice == 0) {
                sharer.createServerUI();
                sharer.promptForFileAndStartServer();
            } else if (choice == 1) {
                sharer.createClientUI();
                sharer.startClient(); // This will now open the discovery dialog
            } else {
                System.exit(0);
            }
        });
    }

    private void createUI(String title) {
        mainFrame = new JFrame(title);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(500, 300);
        mainFrame.setLocationRelativeTo(null);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBorder(new EmptyBorder(5, 5, 5, 5));
        JScrollPane scrollPane = new JScrollPane(logArea);

        mainFrame.add(scrollPane, BorderLayout.CENTER);
        mainFrame.setVisible(true);
        log("Application started.\n");
    }

    private void createServerUI() {
        createUI("LAN File Sharer (Server)");
    }

    private void createClientUI() {
        createUI("LAN File Sharer (Client)");
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
        System.out.print(message + "\n");
    }

    // ========================================================================
    // SERVER LOGIC (Sender)
    // ========================================================================

    private void promptForFileAndStartServer() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select a file to send");
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));

        if (fileChooser.showOpenDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
            // --- DISCOVERY: Set the member variable
            this.fileToSend = fileChooser.getSelectedFile();
            startServer();
        } else {
            log("No file selected. Server not started.");
            mainFrame.dispose();
        }
    }

    private void startServer() {
        if (!fileToSend.exists() || fileToSend.isDirectory()) {
            log("Error: File not found or is a directory: " + fileToSend.getAbsolutePath());
            return;
        }

        // --- DISCOVERY: Start the discovery responder thread
        new Thread(new DiscoveryResponderThread(fileToSend)).start();

        // Start the TCP server thread for file transfer
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
                log("Server started. Hosting: " + fileToSend.getName());

                String ip = InetAddress.getLocalHost().getHostAddress();
                log("Waiting for a client on IP " + ip + " (Port " + TCP_PORT + ")...");

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    log("TCP Client connected from " + clientSocket.getInetAddress().getHostAddress());
                    new Thread(new ClientHandler(clientSocket, fileToSend)).start();
                }
            } catch (IOException e) {
                log("Server Error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * --- DISCOVERY: NEW THREAD ---
     * This thread listens for UDP broadcasts and replies with server info.
     */
    private class DiscoveryResponderThread implements Runnable {
        private File fileToSend;
        private String hostName;

        public DiscoveryResponderThread(File file) {
            this.fileToSend = file;
            try {
                // Get the computer's name to be user-friendly
                this.hostName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                this.hostName = "UnknownServer";
            }
        }

        @Override
        public void run() {
            try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT, InetAddress.getByName("0.0.0.0"))) {
                socket.setBroadcast(true); // Not strictly needed for receiving, but good practice
                log("Discovery service started on UDP port " + DISCOVERY_PORT);
                byte[] receiveBuffer = new byte[1024];

                while (true) { // Listen forever
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    socket.receive(receivePacket); // Block and wait

                    String message = new String(receivePacket.getData(), 0, receivePacket.getLength());

                    // If we get the right request message
                    if (message.equals(LFS_DISCOVER_REQUEST)) {
                        log("Discovery request from " + receivePacket.getAddress().getHostAddress());

                        // Build our reply: "RESPONSE|ServerName|FileName|FileSize"
                        String responseMsg = LFS_DISCOVER_RESPONSE + "|" + hostName + "|" + fileToSend.getName() + "|" + fileToSend.length();
                        byte[] sendBuffer = responseMsg.getBytes();

                        // Send the reply *directly* back to the client that asked
                        DatagramPacket sendPacket = new DatagramPacket(
                                sendBuffer,
                                sendBuffer.length,
                                receivePacket.getAddress(),  // Send back to sender's IP
                                receivePacket.getPort()      // Send back to sender's Port
                        );
                        socket.send(sendPacket);
                    }
                }
            } catch (IOException e) {
                log("Discovery responder error: " + e.getMessage());
            }
        }
    }


    /**
     * Thread to handle sending the file to a connected client (Unchanged).
     */
    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private File fileToSend;

        public ClientHandler(Socket socket, File file) {
            this.clientSocket = socket;
            this.fileToSend = file;
        }

        @Override
        public void run() {
            try (DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
                 FileInputStream fis = new FileInputStream(fileToSend)) {
                log("Sending file details...");
                dos.writeUTF(fileToSend.getName());
                dos.writeLong(fileToSend.length());
                log("Sending file data (" + fileToSend.length() + " bytes)...");
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
                dos.flush();
                log("File sent successfully.");
            } catch (SocketException e) {
                log("Error sending file: " + e.getMessage());
            } catch (IOException e) {
                log("File transfer error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) { /* ignore */ }
                log("Client disconnected.");
            }
        }
    }


    // ========================================================================
    // CLIENT LOGIC (Receiver)
    // ========================================================================

    /**
     * --- DISCOVERY: NEW CLASS ---
     * This class holds info about a discovered server.
     */
    private static class ServerInfo {
        String ip;
        String serverName;
        String fileName;
        long fileSize;

        public ServerInfo(String ip, String serverName, String fileName, long fileSize) {
            this.ip = ip;
            this.serverName = serverName;
            this.fileName = fileName;
            this.fileSize = fileSize;
        }

        /**
         * This is the text that will appear in the JList.
         */
        @Override
        public String toString() {
            String size;
            if (fileSize > 1024 * 1024) {
                size = (fileSize / (1024 * 1024)) + " MB";
            } else {
                size = (fileSize / 1024) + " KB";
            }
            return "<html><b>" + serverName + "</b> (" + ip + ")<br> &nbsp; <i>Hosting: " + fileName + " (" + size + ")</i></html>";
        }
    }

    /**
     * --- DISCOVERY: MODIFIED ---
     * Replaces the IP input box with a discovery dialog.
     */
    private void startClient() {
        showDiscoveryDialog();
    }

    /**
     * --- DISCOVERY: NEW METHOD ---
     * Shows a modal dialog that searches for servers on the network.
     */
    private void showDiscoveryDialog() {
        JDialog dialog = new JDialog(mainFrame, "Discover Servers", true);
        dialog.setSize(450, 300);
        dialog.setLocationRelativeTo(mainFrame);
        dialog.setLayout(new BorderLayout());

        DefaultListModel<ServerInfo> listModel = new DefaultListModel<>();
        JList<ServerInfo> serverList = new JList<>(listModel);
        serverList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        serverList.setBorder(new EmptyBorder(5, 5, 5, 5));

        // Use a map to prevent duplicate entries from the same IP
        Map<String, ServerInfo> discoveredServers = new HashMap<>();

        JScrollPane scrollPane = new JScrollPane(serverList);

        JPanel buttonPanel = new JPanel();
        JButton refreshButton = new JButton("Refresh");
        JButton connectButton = new JButton("Connect");
        connectButton.setEnabled(false); // Disabled until an item is selected

        buttonPanel.add(refreshButton);
        buttonPanel.add(connectButton);

        JLabel statusLabel = new JLabel("Click 'Refresh' to search for servers.");
        statusLabel.setBorder(new EmptyBorder(5, 5, 5, 5));

        dialog.add(statusLabel, BorderLayout.NORTH);
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        serverList.addListSelectionListener(e -> {
            connectButton.setEnabled(serverList.getSelectedIndex() != -1);
        });

        refreshButton.addActionListener(e -> {
            // Run discovery in a new thread so the UI doesn't freeze
            new Thread(() -> {
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setBroadcast(true);
                    socket.setSoTimeout(3000); // Listen for replies for 3 seconds

                    byte[] sendBuffer = LFS_DISCOVER_REQUEST.getBytes();

                    // Broadcast to 255.255.255.255
                    InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
                    DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, broadcastAddress, DISCOVERY_PORT);

                    SwingUtilities.invokeLater(() -> {
                        listModel.clear();
                        discoveredServers.clear();
                        statusLabel.setText("Searching for servers...");
                        connectButton.setEnabled(false);
                    });

                    socket.send(sendPacket);

                    // Now, listen for replies until the timeout
                    byte[] receiveBuffer = new byte[1024];
                    while (true) {
                        try {
                            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                            socket.receive(receivePacket);

                            String message = new String(receivePacket.getData(), 0, receivePacket.getLength());

                            // Check if it's our valid response
                            if (message.startsWith(LFS_DISCOVER_RESPONSE)) {
                                String[] parts = message.split("\\|");
                                if (parts.length == 4) {
                                    String ip = receivePacket.getAddress().getHostAddress();
                                    String serverName = parts[1];
                                    String fileName = parts[2];
                                    long fileSize = Long.parseLong(parts[3]);
                                    ServerInfo info = new ServerInfo(ip, serverName, fileName, fileSize);

                                    // Add to map and list (on UI thread) if it's new
                                    if (!discoveredServers.containsKey(ip)) {
                                        discoveredServers.put(ip, info);
                                        SwingUtilities.invokeLater(() -> listModel.addElement(info));
                                    }
                                }
                            }
                        } catch (SocketTimeoutException ex) {
                            // This is expected. Our 3-second search is over.
                            break;
                        }
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (listModel.isEmpty()) {
                            statusLabel.setText("No servers found. Try refreshing.");
                        } else {
                            statusLabel.setText("Search complete. Select a server.");
                        }
                    });

                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Discovery error: " + ex.getMessage()));
                    ex.printStackTrace();
                }
            }).start();
        });

        connectButton.addActionListener(e -> {
            ServerInfo selected = serverList.getSelectedValue();
            if (selected != null) {
                dialog.dispose(); // Close discovery dialog
                // Call the *original* file receive logic
                connectAndReceiveFile(selected);
            } else {
                JOptionPane.showMessageDialog(dialog, "Please select a server.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        dialog.setVisible(true); // Show the modal dialog
    }


    /**
     * --- DISCOVERY: MODIFIED ---
     * This is the original client logic, now extracted into its own method.
     * It now takes a ServerInfo object instead of just an IP string.
     */
    private void connectAndReceiveFile(ServerInfo server) {
        String serverIp = server.ip;

        // Start the client in a new thread to keep the UI responsive
        new Thread(() -> {
            try (Socket socket = new Socket(serverIp, TCP_PORT);
                 DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                log("Connected to server: " + server.serverName + " at " + serverIp);

                // 1. Read file details (we already have them, but we read to sync the stream)
                String fileNameOnServer = dis.readUTF();
                long fileSizeOnServer = dis.readLong();
                log("Receiving file: " + fileNameOnServer + " (" + fileSizeOnServer + " bytes)");

                // 2. Ask user where to save
                JFileChooser fileChooser = new JFileChooser();
                String homeDir = System.getProperty("user.home");
                File downloadsDir = new File(homeDir + File.separator + "Downloads");

                if (downloadsDir.exists() && downloadsDir.isDirectory()) {
                    fileChooser.setCurrentDirectory(downloadsDir);
                } else {
                    fileChooser.setCurrentDirectory(new File(homeDir));
                }

                fileChooser.setSelectedFile(new File(fileNameOnServer));

                if (fileChooser.showSaveDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
                    File fileToSave = fileChooser.getSelectedFile();

                    try (FileOutputStream fos = new FileOutputStream(fileToSave)) {
                        log("Saving file to: " + fileToSave.getAbsolutePath());

                        // 3. Read file data and write to disk
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long totalRead = 0;

                        while (totalRead < fileSizeOnServer && (bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSizeOnServer - totalRead))) != -1) {
                            fos.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                        }

                        fos.flush();
                        log("File received successfully.");
                    }
                } else {
                    log("User cancelled save operation.");
                }
            } catch (FileNotFoundException e) {
                log("Error receiving file: " + e.getMessage());
                e.printStackTrace();
            }
            catch (UnknownHostException e) {
                log("Error: Unknown host " + serverIp);
            } catch (IOException e) {
                log("Client Error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                log("Disconnected from server.");
            }
        }).start();
    }
}