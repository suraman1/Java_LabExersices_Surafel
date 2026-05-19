package server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.concurrent.ConcurrentHashMap;


public class ChatServer {

    static final int PORT = 5000;
    static final ConcurrentHashMap<String, ClientHandler> online = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        ServerSocket server = new ServerSocket(PORT);
        System.out.println("ChatApp server listening on port " + PORT);
        while (true) {
            Socket s = server.accept();
            new ClientHandler(s).start();
        }
    }

    static class ClientHandler extends Thread {

        final Socket socket;
        BufferedReader  in;
        PrintWriter     out;
        Connection      conn;

        String username;
        int    userId = -1;

        ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try {
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream(),  StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_app", "root", "");

                String line;
                while ((line = in.readLine()) != null) {
                    dispatch(line.trim());
                }
            } catch (Exception e) {
                System.err.println("[" + username + "] " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        void dispatch(String line) throws Exception {
            if (line.isEmpty()) return;
            String[] p = line.split("\\|", -1);
            switch (p[0]) {
                case "REGISTER"      -> handleRegister(p);
                case "LOGIN"         -> handleLogin(p);
                case "SEARCH"        -> handleSearch(p);
                case "GET_DM_LIST"   -> handleDmList();
                case "GET_GROUP_LIST"-> handleGroupList();
                case "GET_DIRECT"    -> handleGetDirect(p);
                case "GET_HISTORY"   -> handleGetHistory(p);
                case "CREATE_GROUP"  -> handleCreateGroup(p);
                case "ADD_MEMBER"    -> handleAddMember(p);
                case "MSG"           -> handleMsg(p);
                default              -> out.println("ERROR|Unknown command: " + p[0]);
            }
        }
        void handleRegister(String[] p) throws Exception {
            if (p.length < 3) {
                out.println("ERROR|Usage: REGISTER|username|hash");
                return;
            }
            String uname = p[1].trim();
            String hash = p[2].trim();
            try {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO users(username,password) VALUES(?,?)");
                ps.setString(1, uname);
                ps.setString(2, hash);
                ps.executeUpdate();
                out.println("OK|Registered");
            } catch (SQLIntegrityConstraintViolationException e) {
                out.println("ERROR|Username already taken");
            }
        }
        void handleLogin(String[] p) throws Exception {
            if (p.length < 3) { out.println("ERROR|Usage: LOGIN|username|hash"); return; }
            String uname = p[1].trim();
            String hash  = p[2].trim();

            PreparedStatement ps = conn.prepareStatement(
                    "SELECT user_id FROM users WHERE username=? AND password=?");
            ps.setString(1, uname);
            ps.setString(2, hash);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) { out.println("ERROR|Invalid credentials"); return; }

            username = uname;
            userId   = rs.getInt(1);
            online.put(username, this);
            out.println("OK|Welcome " + username);
        }


        void handleSearch(String[] p) throws Exception {
            if (!loggedIn()) return;
            String q = p.length > 1 ? p[1].trim() : "";
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT username FROM users WHERE username LIKE ? AND username != ? LIMIT 20");
            ps.setString(1, "%" + q + "%");
            ps.setString(2, username);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.println("SEARCH_RESULT|" + rs.getString(1));
            out.println("END_SEARCH");
        }

        void handleDmList() throws Exception {
            if (!loggedIn()) return;
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT dc.chat_id, " +
                            "       IF(dc.user_a=?, u2.username, u1.username) AS peer, " +
                            "       (SELECT body FROM messages WHERE scope='direct' AND scope_id=dc.chat_id " +
                            "        ORDER BY created_at DESC LIMIT 1) AS last_msg " +
                            "FROM direct_chats dc " +
                            "JOIN users u1 ON u1.user_id=dc.user_a " +
                            "JOIN users u2 ON u2.user_id=dc.user_b " +
                            "WHERE dc.user_a=? OR dc.user_b=? " +
                            "ORDER BY dc.created_at DESC");
            ps.setInt(1, userId); ps.setInt(2, userId); ps.setInt(3, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String last = rs.getString(3);
                out.println("DM|" + rs.getInt(1) + "|" + rs.getString(2) + "|" + (last == null ? "" : last.replace("|","\\|")));
            }
            out.println("END_DM_LIST");
        }

        void handleGroupList() throws Exception {
            if (!loggedIn()) return;
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT g.group_id, g.name FROM groups_ g " +
                            "JOIN group_members gm ON gm.group_id=g.group_id " +
                            "WHERE gm.user_id=?");
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.println("GROUP|" + rs.getInt(1) + "|" + rs.getString(2));
            out.println("END_GROUP_LIST");
        }

        void handleGetDirect(String[] p) throws Exception {
            if (!loggedIn()) return;
            if (p.length < 2) { out.println("ERROR|Missing username"); return; }
            String other   = p[1].trim();
            int    otherId = getUserId(other);
            if (otherId == -1) { out.println("ERROR|User not found: " + other); return; }

            int a = Math.min(userId, otherId);
            int b = Math.max(userId, otherId);

            PreparedStatement find = conn.prepareStatement(
                    "SELECT chat_id FROM direct_chats WHERE user_a=? AND user_b=?");
            find.setInt(1, a); find.setInt(2, b);
            ResultSet rs = find.executeQuery();
            if (rs.next()) { out.println("CHAT_ID|" + rs.getInt(1)); return; }

            PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO direct_chats(user_a,user_b) VALUES(?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ins.setInt(1, a); ins.setInt(2, b);
            ins.executeUpdate();
            ResultSet keys = ins.getGeneratedKeys();
            out.println("CHAT_ID|" + (keys.next() ? keys.getInt(1) : -1));
        }

        void handleGetHistory(String[] p) throws Exception {
            if (!loggedIn()) return;
            if (p.length < 3) { out.println("ERROR|Bad GET_HISTORY"); return; }
            String scope   = p[1];   // "direct" or "group"
            int    scopeId = Integer.parseInt(p[2]);

            PreparedStatement ps = conn.prepareStatement(
                    "SELECT u.username, m.body, m.created_at " +
                            "FROM messages m JOIN users u ON u.user_id=m.sender_id " +
                            "WHERE m.scope=? AND m.scope_id=? ORDER BY m.created_at ASC LIMIT 300");
            ps.setString(1, scope);
            ps.setInt(2, scopeId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String body = rs.getString(2).replace("|", "\\|");
                out.println("MSGH|" + rs.getString(1) + "|" + body + "|" + rs.getString(3));
            }
            out.println("END_HISTORY");
        }

        void handleCreateGroup(String[] p) throws Exception {
            if (!loggedIn()) return;
            if (p.length < 2) { out.println("ERROR|Missing group name"); return; }
            String name = p[1].trim();

            PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO groups_(name,owner_id) VALUES(?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ins.setString(1, name); ins.setInt(2, userId);
            ins.executeUpdate();
            ResultSet keys = ins.getGeneratedKeys();
            int gid = keys.next() ? keys.getInt(1) : -1;

            PreparedStatement mem = conn.prepareStatement(
                    "INSERT INTO group_members(group_id,user_id) VALUES(?,?)");
            mem.setInt(1, gid); mem.setInt(2, userId);
            mem.executeUpdate();

            out.println("GROUP_CREATED|" + gid + "|" + name);
        }

        void handleAddMember(String[] p) throws Exception {
            if (!loggedIn()) return;
            if (p.length < 3) { out.println("ERROR|Bad ADD_MEMBER"); return; }
            int    gid     = Integer.parseInt(p[1]);
            String newUser = p[2].trim();
            int    newUid  = getUserId(newUser);
            if (newUid == -1) { out.println("ERROR|User not found: " + newUser); return; }

            PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO group_members(group_id,user_id) VALUES(?,?)");
            ps.setInt(1, gid); ps.setInt(2, newUid);
            ps.executeUpdate();
            out.println("OK|Added " + newUser);

            PreparedStatement nameQ = conn.prepareStatement(
                    "SELECT name FROM groups_ WHERE group_id=?");
            nameQ.setInt(1, gid);
            ResultSet rs = nameQ.executeQuery();
            if (rs.next()) {
                pushTo(newUser, "GROUP_ADDED|" + gid + "|" + rs.getString(1));
            }
        }

        void handleMsg(String[] p) throws Exception {
            if (!loggedIn()) return;
            if (p.length < 4) { out.println("ERROR|Bad MSG"); return; }
            String scope   = p[1];           // "direct" or "group"
            int    scopeId = Integer.parseInt(p[2]);
            String text    = p[3];

            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO messages(scope,scope_id,sender_id,body,msg_type,created_at) VALUES(?,?,?,?,'text',NOW())");
            ps.setString(1, scope); ps.setInt(2, scopeId);
            ps.setInt(3, userId);   ps.setString(4, text);
            ps.executeUpdate();

            String push = "INCOMING_MSG|" + scope + "|" + scopeId + "|" + username + "|" + text.replace("|","\\|");

            if ("direct".equals(scope)) {
                PreparedStatement q = conn.prepareStatement(
                        "SELECT IF(user_a=?,u2.username,u1.username) " +
                                "FROM direct_chats dc JOIN users u1 ON u1.user_id=dc.user_a JOIN users u2 ON u2.user_id=dc.user_b " +
                                "WHERE dc.chat_id=?");
                q.setInt(1, userId); q.setInt(2, scopeId);
                ResultSet rs = q.executeQuery();
                if (rs.next()) pushTo(rs.getString(1), push);
                pushTo(username, push);  // echo to sender too

            } else {
                PreparedStatement q = conn.prepareStatement(
                        "SELECT u.username FROM group_members gm JOIN users u ON u.user_id=gm.user_id WHERE gm.group_id=?");
                q.setInt(1, scopeId);
                ResultSet rs = q.executeQuery();
                while (rs.next()) pushTo(rs.getString(1), push);
            }
        }

        void pushTo(String user, String msg) {
            ClientHandler h = online.get(user);
            if (h != null) h.out.println(msg);
        }

        int getUserId(String uname) throws Exception {
            PreparedStatement ps = conn.prepareStatement("SELECT user_id FROM users WHERE username=?");
            ps.setString(1, uname);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : -1;
        }

        boolean loggedIn() {
            if (username == null) { out.println("ERROR|Not logged in"); return false; }
            return true;
        }

        void cleanup() {
            if (username != null) online.remove(username, this);
            try { if (conn != null && !conn.isClosed()) conn.close(); } catch (Exception ignored) {}
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
}
