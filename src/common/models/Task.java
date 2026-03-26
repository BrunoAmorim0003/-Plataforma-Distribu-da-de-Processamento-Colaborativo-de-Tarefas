package common.models;

import java.io.Serializable;
import java.util.UUID;

public class Task implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String description;
    private long executionTime;
    private TaskStatus status;
    private String clientId;
    private String assignedWorker;
    private long lamportTimestamp;
    private long submissionTime;
    private long completionTime;
    
    public Task() {
        this.id = UUID.randomUUID().toString();
        this.status = TaskStatus.PENDING;
        this.submissionTime = System.currentTimeMillis();
    }
    
    public Task(String description, long executionTime, String clientId) {
        this();
        this.description = description;
        this.executionTime = executionTime;
        this.clientId = clientId;
    }
    
    
    public String getId() {
        return id;
    }
    public void setId(String id){ 
        this.id = id;
    }
    
    public String getDescription(){ 
        return description; 
    }
    public void setDescription(String description){ 
        this.description = description; 
    }
    
    public long getExecutionTime(){
         return executionTime; 
        }

    public void setExecutionTime(long executionTime){ 
        this.executionTime = executionTime; 
    }
    
    public TaskStatus getStatus(){
         return status; 
    }

    public void setStatus(TaskStatus status){ 
        this.status = status; 
    }
    
    public String getClientId(){ 
        return clientId; 
    }
    public void setClientId(String clientId){ 
        this.clientId = clientId;
    }
    
    public String getAssignedWorker(){ 
        return assignedWorker;
    }
    public void setAssignedWorker(String assignedWorker){ 
        this.assignedWorker = assignedWorker; 
    }
    
    public long getLamportTimestamp(){ 
        return lamportTimestamp; 
    }
    public void setLamportTimestamp(long lamportTimestamp){ 
        this.lamportTimestamp = lamportTimestamp; 
    }
    
    public long getSubmissionTime(){ 
        return submissionTime; 
    }
    public void setSubmissionTime(long submissionTime){ 
        this.submissionTime = submissionTime; 
    }
    
    public long getCompletionTime(){ 
        return completionTime; 
    }
    public void setCompletionTime(long completionTime){ 
        this.completionTime = completionTime; 
    }
    
    @Override
    public String toString() {
        return String.format("[Tarefa %s] %s - %s", id, description, status);
    }
}