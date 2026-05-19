import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class Client {

    private Socket     socket;
    private PrintWriter out;
    private BufferedReader in;

    public String username;

    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();

    private Consumer<String> pushCallback;

    public void setPushCallback(Consumer<String> cb) { this.pushCallback = cb; }

    public void connect(String host, int port) throws Exception {
        socket = new Socket(host, port);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        Thread t = new Thread(this::listen, "listener");
        t.setDaemon(true);
        t.start();
    }

    public boolean register(String uname, String password) throws Exception {
        send("REGISTER|" + uname + "|" + sha256(password));
        return awaitOk();
    }

    public boolean login(String uname, String password) throws Exception {
        send("LOGIN|" + uname + "|" + sha256(password));
        if (awaitOk()) { username = uname; return true; }
        return false;
    }
    public List<String> search(String query) throws Exception {
        send("SEARCH|" + query);
        List<String> results = new ArrayList<>();
        String line;
        while (!(line = poll()).equals("END_SEARCH")) {
            if (line.startsWith("SEARCH_RESULT|")) results.add(line.substring(14));
        }
        return results;
    }
    public List<String[]> getDmList() throws Exception {
        send("GET_DM_LIST");
        List<String[]> list = new ArrayList<>();
        String line;
        while (!(line = poll()).equals("END_DM_LIST")) {
            if (line.startsWith("DM|")) {
                String[] p = line.split("\\|", 4);
                list.add(new String[]{p[1], p[2], p.length > 3 ? p[3] : ""});
            }
        }
        return list;
    }

    public List<String[]> getGroupList() throws Exception {
        send("GET_GROUP_LIST");
        List<String[]> list = new ArrayList<>();
        String line;
        while (!(line = poll()).equals("END_GROUP_LIST")) {
            if (line.startsWith("GROUP|")) {
                String[] p = line.split("\\|", 3);
                list.add(new String[]{p[1], p[2]});
            }
        }
        return list;
    }

    public int getOrCreateDirect(String otherUsername) throws Exception {
        send("GET_DIRECT|" + otherUsername);
        String r = poll();
        if (r.startsWith("CHAT_ID|")) return Integer.parseInt(r.split("\\|")[1]);
        return -1;
    }

    public List<String[]> getHistory(String scope, int scopeId) throws Exception {
        send("GET_HISTORY|" + scope + "|" + scopeId);
        List<String[]> list = new ArrayList<>();
        String line;
        while (!(line = poll()).equals("END_HISTORY")) {
            if (line.startsWith("MSGH|")) {
                String[] p = line.split("\\|", 4);
                list.add(new String[]{p[1], p[2].replace("\\|", "|"), p.length > 3 ? p[3] : ""});
            }
        }
        return list;
    }

    public int[] createGroup(String name) throws Exception {
        send("CREATE_GROUP|" + name);
        String r = poll();
        if (r.startsWith("GROUP_CREATED|")) {
            String[] p = r.split("\\|");
            return new int[]{Integer.parseInt(p[1])};
        }
        return new int[]{-1};
    }

    public void addMember(int groupId, String uname) throws Exception {
        send("ADD_MEMBER|" + groupId + "|" + uname);
        poll();
    }
    public void sendMessage(String scope, int scopeId, String text) {
        send("MSG|" + scope + "|" + scopeId + "|" + text.replace("|", "\\|"));
    }
    private void send(String line) {
        out.println(line);
    }

    private String poll() throws InterruptedException {
        String s = responseQueue.poll(6, TimeUnit.SECONDS);
        return s != null ? s : "ERROR|Timeout";
    }

    private boolean awaitOk() throws InterruptedException {
        String r = poll();
        return r.startsWith("OK|");
    }

    private void listen() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("INCOMING_MSG|") || line.startsWith("GROUP_CREATED|")) {
                    if (pushCallback != null) pushCallback.accept(line);
                } else {
                    responseQueue.put(line);
                }
            }
        } catch (Exception e) {
            System.err.println("Listener closed: " + e.getMessage());
        }
    }

    public void disconnect() {
        try { socket.close(); } catch (Exception ignored) {}
    }

    public static String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
