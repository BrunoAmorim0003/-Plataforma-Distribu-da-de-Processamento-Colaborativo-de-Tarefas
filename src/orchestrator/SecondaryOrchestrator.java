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
                Map<String, Map<String, Object>> taskMaps = (Map<String, Map<String, Object>>) state.get("tasks");
                for (Map.Entry<String, Map<String, Object>> entry : taskMaps.entrySet()) {
                    Task task = new Task();
                    task.setId(entry.getKey());
                    tasks.put(entry.getKey(), task);
                }
            }
            
            if (state.containsKey("lamportClock")) {
                lamportClock = ((Number) state.get("lamportClock")).longValue();
            }
            
            Logger.info("SecondaryOrchestrator", "Estado atualizado - Tasks: " + tasks.size() + 
                    ", Clock: " + lamportClock);
        } catch (Exception e) {
            Logger.error("SecondaryOrchestrator", "Erro ao atualizar estado: " + e.getMessage());
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
                        String status = String.format("ID: %s | Status: %s", task.getId(), task.getStatus());
                        out.println(MessageProtocol.build(MessageProtocol.MSG_STATUS_RESPONSE, status));
                    } else {
                        out.println("ERRO|Tarefa não encontrada");
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