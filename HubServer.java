import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HubServer {

    private static Map<String, Socket> clients = new ConcurrentHashMap<String, Socket>();
    private static Map<String, Socket> receptors = new ConcurrentHashMap<String, Socket>();

    public static void main(String[] args) {
        int port = 8080;
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Servidor online na porta " + port);

            while (true) {
                final Socket socket = serverSocket.accept();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        handleConnection(socket);
                    }
                }).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleConnection(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            byte[] headerBuf = new byte[256];
            int headerLen = in.read(headerBuf);
            if (headerLen <= 0) {
                socket.close();
                return;
            }

            String header = new String(headerBuf, 0, headerLen).trim();
            String role = null;
            String roomId = null;
            String[] parts = header.split(";");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].startsWith("ROLE:")) role = parts[i].substring("ROLE:".length());
                if (parts[i].startsWith("ROOM:")) roomId = parts[i].substring("ROOM:".length());
            }

            if (role == null || roomId == null) {
                out.write("REJECT".getBytes());
                out.flush();
                socket.close();
                return;
            }

            if (role.equals("CLIENT")) clients.put(roomId, socket);
            else if (role.equals("RECEPTOR")) receptors.put(roomId, socket);
            else {
                out.write("REJECT".getBytes());
                out.flush();
                socket.close();
                return;
            }

            Socket other = role.equals("CLIENT") ? receptors.get(roomId) : clients.get(roomId);
            if (other != null) out.write("ACCEPT".getBytes());
            else out.write("WAIT".getBytes());
            out.flush();

            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
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
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private static void removeSocket(Socket socket) {
        clients.values().remove(socket);
        receptors.values().remove(socket);
        System.out.println("Socket removido: " + socket);
    }
}
