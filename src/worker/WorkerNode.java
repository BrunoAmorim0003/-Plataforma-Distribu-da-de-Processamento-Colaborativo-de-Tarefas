package worker;

import common.models.*;
import common.util.Logger;
import common.util.JsonUtil;
import common.protocol.MessageProtocol;

import java.net.*;
import java.io.*;

public class WorkerNode {
    private String id;
    private String orchestratorHost;
    private int orchestratorPort;
    private int workerPort;
    private boolean active;
    private ServerSocket serverSocket;
    private String currentTaskId;
    
    public WorkerNode(String id, String orchestratorHost, int orchestratorPort, int workerPort) {
        this.id = id;
        this.orchestratorHost = orchestratorHost;
        this.orchestratorPort = orchestratorPort;
        this.workerPort = workerPort;
        this.active = true;
    }
    
    public void start() {
        Logger.info("Worker-" + id, "Iniciando Worker Node...");
        
        // Registrar no orquestrador
        registerWithOrchestrator();
        
        // Iniciar servidor para receber tarefas
        new Thread(this::startServer).start();
        
        // Iniciar envio de heartbeats
        new Thread(this::sendHeartbeats).start();
        
        Logger.info("Worker-" + id, "Worker rodando na porta " + workerPort);
    }
    
    private void registerWithOrchestrator() {
        try (Socket socket = new Socket(orchestratorHost, orchestratorPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            WorkerInfo workerInfo = new WorkerInfo(id, "localhost", workerPort);
            String json = JsonUtil.toJson(workerInfo);
            String message = MessageProtocol.build(MessageProtocol.MSG_WORKER_REGISTER, json);
            out.println(message);
            
            String response = in.readLine();
            Logger.info("Worker-" + id, "Registrado no orquestrador: " + response);
        } catch (IOException e) {
            Logger.error("Worker-" + id, "Erro ao registrar: " + e.getMessage());
        }
    }
    
    private void startServer() {
        try {
            serverSocket = new ServerSocket(workerPort);
            
            while (active) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleTask(socket)).start();
            }
        } catch (IOException e) {
            Logger.error("Worker-" + id, "Erro no servidor: " + e.getMessage());
        }
    }
    
    private void handleTask(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            String inputLine = in.readLine();
            if (inputLine != null) {
                String[] parts = MessageProtocol.parse(inputLine);
                if (parts[0].equals(MessageProtocol.MSG_TASK_DISTRIBUTION)) {
                    Task task = JsonUtil.fromJson(parts[1], Task.class);
                    executeTask(task, out);
                }
            }
        } catch (IOException e) {
            Logger.error("Worker-" + id, "Erro ao processar tarefa: " + e.getMessage());
        }
    }
    
    private void executeTask(Task task, PrintWriter out) {
        currentTaskId = task.getId();
        Logger.info("Worker-" + id, "Executando tarefa: " + task.getId() + " - " + task.getDescription());
        
        TaskResult result = new TaskResult();
        result.setTaskId(task.getId());
        result.setLamportTimestamp(System.currentTimeMillis());
        
        try {
            // Simular processamento
            Thread.sleep(task.getExecutionTime());
            
            result.setSuccess(true);
            result.setMessage("Tarefa executada com sucesso!");
            Logger.info("Worker-" + id, "Tarefa " + task.getId() + " concluída");
            
        } catch (InterruptedException e) {
            result.setSuccess(false);
            result.setMessage("Erro na execução: " + e.getMessage());
            Logger.error("Worker-" + id, "Erro na tarefa " + task.getId() + ": " + e.getMessage());
        }
        
        String resultJson = JsonUtil.toJson(result);
        String message = MessageProtocol.build(MessageProtocol.MSG_TASK_RESULT, resultJson);
        out.println(message);
        
        currentTaskId = null;
    }
    
    private void sendHeartbeats() {
        while (active) {
            try {
                Thread.sleep(3000);
                
                try (Socket socket = new Socket(orchestratorHost, orchestratorPort)) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    String message = MessageProtocol.build(MessageProtocol.MSG_HEARTBEAT, id);
                    out.println(message);
                } catch (IOException e) {
                    Logger.warning("Worker-" + id, "Falha ao enviar heartbeat: " + e.getMessage());
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    public void stop() {
        active = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // Ignorar
        }
        Logger.info("Worker-" + id, "Worker finalizado");
    }
    
    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Uso: java WorkerNode <id> <orchestratorHost> <orchestratorPort> <workerPort>");
            System.out.println("Exemplo: java WorkerNode worker1 localhost 8080 9001");
            return;
        }
        
        String id = args[0];
        String host = args[1];
        int orchPort = Integer.parseInt(args[2]);
        int workerPort = Integer.parseInt(args[3]);
        
        WorkerNode worker = new WorkerNode(id, host, orchPort, workerPort);
        worker.start();
    }
}