package common.models;

public enum TaskStatus {
    
    PENDING("Pendente"),
    ASSIGNED("Atribuída"),
    PROCESSING("Processando"),
    COMPLETED("Concluída"),
    FAILED("Falhou"),
    REASSIGNED("Reatribuída");
    
    private final String description;
    
    TaskStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return description;
    }
}