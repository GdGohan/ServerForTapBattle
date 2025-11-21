import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class HubServer {

    // 1x1 por sala
    private static final Map<String, Socket> clients   = new ConcurrentHashMap<>();
    private static final Map<String, Socket> receptors = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        int port = 8080;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("HubServer online na porta " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handle(socket)).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handle(Socket socket) {
        String roomId = null;
        String role   = null;

        try {
            DataInputStream in  = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            socket.setSoTimeout(8000);
            socket.setReceiveBufferSize(256 * 1024);

            // 1) Pedido de lista de salas
            String first = in.readUTF();

            if ("LIST_ROOMS".equals(first)) {
                Set<String> rooms = receptors.keySet();

                out.writeInt(rooms.size());
                for (String room : rooms) {
                    out.writeUTF(room);
                }

                out.flush();
                socket.close();
                return;
            }

            socket.setSoTimeout(0);

            // 2) Registro: ROLE:CLIENT;ROOM:X
            String[] parts = first.split(";");
            for (String s : parts) {
                if (s.startsWith("ROLE:")) role = s.substring(5);
                if (s.startsWith("ROOM:")) roomId = s.substring(5);
            }

            if (role == null || roomId == null) {
                out.writeUTF("REJECT");
                socket.close();
                return;
            }

            // 3) Controle de salas
            if (role.equals("RECEPTOR")) {
                if (receptors.containsKey(roomId)) {
                    out.writeUTF("ROOM_EXISTS");
                    socket.close();
                    return;
                }
                receptors.put(roomId, socket);
            } 
            else if (role.equals("CLIENT")) {
                if (clients.containsKey(roomId)) {
                    out.writeUTF("ROOM_FULL");
                    socket.close();
                    return;
                }
                clients.put(roomId, socket);
            } 
            else {
                out.writeUTF("REJECT");
                socket.close();
                return;
            }

            // 4) Checa se já tem par
            Socket other = role.equals("CLIENT")
                    ? receptors.get(roomId)
                    : clients.get(roomId);

            out.writeUTF(other != null ? "ACCEPT" : "WAIT");
            out.flush();

            // 5) Ponte de dados
            byte[] buffer = new byte[4096];
            int count;

            while ((count = in.read(buffer)) != -1) {
                Socket target = role.equals("CLIENT")
                        ? receptors.get(roomId)
                        : clients.get(roomId);

                if (target != null && !target.isClosed()) {
                    OutputStream targetOut = target.getOutputStream();
                    targetOut.write(buffer, 0, count);
                    targetOut.flush();
                }
            }

        } catch (Exception e) {
            System.out.println("Erro: " + e.getMessage());
        } finally {
            removeSocket(socket, role, roomId);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static void removeSocket(Socket socket, String role, String room) {
        if (room == null || role == null) return;

        if ("CLIENT".equals(role)) {
            clients.remove(room);
        }

        if ("RECEPTOR".equals(role)) {
            receptors.remove(room);
        }

        System.out.println("Removido -> " + role + " | Sala: " + room);
    }
}
