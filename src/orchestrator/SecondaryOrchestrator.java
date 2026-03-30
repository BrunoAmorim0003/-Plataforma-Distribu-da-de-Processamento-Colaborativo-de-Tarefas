package orchestrator;

import common.models.*;
import common.util.Logger;
import common.util.JsonUtil;
import common.protocol.MessageProtocol;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class SecondaryOrchestrator {
    private static final int MULTICAST_PORT = 8888;
    private static final String MULTICAST_GROUP = "230.0.0.1";
    private static final int FAILOVER_TIMEOUT = 10000;
    private static final int CHECK_INTERVAL = 2000;
    
    private Map<String, Task> tasks;
    private Map<String, WorkerInfo> workers;
    private long lamportClock;
    private long lastStateUpdate;
    private boolean isPrimary = false;
    private MulticastSocket multicastSocket;
    private ScheduledExecutorService scheduler;
    
    public SecondaryOrchestrator() {
        this.tasks = new ConcurrentHashMap<>();
        this.workers = new ConcurrentHashMap<>();
        this.lamportClock = 0;
        this.lastStateUpdate = System.currentTimeMillis();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    public void start() {
        Logger.info("SecondaryOrchestrator", "Iniciando Orquestrador Backup...");
        
        // Thread para escutar mensagens do primário
        new Thread(this::listenForState).start();
        
        // Thread para monitorar falha do primário
        startFailoverMonitor();
        
        Logger.info("SecondaryOrchestrator", "Backup rodando, aguardando sincronização...");
    }
    
    private void listenForState() {
        try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            socket.joinGroup(group);
            
            byte[] buffer = new byte[65536];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            
            while (true) {
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                String[] parts = MessageProtocol.parse(message);
                
                if (parts[0].equals(MessageProtocol.MSG_STATE_SYNC)) {
                    updateState(parts[1]);
                    lastStateUpdate = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            Logger.error("SecondaryOrchestrator", "Erro no multicast: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
private void updateState(String stateJson) {
    try {
        Map<String, Object> state = JsonUtil.fromJson(stateJson, Map.class);
        
        if (state.containsKey("tasks")) {
            tasks.clear();
            // O estado vem como Map, precisamos converter corretamente
            Object tasksObj = state.get("tasks");
            if (tasksObj instanceof Map) {
                Map<String, Map<String, Object>> taskMaps = (Map<String, Map<String, Object>>) tasksObj;
                for (Map.Entry<String, Map<String, Object>> entry : taskMaps.entrySet()) {
                    Task task = new Task();
                    Map<String, Object> taskData = entry.getValue();
                    
                    // Preencher todos os campos da task
                    if (taskData.containsKey("id")) task.setId((String) taskData.get("id"));
                    if (taskData.containsKey("description")) task.setDescription((String) taskData.get("description"));
                    if (taskData.containsKey("executionTime")) task.setExecutionTime(((Number) taskData.get("executionTime")).longValue());
                    if (taskData.containsKey("status")) {
                        String statusStr = (String) taskData.get("status");
                        task.setStatus(TaskStatus.valueOf(statusStr));
                    }
                    if (taskData.containsKey("clientId")) task.setClientId((String) taskData.get("clientId"));
                    if (taskData.containsKey("assignedWorker")) task.setAssignedWorker((String) taskData.get("assignedWorker"));
                    if (taskData.containsKey("lamportTimestamp")) task.setLamportTimestamp(((Number) taskData.get("lamportTimestamp")).longValue());
                    if (taskData.containsKey("submissionTime")) task.setSubmissionTime(((Number) taskData.get("submissionTime")).longValue());
                    if (taskData.containsKey("completionTime")) task.setCompletionTime(((Number) taskData.get("completionTime")).longValue());
                    
                    tasks.put(task.getId(), task);
                }
            }
        }
        
        if (state.containsKey("workers")) {
            workers.clear();
            Object workersObj = state.get("workers");
            if (workersObj instanceof Map) {
                Map<String, Map<String, Object>> workersMaps = (Map<String, Map<String, Object>>) workersObj;
                for (Map.Entry<String, Map<String, Object>> entry : workersMaps.entrySet()) {
                    WorkerInfo worker = new WorkerInfo();
                    Map<String, Object> workerData = entry.getValue();
                    
                    if (workerData.containsKey("id")) worker.setId((String) workerData.get("id"));
                    if (workerData.containsKey("host")) worker.setHost((String) workerData.get("host"));
                    if (workerData.containsKey("port")) worker.setPort(((Number) workerData.get("port")).intValue());
                    if (workerData.containsKey("active")) worker.setActive((Boolean) workerData.get("active"));
                    if (workerData.containsKey("currentLoad")) worker.setCurrentLoad(((Number) workerData.get("currentLoad")).intValue());
                    
                    workers.put(worker.getId(), worker);
                }
            }
        }
        
        if (state.containsKey("lamportClock")) {
            lamportClock = ((Number) state.get("lamportClock")).longValue();
        }
        
        Logger.info("SecondaryOrchestrator", "Estado atualizado - Tasks: " + tasks.size() + 
                    ", Workers: " + workers.size() + ", Clock: " + lamportClock);
        
        // Log para debug - mostrar as tasks
        for (Task task : tasks.values()) {
            Logger.info("SecondaryOrchestrator", "Task sincronizada: " + task.getId() + " - " + task.getStatus());
        }
        
    } catch (Exception e) {
        Logger.error("SecondaryOrchestrator", "Erro ao atualizar estado: " + e.getMessage());
        e.printStackTrace();
    }
}
    
    private void startFailoverMonitor() {
        scheduler.scheduleAtFixedRate(() -> {
            long timeSinceLastUpdate = System.currentTimeMillis() - lastStateUpdate;
            
            if (!isPrimary && timeSinceLastUpdate > FAILOVER_TIMEOUT) {
                Logger.warning("SecondaryOrchestrator", "Primário falhou! Iniciando failover...");
                performFailover();
            }
        }, CHECK_INTERVAL, CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    private void performFailover() {
        isPrimary = true;
        Logger.info("SecondaryOrchestrator", "===== ASSUMINDO COMO ORQUESTRADOR PRINCIPAL =====");
        
        // Iniciar servidor para clientes
        new Thread(() -> startClientServer()).start();
        
        // Iniciar servidor para workers
        new Thread(() -> startWorkerServer()).start();
        
        // Reativar workers pendentes
        for (WorkerInfo worker : workers.values()) {
            worker.setActive(true);
            Logger.info("SecondaryOrchestrator", "Worker " + worker.getId() + " reativado");
        }
    }
    
    private void startClientServer() {
        try (ServerSocket serverSocket = new ServerSocket(8081)) {
            Logger.info("SecondaryOrchestrator", "Servidor de clientes iniciado na porta 8081");
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleClient(socket)).start();
            }
        } catch (IOException e) {
            Logger.error("SecondaryOrchestrator", "Erro no servidor de clientes: " + e.getMessage());
        }
    }
    
    private void handleClient(Socket socket) {
    try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
         PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
        
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            String[] parts = MessageProtocol.parse(inputLine);
            String type = parts[0];
            String data = parts.length > 1 ? parts[1] : "";
            
            if (type.equals(MessageProtocol.MSG_STATUS_REQUEST)) {
                Task task = tasks.get(data);
                if (task != null) {
                    String status = String.format("ID: %s | Descrição: %s | Status: %s | Worker: %s",
                            task.getId(), 
                            task.getDescription(), 
                            task.getStatus(),
                            task.getAssignedWorker() != null ? task.getAssignedWorker() : "N/A");
                    out.println(MessageProtocol.build(MessageProtocol.MSG_STATUS_RESPONSE, status));
                    Logger.info("SecondaryOrchestrator", "Status consultado: " + task.getId() + " - " + task.getStatus());
                } else {
                    out.println("ERRO|Tarefa não encontrada. ID: " + data);
                    Logger.warning("SecondaryOrchestrator", "Tarefa não encontrada: " + data);
                }
            } else if (type.equals(MessageProtocol.MSG_AUTH_REQUEST)) {
                String[] creds = data.split(":");
                if (creds.length == 2) {
                    if ((creds[0].equals("aluno1") && creds[1].equals("senha123")) ||
                        (creds[0].equals("aluno2") && creds[1].equals("senha456"))) {
                        String token = java.util.UUID.randomUUID().toString();
                        out.println(MessageProtocol.build(MessageProtocol.MSG_AUTH_RESPONSE, token));
                        Logger.info("SecondaryOrchestrator", "Usuário autenticado: " + creds[0]);
                    } else {
                        out.println(MessageProtocol.build(MessageProtocol.MSG_AUTH_RESPONSE, "FAILED"));
                    }
                }
            } else {
                out.println("ERRO|Operação não disponível no momento");
            }
        }
    } catch (IOException e) {
        Logger.error("SecondaryOrchestrator", "Erro no cliente: " + e.getMessage());
    }
}
    
    private void startWorkerServer() {
        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            Logger.info("SecondaryOrchestrator", "Servidor de workers iniciado na porta 8080");
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleWorker(socket)).start();
            }
        } catch (IOException e) {
            Logger.error("SecondaryOrchestrator", "Erro no servidor de workers: " + e.getMessage());
        }
    }
    
    private void handleWorker(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                String[] parts = MessageProtocol.parse(inputLine);
                String type = parts[0];
                String data = parts.length > 1 ? parts[1] : "";
                
                if (type.equals(MessageProtocol.MSG_HEARTBEAT)) {
                    // Atualizar heartbeat
                    out.println("ACK");
                } else if (type.equals(MessageProtocol.MSG_WORKER_REGISTER)) {
                    out.println("REGISTERED");
                }
            }
        } catch (IOException e) {
            Logger.error("SecondaryOrchestrator", "Erro no worker: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        SecondaryOrchestrator backup = new SecondaryOrchestrator();
        backup.start();
    }
}