package orchestrator;

import common.models.*;
import common.util.Logger;
import common.util.JsonUtil;
import common.protocol.MessageProtocol;

import java.io.*;
import java.net.Socket;

public class WorkerHandler implements Runnable {
    private Socket socket;
    private PrimaryOrchestrator orchestrator;
    
    public WorkerHandler(Socket socket, PrimaryOrchestrator orchestrator) {
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
                    case MessageProtocol.MSG_WORKER_REGISTER:
                        WorkerInfo worker = JsonUtil.fromJson(data, WorkerInfo.class);
                        if (worker != null) {
                            orchestrator.registerWorker(worker);
                            out.println("REGISTERED");
                        }
                        break;
                    case MessageProtocol.MSG_HEARTBEAT:
                        orchestrator.updateHeartbeat(data);
                        break;
                    case MessageProtocol.MSG_TASK_RESULT:
                        TaskResult result = JsonUtil.fromJson(data, TaskResult.class);
                        // Processamento assíncrono
                        break;
                    case "STATUS_UPDATE":
                        Task task = JsonUtil.fromJson(data, Task.class);
                        if (task != null && task.getStatus() == TaskStatus.PROCESSING) {
                             Task existingTask = orchestrator.getTasks().get(task.getId());
                             if (existingTask != null) {
                             existingTask.setStatus(TaskStatus.PROCESSING);
                            Logger.info("WorkerHandler", "Tarefa " + task.getId() + " agora em PROCESSING");
            }
        }
        break;
                    default:
                        Logger.warning("WorkerHandler", "Mensagem desconhecida: " + type);
                }
            }
        } catch (IOException e) {
            Logger.error("WorkerHandler", "Erro: " + e.getMessage());
        }
    }
}