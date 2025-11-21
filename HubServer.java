import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HubServer {

    private static Map<String, Socket> clients = new ConcurrentHashMap<>();
    private static Map<String, Socket> receptors = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        int port = 8080;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Servidor online na porta " + port);

            while (true) {
                final Socket socket = serverSocket.accept();
                new Thread(() -> handleConnection(socket)).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleConnection(Socket socket) {
        try {
            DataInputStream dataIn = new DataInputStream(socket.getInputStream());
            DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());

            // 1️⃣ Verifica se o cliente quer a lista de receptores
            socket.setSoTimeout(5000); // opcional: timeout de leitura inicial
            socket.setReceiveBufferSize(256*1024);
            String firstMsg = dataIn.readUTF();
            if ("REQUEST_CONNECTED_USERS".equals(firstMsg)) {
                dataOut.writeInt(receptors.size());
                dataOut.flush();

                for (Socket s : receptors.values()) {
                    dataOut.writeUTF(s.getInetAddress().getHostAddress());
                    dataOut.flush();
                }
                return; // pode fechar a conexão ou continuar se quiser
            }
            socket.setSoTimeout(0); // remove timeout

            // 2️⃣ Lê header normal ROLE/ROOM
            String header = dataIn.readUTF();
            String role = null;
            String roomId = null;
            String[] parts = header.split(";");
            for (String part : parts) {
                if (part.startsWith("ROLE:")) role = part.substring("ROLE:".length());
                if (part.startsWith("ROOM:")) roomId = part.substring("ROOM:".length());
            }

            if (role == null || roomId == null) {
                dataOut.writeUTF("REJECT");
                dataOut.flush();
                socket.close();
                return;
            }

            // 3️⃣ Adiciona socket à coleção correta
            if (role.equals("CLIENT")) clients.put(roomId, socket);
            else if (role.equals("RECEPTOR")) receptors.put(roomId, socket);
            else {
                dataOut.writeUTF("REJECT");
                dataOut.flush();
                socket.close();
                return;
            }

            // 4️⃣ Notifica se há par disponível
            Socket other = role.equals("CLIENT") ? receptors.get(roomId) : clients.get(roomId);
            if (other != null) dataOut.writeUTF("ACCEPT");
            else dataOut.writeUTF("WAIT");
            dataOut.flush();

            // 5️⃣ Loop de encaminhamento de dados
            byte[] buffer = new byte[4096];
            int read;
            while ((read = dataIn.read(buffer)) != -1) {
                Socket target = role.equals("CLIENT") ? receptors.get(roomId) : clients.get(roomId);
                if (target != null && !target.isClosed()) {
                    target.getOutputStream().write(buffer, 0, read);
                    target.getOutputStream().flush();
                }
            }

        } catch (IOException e) {
            System.out.println("Erro: " + e.getMessage());
        } finally {
            removeSocket(socket);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static void removeSocket(Socket socket) {
        clients.values().removeIf(s -> s == socket);
        receptors.values().removeIf(s -> s == socket);
        System.out.println("Socket removido: " + socket);
    }
}
