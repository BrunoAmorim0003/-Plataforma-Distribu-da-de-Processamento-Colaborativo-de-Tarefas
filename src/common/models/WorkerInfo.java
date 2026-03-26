package common.models;

import java.io.Serializable;

public class WorkerInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String host;
    private int port;
    private boolean active;
    private long lastHeartbeat;
    private int currentLoad;
    private String currentTaskId;
    
    public WorkerInfo() {
        this.active = true;
        this.currentLoad = 0;
        this.lastHeartbeat = System.currentTimeMillis();
    }
    
    public WorkerInfo(String id, String host, int port) {
        this();
        this.id = id;
        this.host = host;
        this.port = port;
    }
    

    public String getId(){ 
        return id;
    }
    public void setId(String id){
        this.id = id; 
    }
    
    public String getHost(){ 
        return host;
    }
    public void setHost(String host){ 
        this.host = host; 
    }
    
    public int getPort(){ 
        return port; 
    }
    public void setPort(int port){ 
        this.port = port; 
    }
    
    public boolean isActive(){ 
        return active; 
    }
    public void setActive(boolean active){ 
        this.active = active; 
    }
    
    public long getLastHeartbeat(){ 
        return lastHeartbeat; 
    }
    public void setLastHeartbeat(long lastHeartbeat){ 
        this.lastHeartbeat = lastHeartbeat; 
    }
    
    public int getCurrentLoad(){ 
        return currentLoad; 
    }
    public void incrementLoad(){ 
        currentLoad++;
    }
    public void decrementLoad(){ 
        currentLoad--; 
    }
    
    public String getCurrentTaskId(){ 
        return currentTaskId; 
    }
    public void setCurrentTaskId(String currentTaskId){ 
        this.currentTaskId = currentTaskId; 
    }
    
    @Override
    public String toString() {
        return String.format("[Worker %s] %s:%d - Ativo: %s - Carga: %d", 
                id, host, port, active, currentLoad);
    }
}