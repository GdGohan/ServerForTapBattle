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
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // Primeiro pacote recebido: ROLE
            String role = in.readUTF();
            System.out.println("Função recebida: " + role);

            if (role.equals("ROLE:RECEPTOR")) {
                receptor = socket;
                System.out.println("Receptor registrado");
            } else if (role.equals("ROLE:CLIENT")) {
                client = socket;
                System.out.println("Cliente registrado");
            }

            // Agora começa loop de encaminhamento
            while (true) {
                String msg = in.readUTF();

                // Se veio do cliente, envie ao receptor
                if (socket == client && receptor != null) {
                    new DataOutputStream(receptor.getOutputStream()).writeUTF(msg);
                }

                // Se veio do receptor, envie ao cliente
                if (socket == receptor && client != null) {
                    new DataOutputStream(client.getOutputStream()).writeUTF(msg);
                }
            }

        } catch (Exception e) {
            System.out.println("Cliente desconectado: " + socket.getInetAddress());
        }
    }
}
