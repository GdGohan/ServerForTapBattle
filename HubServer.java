import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class HubServer {

    private static final ConcurrentHashMap<String, Socket> clients   = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Socket> receptors = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = 8080;
        System.out.println("HubServer iniciado na porta " + port);

        ServerSocket server = new ServerSocket(port);

        while (true) {
            Socket socket = server.accept();
            new Thread(() -> handle(socket)).start();
        }
    }

    private static void handle(Socket socket) {
        String role = null;
        String room = null;

        try {
            socket.setSoTimeout(30000);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // Handshake
            String command = in.readUTF();
            String[] parts = command.split(";");

            for (String p : parts) {
                if (p.startsWith("ROLE:")) role = p.substring(5);
                if (p.startsWith("ROOM:")) room = p.substring(5);
            }

            if (role == null || room == null) {
                out.writeUTF("REJECT");
                socket.close();
                return;
            }

            if (role.equals("RECEPTOR")) {

                Socket old = receptors.get(room);
                if (old != null && old.isConnected() && !old.isClosed()) {
                    out.writeUTF("ROOM_EXISTS");
                    socket.close();
                    return;
                }

                receptors.put(room, socket);
            }
            else if (role.equals("CLIENT")) {

                Socket old = clients.get(room);
                if (old != null && old.isConnected() && !old.isClosed()) {
                    out.writeUTF("ROOM_FULL");
                    socket.close();
                    return;
                }

                clients.put(room, socket);
            }
            else {
                out.writeUTF("REJECT");
                socket.close();
                return;
            }

            Socket other = role.equals("CLIENT")
                    ? receptors.get(room)
                    : clients.get(room);

            out.writeUTF(other != null ? "ACCEPT" : "WAIT");
            out.flush();

            // Ponte de bytes
            InputStream rawIn = socket.getInputStream();
            byte[] buffer = new byte[4096];
            int len;

            while ((len = rawIn.read(buffer)) != -1) {

                Socket target = role.equals("CLIENT")
                        ? receptors.get(room)
                        : clients.get(room);

                if (target != null && !target.isClosed()) {
                    OutputStream targetOut = target.getOutputStream();
                    targetOut.write(buffer, 0, len);
                    targetOut.flush();
                }
            }

        } catch (IOException e) {
            System.out.println("Conexão encerrada: " + e.getMessage());
        } finally {
            cleanup(socket, role, room);
        }
    }

    private static void cleanup(Socket socket, String role, String room) {

        if (room != null && role != null) {

            if (role.equals("CLIENT")) {
                clients.remove(room);
            }

            if (role.equals("RECEPTOR")) {
                receptors.remove(room);
            }
        }

        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}

        System.out.println("Socket removido -> " + role + " | Sala: " + room);
    }
}
