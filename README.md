Java LAN File Sharer
====================

A desktop utility built in Java for simple, peer-to-peer file sharing on a local network.

Its key feature is **zero-configuration discovery** (ZeroConf) — it uses UDP broadcasting to automatically find other users, eliminating the need to manually find and type IP addresses.

🚀 Windows Install
---------------------------------------

The easiest way to use this application is with the official installer.

1.  Go to the [**Releases**](https://github.com/urveeshdadhich/JAVA-LAN-File-Sharer/releases) page for this project.
    
2.  Download the latest JavaLANFileSharer-Setup.exe.
    
3.  Run the installer. 
    

**Prerequisite:** You must have **Java (version 11 or newer)** installed on your machine.

✨ Features
----------

*   **Zero-Configuration Discovery:** Automatically finds servers on the network using UDP broadcasting. No more IP-hunting!
    
*   **Reliable File Transfer:** Uses TCP sockets to ensure every file is transferred completely and without corruption.
    
*   **Concurrent Server:** The server is multithreaded and can handle multiple client connections simultaneously.
    
*   **Simple GUI:** A clean, responsive graphical user interface built with Java Swing.
    
*   **Windows Installer:** An installer that handles setup and firewall configuration.
    

⚙️ How It Works: The Hybrid Protocol
------------------------------------

The application uses a hybrid protocol model, separating the "discovery" phase from the "transfer" phase.

### 1\. Discovery (using UDP)

1.  **Client:** When a user clicks "Refresh," the client broadcasts a single UDP packet (message: LFS\_DISCOVER\_REQUEST) to the entire network on port 6790.
    
2.  **Server:** The server constantly listens on UDP port 6790. When it hears a request, it replies _directly_ to that client's IP with a UDP packet containing its metadata (e.g., LFS\_DISCOVER\_RESPONSE|MyPC|report.pdf|2048KB).
    
3.  **Client:** The client collects all responses for 3 seconds, parses the metadata, and populates the list of available servers in the GUI.
    

### 2\. Transfer (using TCP)

1.  **Client:** Once a user selects a server from the list, the client initiates a standard TCP connection to the server's IP on port 6789.
    
2.  **Server:** The server, which is also listening on TCP port 6789, accepts this connection. It then spins off a new ClientHandler thread to manage this specific user.
    
3.  **Transfer:** The server streams the file data over the reliable TCP connection, and the client writes it to the local disk. This process is efficient as the file is never fully loaded into memory.
    

🛠️ Building from Source (Developer Guide)
------------------------------------------

If you want to contribute or build the project yourself, follow these steps.

### Prerequisites

*   **Java JDK (version 11 or newer):** Required to compile the code.
    
*   **Inno Setup:** Required to compile the installer script.
    

### 1\. Compile the Java Code

Open your terminal, navigate to the project directory, and run:

`   javac LanFileSharer.java   `

### 2\. Package the Application JAR

This command packages the .class file into a single, executable .jar file.

`   jar cfe LanFileSharer.jar LanFileSharer LanFileSharer.class icon.png   `

### 3\. Compile the Installer (Windows)

1.  Make sure you have LanFileSharer.jar and setup\_script.iss  in the same directory.
    
2.  Open setup\_script.iss with the Inno Setup Compiler.
    
3.  Click **Build > Compile**.
    
4.  This will generate your JavaLANFileSharer-Setup.exe in a new Output folder.
    

🛠️ Technologies Used
---------------------

*   **Java:** Core application logic.
    
*   **Java Networking (Sockets):**
    
    *   java.net.Socket & java.net.ServerSocket (for TCP file transfer).
        
    *   java.net.DatagramSocket & java.net.DatagramPacket (for UDP discovery).
        
*   **Java Swing:**
    
    *   JFrame, JDialog, JList, JButton, etc., for the graphical user interface.
        
*   **Java Concurrency (Multithreading):**
    
    *   java.lang.Thread to handle multiple clients and keep the GUI responsive.
        
    *   SwingUtilities.invokeLater to ensure thread safety when updating the GUI.
        
*   **Inno Setup:**
    
    *   For creating the Windows installer and managing firewall rules (netsh).
        

📄 License
----------

This project is licensed under the MIT License.





