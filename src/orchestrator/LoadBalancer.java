package orchestrator;

import common.models.WorkerInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancer {
    private Map<String, WorkerInfo> workers;
    private AtomicInteger roundRobinCounter;
    private String strategy;
    
    public LoadBalancer(Map<String, WorkerInfo> workers) {
        this.workers = workers;
        this.roundRobinCounter = new AtomicInteger(0);
        this.strategy = "LEAST_LOAD";
    }
    
    public WorkerInfo getNextWorker() {
        List<WorkerInfo> activeWorkers = new ArrayList<>();
        for (WorkerInfo w : workers.values()) {
            if (w.isActive()) {
                activeWorkers.add(w);
            }
        }
        
        if (activeWorkers.isEmpty()) {
            return null;
        }
        
        if (strategy.equals("LEAST_LOAD")) {
            // Encontra a menor carga atual
            int minLoad = activeWorkers.stream()
                    .mapToInt(WorkerInfo::getCurrentLoad)
                    .min()
                    .orElse(0);
            
            // Filtrar apenas os workers com a menor carga
            List<WorkerInfo> leastLoadedWorkers = new ArrayList<>();
            for (WorkerInfo w : activeWorkers) {
                if (w.getCurrentLoad() == minLoad) {
                    leastLoadedWorkers.add(w);
                }
            }
            
            // usar Round Robin entre os empatados
            int index = roundRobinCounter.getAndIncrement() % leastLoadedWorkers.size();
            WorkerInfo selected = leastLoadedWorkers.get(index);
            
            System.out.println("[LoadBalancer] Workers com carga " + minLoad + ": " + leastLoadedWorkers.size() + 
                               " | Escolhido: " + selected.getId());
            
            return selected;
            
        } else {
            //Round Robin
            List<WorkerInfo> allActive = new ArrayList<>(activeWorkers);
            int index = roundRobinCounter.getAndIncrement() % allActive.size();
            return allActive.get(index);
        }
    }
    
    public String getStrategy() {
        return strategy;
    }
}