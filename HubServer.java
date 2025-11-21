import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class HubServer {

    private static final ConcurrentHashMap<String, Socket> clients   = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Socket> receptors = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        int port = 8080;

        System.out.println("HubServer iniciado na porta " + port);

        try (ServerSocket server = new ServerSocket(port)) {
            while (true) {
                Socket socket = server.accept();
                new Thread(() -> handle(socket)).start();
            }
        } catch (IOException e) {
            System.out.println("Erro no servidor: " + e.getMessage());
        }
    }

    private static void handle(Socket socket) {
        String role = null;
        String room = null;

        try {
            socket.setSoTimeout(30000);   // timeout de leitura
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);

            DataInputStream in  = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // 1 - Handshake
            String command = in.readUTF();

            String[] parts = command.split(";");
            for (String part : parts) {
                if (part.startsWith("ROLE:")) role = part.substring(5);
                if (part.startsWith("ROOM:")) room = part.substring(5);
            }

            if (role == null || room == null) {
                out.writeUTF("REJECT");
                return;
            }

            // 2 - Registro
            if ("RECEPTOR".equals(role)) {

                Socket old = receptors.get(room);
                if (old != null && old.isConnected() && !old.isClosed()) {
                    out.writeUTF("ROOM_EXISTS");
                    return;
                }

                receptors.put(room, socket);

            } else if ("CLIENT".equals(role)) {

                Socket old = clients.get(room);
                if (old != null && old.isConnected() && !old.isClosed()) {
                    out.writeUTF("ROOM_FULL");
                    return;
                }

                clients.put(room, socket);

            } else {
                out.writeUTF("REJECT");
                return;
            }

            // 3 - Espera o par se conectar
            Socket other = role.equals("CLIENT")
                    ? receptors.get(room)
                    : clients.get(room);

            while (other == null || other.isClosed()) {

                out.writeUTF("WAIT");
                out.flush();

                Thread.sleep(500);

                other = role.equals("CLIENT")
                        ? receptors.get(room)
                        : clients.get(room);
            }

            out.writeUTF("ACCEPT");
            out.flush();

            // 4 - Ponte de bytes (modo bruto)
            byte[] buffer = new byte[4096];
            InputStream rawIn = socket.getInputStream();
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

        } catch (Exception e) {
            System.out.println("Erro conexão [" + role + "] sala [" + room + "]: " + e.getMessage());
        } finally {
            cleanup(socket, role, room);
        }
    }

    private static void cleanup(Socket socket, String role, String room) {

        if (room != null && role != null) {
            if ("CLIENT".equals(role)) {
                clients.remove(room);
            }
            if ("RECEPTOR".equals(role)) {
                receptors.remove(room);
            }
        }

        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}

        System.out.println("Desconectado -> " + role + " | Sala: " + room);
    }
}
