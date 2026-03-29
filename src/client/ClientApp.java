package client;

import common.util.Logger;
import common.protocol.MessageProtocol;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ClientApp {
    private String serverHost;
    private int serverPort;
    private String authToken;
    private String username;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    
    public ClientApp(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }
    
    public void start() {
        try {
            socket = new Socket(serverHost, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            Scanner scanner = new Scanner(System.in);
            
            System.out.println("=== Plataforma Distribuída de Processamento ===");
            System.out.println("Conectado ao servidor " + serverHost + ":" + serverPort);
            
            while (true) {
                if (authToken == null) {
                    System.out.print("\n[LOGIN] Usuário: ");
                    String user = scanner.nextLine();
                    System.out.print("[LOGIN] Senha: ");
                    String pass = scanner.nextLine();
                    
                    String authMsg = MessageProtocol.build(MessageProtocol.MSG_AUTH_REQUEST, user + ":" + pass);
                    out.println(authMsg);
                    
                    String response = in.readLine();
                    String[] parts = MessageProtocol.parse(response);
                    if (parts[0].equals(MessageProtocol.MSG_AUTH_RESPONSE) && !parts[1].equals("FAILED")) {
                        authToken = parts[1];
                        username = user;
                        System.out.println("✅ Login realizado com sucesso!");
                        showMenu();
                    } else {
                        System.out.println("❌ Falha no login. Tente novamente.");
                    }
                } else {
                    System.out.print("\n> ");
                    String command = scanner.nextLine();
                    processCommand(command);
                }
            }
            
        } catch (IOException e) {
            Logger.error("ClientApp", "Erro de conexão: " + e.getMessage());
            System.err.println("Não foi possível conectar ao servidor.");
        }
    }
    
    private void showMenu() {
        System.out.println("\n=== MENU ===");
        System.out.println("submit <descrição> <tempo_ms> - Submeter tarefa");
        System.out.println("status <id> - Consultar status da tarefa");
        System.out.println("logout - Sair");
        System.out.println("=================");
    }
    
    private void processCommand(String command) {
        String[] parts = command.split(" ", 3);
        
        if (parts[0].equalsIgnoreCase("submit") && parts.length >= 3) {
            String description = parts[1];
            long execTime;
            try {
                execTime = Long.parseLong(parts[2]);
                submitTask(description, execTime);
            } catch (NumberFormatException e) {
                System.out.println("❌ Tempo deve ser um número em milissegundos");
            }
        } else if (parts[0].equalsIgnoreCase("status") && parts.length >= 2) {
            getStatus(parts[1]);
        } else if (parts[0].equalsIgnoreCase("logout")) {
            logout();
        } else {
            System.out.println("❌ Comando inválido. Use: submit <desc> <tempo> ou status <id>");
        }
    }
    
    private void submitTask(String description, long executionTime) {
        String taskData = description + "|" + executionTime;
        String msg = MessageProtocol.build(MessageProtocol.MSG_TASK_SUBMISSION, taskData);
        out.println(msg);
        
        try {
            String response = in.readLine();
            if (response.startsWith("SUCESSO")) {
                String taskId = response.split("\\|")[1];
                System.out.println("✅ Tarefa submetida com sucesso! ID: " + taskId);
            } else {
                System.out.println("❌ Erro ao submeter tarefa: " + response);
            }
        } catch (IOException e) {
            System.err.println("Erro: " + e.getMessage());
        }
    }
    
    private void getStatus(String taskId) {
        String msg = MessageProtocol.build(MessageProtocol.MSG_STATUS_REQUEST, taskId);
        out.println(msg);
        
        try {
            String response = in.readLine();
            String[] parts = MessageProtocol.parse(response);
            if (parts[0].equals(MessageProtocol.MSG_STATUS_RESPONSE)) {
                System.out.println("📊 Status: " + parts[1]);
            } else {
                System.out.println("❌ " + parts[1]);
            }
        } catch (IOException e) {
            System.err.println("Erro: " + e.getMessage());
        }
    }
    
    private void logout() {
        authToken = null;
        username = null;
        System.out.println("✅ Logout realizado com sucesso!");
        showMenu();
    }
    
    public static void main(String[] args) {
        String host = "localhost";
        int port = 8081;
        
        if (args.length >= 2) {
            host = args[0];
            port = Integer.parseInt(args[1]);
        }
        
        ClientApp client = new ClientApp(host, port);
        client.start();
    }
}