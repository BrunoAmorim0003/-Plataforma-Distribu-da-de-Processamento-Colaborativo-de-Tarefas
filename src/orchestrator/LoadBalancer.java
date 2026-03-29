package orchestrator;

import common.models.WorkerInfo;

import java.util.*;

public class LoadBalancer {
    private Map<String, WorkerInfo> workers;
    private int roundRobinIndex;
    private String strategy;
    
    public LoadBalancer(Map<String, WorkerInfo> workers) {
        this.workers = workers;
        this.roundRobinIndex = 0;
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
            return activeWorkers.stream()
                    .min(Comparator.comparingInt(WorkerInfo::getCurrentLoad))
                    .orElse(null);
        } else {
            int index = roundRobinIndex % activeWorkers.size();
            roundRobinIndex++;
            return activeWorkers.get(index);
        }
    }
    
    public String getStrategy() {
        return strategy;
    }
}