
package server;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {

    static final int PORT = 5000;
    static Map<String, List<PrintWriter>> rooms = new ConcurrentHashMap<>();
    static Connection conn;

    public static void main(String[] args) throws Exception {
        conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/chat_app", "root", ""
        );

        ServerSocket server = new ServerSocket(PORT);
        System.out.println("Server running on port " + PORT);

        while (true) {
            Socket s = server.accept();
            new Thread(() -> handle(s)).start();
        }
    }

    static void handle(Socket socket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String currentGroup = "Software";
            joinRoom(currentGroup, out);
            sendHistory(currentGroup, out);

            String line;
            while ((line = in.readLine()) != null) {

                String[] p = line.split("\\|", 5);
                if (p.length < 3) continue;

                String group = p[0];
                String user  = p[1];
                String type  = p[2];
                currentGroup = group;

                if (type.equals("TEXT")) {
                    String msg = p.length > 3 ? p[3] : "";
                    save(group, user, "TEXT", msg, null, null);
                    broadcast(group, user + "|TEXT|" + msg);
                } else if (type.equals("FILE")) {
                    String fileName = p.length > 3 ? p[3] : "file";
                    String fileData = p.length > 4 ? p[4] : "";
                    save(group, user, "FILE", null, fileName, fileData);
                    broadcast(group, user + "|FILE|" + fileName + "|" + fileData);
                }
            }
        } catch (Exception e) {
            System.out.println("Client disconnected");
        }
    }

    static void joinRoom(String group, PrintWriter out) {
        rooms.computeIfAbsent(group, k -> new CopyOnWriteArrayList<>()).add(out);
    }

    static void broadcast(String group, String msg) {
        List<PrintWriter> list = rooms.get(group);
        if (list == null) return;
        for (PrintWriter out : list) out.println(msg);
    }

    static void save(String group, String user, String type, String msg, String fileName, String fileData) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO messages(group_name, username, content, type, file_name, file_data) VALUES(?,?,?,?,?,?)"
            );
            ps.setString(1, group);
            ps.setString(2, user);
            ps.setString(3, msg != null ? msg : "");
            ps.setString(4, type);
            ps.setString(5, fileName);
            ps.setString(6, fileData);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void sendHistory(String group, PrintWriter out) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT username, type, content, file_name, file_data FROM messages WHERE group_name=? ORDER BY id ASC"
            );
            ps.setString(1, group);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String user     = rs.getString(1);
                String type     = rs.getString(2);
                String content  = rs.getString(3);
                String fileName = rs.getString(4);

String fileData = rs.getString(5);
                if ("FILE".equals(type)) {
                    out.println(user + "|FILE|" + fileName + "|" + fileData);
                } else {
                    out.println(user + "|TEXT|" + content);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}