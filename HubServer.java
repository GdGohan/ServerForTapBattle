import java.io.*;
import java.net.*;

public class HubServer {

    private static Socket receptor = null;
    private static Socket client = null;

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(8080);
        System.out.println("Servidor online na porta 8080");

        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("Novo cliente conectado: " + socket.getInetAddress());

            new Thread(() -> handleConnection(socket)).start();
        }
    }

    private static void handleConnection(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // Primeiro pacote recebido: ROLE (string curta, convertida em bytes)
            byte[] roleBuffer = new byte[20];
            int read = in.read(roleBuffer);
            String role = new String(roleBuffer, 0, read).trim();
            System.out.println("Função recebida: " + role);

            if (role.equals("ROLE:RECEPTOR")) {
                receptor = socket;
                out.write("ACCEPT".getBytes());
                out.flush();
                System.out.println("Receptor registrado");
            } else if (role.equals("ROLE:CLIENT")) {
                client = socket;
                out.write("ACCEPT".getBytes());
                out.flush();
                System.out.println("Cliente registrado");
            } else {
                out.write("REJECT".getBytes());
                out.flush();
                socket.close();
                return;
            }

            // Loop de encaminhamento de bytes
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                if (socket == client && receptor != null) {
                    OutputStream receptorOut = receptor.getOutputStream();
                    receptorOut.write(buffer, 0, bytesRead);
                    receptorOut.flush();
                } else if (socket == receptor && client != null) {
                    OutputStream clientOut = client.getOutputStream();
                    clientOut.write(buffer, 0, bytesRead);
                    clientOut.flush();
                }
            }

        } catch (IOException e) {
            System.out.println("Cliente desconectado: " + socket.getInetAddress());
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}
