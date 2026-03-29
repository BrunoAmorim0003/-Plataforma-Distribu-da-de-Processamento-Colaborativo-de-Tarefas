package orchestrator;

import common.models.Task;
import common.util.Logger;
import common.protocol.MessageProtocol;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;
    private PrimaryOrchestrator orchestrator;
    private String authenticatedClient;
    
    public ClientHandler(Socket socket, PrimaryOrchestrator orchestrator) {
        this.socket = socket;
        this.orchestrator = orchestrator;
    }
    
    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                String[] parts = MessageProtocol.parse(inputLine);
                String type = parts[0];
                String data = parts.length > 1 ? parts[1] : "";
                
                switch (type) {
                    case MessageProtocol.MSG_AUTH_REQUEST:
                        String[] creds = data.split(":");
                        if (creds.length == 2) {
                            String token = orchestrator.authenticate(creds[0], creds[1]);
                            if (token != null) {
                                authenticatedClient = creds[0];
                                out.println(MessageProtocol.build(MessageProtocol.MSG_AUTH_RESPONSE, token));
                            } else {
                                out.println(MessageProtocol.build(MessageProtocol.MSG_AUTH_RESPONSE, "FAILED"));
                            }
                        }
                        break;
                    case MessageProtocol.MSG_TASK_SUBMISSION:
                        if (authenticatedClient == null) {
                            out.println("ERRO|Não autenticado");
                            break;
                        }
                        String[] taskData = data.split("\\|");
                        if (taskData.length == 2) {
                            String description = taskData[0];
                            long execTime = Long.parseLong(taskData[1]);
                            Task task = orchestrator.submitTask(authenticatedClient, description, execTime);
                            out.println("SUCESSO|" + task.getId());
                        }
                        break;
                    case MessageProtocol.MSG_STATUS_REQUEST:
                        if (authenticatedClient == null) {
                            out.println("ERRO|Não autenticado");
                            break;
                        }
                        Task task = orchestrator.getTaskStatus(data);
                        if (task != null && task.getClientId().equals(authenticatedClient)) {
                            String status = String.format("ID: %s | Descrição: %s | Status: %s | Worker: %s",
                                    task.getId(), task.getDescription(), task.getStatus(),
                                    task.getAssignedWorker() != null ? task.getAssignedWorker() : "N/A");
                            out.println(MessageProtocol.build(MessageProtocol.MSG_STATUS_RESPONSE, status));
                        } else {
                            out.println("ERRO|Tarefa não encontrada");
                        }
                        break;
                    default:
                        out.println("ERRO|Mensagem desconhecida");
                }
            }
        } catch (IOException e) {
            Logger.error("ClientHandler", "Erro: " + e.getMessage());
        }
    }
}