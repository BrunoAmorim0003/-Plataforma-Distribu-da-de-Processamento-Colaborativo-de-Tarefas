package common.models;

import java.io.Serializable;

public class TaskResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String taskId;
    private boolean success;
    private String message;
    private long lamportTimestamp;
    private long processingTime;
    
    public TaskResult() {}
    
    public TaskResult(String taskId, boolean success, String message) {
        this.taskId = taskId;
        this.success = success;
        this.message = message;
    }
    
    // Getters e Setters
    public String getTaskId(){ 
        return taskId; 
    }
    public void setTaskId(String taskId){ 
        this.taskId = taskId; 
    }
    
    public boolean isSuccess(){ 
        return success; 
    }
    public void setSuccess(boolean success){ 
        this.success = success; 
    }
    
    public String getMessage(){
        return message; 
    }
    public void setMessage(String message){
         this.message = message; 
    }
    
    public long getLamportTimestamp(){ 
        return lamportTimestamp; 
    }
    public void setLamportTimestamp(long lamportTimestamp){ 
        this.lamportTimestamp = lamportTimestamp; 
    }
    
    public long getProcessingTime(){ 
        return processingTime; 
    }
    public void setProcessingTime(long processingTime){ 
        this.processingTime = processingTime; 
    }
    
    @Override
    public String toString() {
        return String.format("[Resultado] Tarefa %s - Sucesso: %s - %s", 
                taskId, success, message);
    }
}