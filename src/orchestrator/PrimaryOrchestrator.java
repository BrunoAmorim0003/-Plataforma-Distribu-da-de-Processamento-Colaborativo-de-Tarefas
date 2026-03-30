package orchestrator;

import common.models.*;
import common.util.Logger;
import common.util.JsonUtil;
import common.protocol.MessageProtocol;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class PrimaryOrchestrator {
    private static final int ORCHESTRATOR_PORT = 8080;
    private static final int CLIENT_PORT = 8081;
    private static final int MULTICAST_PORT = 8888;
    private static final String MULTICAST_GROUP = "230.0.0.1";
    private static final int HEARTBEAT_TIMEOUT = 10000;
    private static final int HEARTBEAT_CHECK_INTERVAL = 5000;
    private static final int STATE_SYNC_INTERVAL = 3000;
    
    private Map<String, Task> tasks;
    private Map<String, WorkerInfo> workers;
    private Map<String, String> authTokens;
    private Map<String, String> clientCredentials;
    private LoadBalancer loadBalancer;
    private long lamportClock;
    private ScheduledExecutorService scheduler;
    private MulticastSocket multicastSocket;
    private InetAddress multicastAddress;
    
    public PrimaryOrchestrator() {
        this.tasks = new ConcurrentHashMap<>();
        this.workers = new ConcurrentHashMap<>();
        this.authTokens = new ConcurrentHashMap<>();
        this.clientCredentials = new ConcurrentHashMap<>();
        this.loadBalancer = new LoadBalancer(workers);
        this.lamportClock = 0;
        this.scheduler = Executors.newScheduledThreadPool(5);
        
        // Credenciais de teste
        clientCredentials.put("aluno1", "senha123");
        clientCredentials.put("aluno2", "senha456");
        
        try {
            multicastAddress = InetAddress.getByName(MULTICAST_GROUP);
            multicastSocket = new MulticastSocket();
            multicastSocket.setTimeToLive(1);
        } catch (IOException e) {
            Logger.error("PrimaryOrchestrator", "Erro ao configurar multicast: " + e.getMessage());
        }
    }
    
    public void start() {
        Logger.info("PrimaryOrchestrator", "Iniciando Orquestrador Principal...");
        
        // Thread para aceitar workers
        new Thread(this::acceptWorkers).start();
        
        // Thread para aceitar clientes
        new Thread(this::acceptClients).start();
        
        // Iniciar monitoramento de heartbeat
        startHeartbeatMonitor();
        
        // Iniciar sincronização com backup
        startStateSync();
        
        Logger.info("PrimaryOrchestrator", "Orquestrador Principal rodando na porta " + ORCHESTRATOR_PORT);
    }
    
    private void acceptWorkers() {
        try (ServerSocket serverSocket = new ServerSocket(ORCHESTRATOR_PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                Logger.info("PrimaryOrchestrator", "Worker conectado: " + socket.getRemoteSocketAddress());
                new Thread(new WorkerHandler(socket, this)).start();
            }
        } catch (IOException e) {
            Logger.error("PrimaryOrchestrator", "Erro ao aceitar worker: " + e.getMessage());
        }
    }
    
    private void acceptClients() {
        try (ServerSocket serverSocket = new ServerSocket(CLIENT_PORT)) {
            Logger.info("PrimaryOrchestrator", "Servidor de clientes rodando na porta " + CLIENT_PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                Logger.info("PrimaryOrchestrator", "Cliente conectado: " + socket.getRemoteSocketAddress());
                new Thread(new ClientHandler(socket, this)).start();
            }
        } catch (IOException e) {
            Logger.error("PrimaryOrchestrator", "Erro ao aceitar cliente: " + e.getMessage());
        }
    }
    
    private void startHeartbeatMonitor() {
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            List<String> failedWorkers = new ArrayList<>();
            
            for (WorkerInfo worker : workers.values()) {
                if (worker.isActive() && (now - worker.getLastHeartbeat() > HEARTBEAT_TIMEOUT)) {
                    Logger.warning("PrimaryOrchestrator", "Worker " + worker.getId() + " falhou! Último heartbeat: " + 
                            (now - worker.getLastHeartbeat()) + "ms atrás");
                    worker.setActive(false);
                    failedWorkers.add(worker.getId());
                    reassignTasksFromFailedWorker(worker);
                }
            }
            
            if (!failedWorkers.isEmpty()) {
                Logger.info("PrimaryOrchestrator", "Workers falhos: " + String.join(", ", failedWorkers));
            }
        }, HEARTBEAT_CHECK_INTERVAL, HEARTBEAT_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    private void reassignTasksFromFailedWorker(WorkerInfo failedWorker) {
        for (Task task : tasks.values()) {
            if (failedWorker.getId().equals(task.getAssignedWorker()) && 
                task.getStatus() == TaskStatus.ASSIGNED || task.getStatus() == TaskStatus.PROCESSING) {
                task.setStatus(TaskStatus.REASSIGNED);
                distributeTask(task);
                Logger.info("PrimaryOrchestrator", "Tarefa " + task.getId() + " reatribuída do worker " + failedWorker.getId());
            }
        }
    }
    
    private void startStateSync() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> state = new HashMap<>();
                state.put("tasks", tasks);
                state.put("workers", workers);
                state.put("lamportClock", lamportClock);
                
                String stateJson = JsonUtil.toJson(state);
                String message = MessageProtocol.build(MessageProtocol.MSG_STATE_SYNC, stateJson);
                
                DatagramPacket packet = new DatagramPacket(
                        message.getBytes(), message.length(),
                        multicastAddress, MULTICAST_PORT);
                multicastSocket.send(packet);
                
                Logger.info("PrimaryOrchestrator", "Estado sincronizado com backup");
            } catch (IOException e) {
                Logger.warning("PrimaryOrchestrator", "Erro ao sincronizar estado: " + e.getMessage());
            }
        }, STATE_SYNC_INTERVAL, STATE_SYNC_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    public synchronized long getNextLamportTimestamp() {
        return ++lamportClock;
    }
    
    public synchronized void updateLamportTimestamp(long receivedTimestamp) {
        lamportClock = Math.max(lamportClock, receivedTimestamp) + 1;
    }
    
    public String authenticate(String username, String password) {
        if (clientCredentials.containsKey(username) && 
            clientCredentials.get(username).equals(password)) {
            String token = UUID.randomUUID().toString();
            authTokens.put(token, username);
            Logger.info("PrimaryOrchestrator", "Usuário " + username + " autenticado");
            return token;
        }
        Logger.warning("PrimaryOrchestrator", "Falha de autenticação para " + username);
        return null;
    }
    
    public boolean validateToken(String token) {
        return authTokens.containsKey(token);
    }
    
    public Task submitTask(String clientId, String description, long executionTime) {
        Task task = new Task(description, executionTime, clientId);
        task.setLamportTimestamp(getNextLamportTimestamp());
        tasks.put(task.getId(), task);
        
        Logger.info("PrimaryOrchestrator", "Tarefa submetida: " + task.getId() + " por " + clientId);
        distributeTask(task);
        
        return task;
    }
    
    public void distributeTask(Task task) {
        WorkerInfo worker = loadBalancer.getNextWorker();
        
        if (worker != null && worker.isActive()) {
            task.setStatus(TaskStatus.ASSIGNED);
            task.setAssignedWorker(worker.getId());
            task.setLamportTimestamp(getNextLamportTimestamp());
            
            worker.incrementLoad();
            worker.setCurrentTaskId(task.getId());
            
            sendTaskToWorker(worker, task);
            Logger.info("PrimaryOrchestrator", "Tarefa " + task.getId() + " distribuída para " + worker.getId());
        } else {
            task.setStatus(TaskStatus.PENDING);
            Logger.warning("PrimaryOrchestrator", "Nenhum worker disponível para tarefa " + task.getId());
        }
    }
    
    private void sendTaskToWorker(WorkerInfo worker, Task task) {
        new Thread(() -> {
            try (Socket socket = new Socket(worker.getHost(), worker.getPort())) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                String taskJson = JsonUtil.toJson(task);
                String message = MessageProtocol.build(MessageProtocol.MSG_TASK_DISTRIBUTION, taskJson);
                out.println(message);
                
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String response = in.readLine();
                
                if (response != null) {
                    String[] parts = MessageProtocol.parse(response);
                    if (parts[0].equals(MessageProtocol.MSG_TASK_RESULT)) {
                        TaskResult result = JsonUtil.fromJson(parts[1], TaskResult.class);
                        handleTaskResult(result, worker);
                    }
                }
            } catch (IOException e) {
                Logger.error("PrimaryOrchestrator", "Erro ao enviar tarefa para worker: " + e.getMessage());
                worker.setActive(false);
                reassignTasksFromFailedWorker(worker);
            }
        }).start();
    }
    
    private void handleTaskResult(TaskResult result, WorkerInfo worker) {
        Task task = tasks.get(result.getTaskId());
        if (task != null) {
            updateLamportTimestamp(result.getLamportTimestamp());
            task.setLamportTimestamp(getNextLamportTimestamp());
            
            if (result.isSuccess()) {
                task.setStatus(TaskStatus.COMPLETED);
                task.setCompletionTime(System.currentTimeMillis());
                Logger.info("PrimaryOrchestrator", "Tarefa " + task.getId() + " concluída pelo worker " + worker.getId());
            } else {
                task.setStatus(TaskStatus.FAILED);
                Logger.warning("PrimaryOrchestrator", "Tarefa " + task.getId() + " falhou: " + result.getMessage());
            }
            
            worker.decrementLoad();
            worker.setCurrentTaskId(null);
        }
    }
    
    public Task getTaskStatus(String taskId) {
        return tasks.get(taskId);
    }
    
    public void registerWorker(WorkerInfo worker) {
        workers.put(worker.getId(), worker);
        Logger.info("PrimaryOrchestrator", "Worker registrado: " + worker.getId());
    }
    
    public void updateHeartbeat(String workerId) {
        WorkerInfo worker = workers.get(workerId);
        if (worker != null) {
            worker.setLastHeartbeat(System.currentTimeMillis());
            if (!worker.isActive()) {
                worker.setActive(true);
                Logger.info("PrimaryOrchestrator", "Worker " + workerId + " recuperado");
            }
        }
    }
    
    public Map<String, Task> getTasks() { return tasks; }
    public Map<String, WorkerInfo> getWorkers() { return workers; }
    
    public static void main(String[] args) {
        PrimaryOrchestrator orchestrator = new PrimaryOrchestrator();
        orchestrator.start();
    }
}