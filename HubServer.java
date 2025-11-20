import java.io.*;
import java.net.*;
import java.util.*;

public class HubServer {
    public static void main(String[] args) throws IOException {
        int port = 12345; // porta do servidor
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Servidor rodando na porta " + port);

        List<Socket> clients = Collections.synchronizedList(new ArrayList<>());

        while (true) {
            Socket clientSocket = serverSocket.accept();
            clients.add(clientSocket);
            System.out.println("Novo cliente conectado: " + clientSocket.getInetAddress());

            // Mantém cada cliente em thread separada
            new Thread(() -> handleClient(clientSocket)).start();
        }
    }

    private static void handleClient(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Mantém a conexão aberta; o seu jogo fará a lógica
            while (true) {
                String msg = in.readLine();
                if (msg == null) break; // cliente desconectou
                System.out.println("Mensagem de " + socket.getInetAddress() + ": " + msg);
            }
        } catch (IOException e) {
            System.out.println("Cliente desconectou: " + socket.getInetAddress());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
                               }
