package common.protocol;

public class MessageProtocol {
    // Tipos de mensagem
    public static final String MSG_TASK_SUBMISSION = "TASK_SUBMISSION";
    public static final String MSG_TASK_DISTRIBUTION = "TASK_DISTRIBUTION";
    public static final String MSG_TASK_RESULT = "TASK_RESULT";
    public static final String MSG_HEARTBEAT = "HEARTBEAT";
    public static final String MSG_WORKER_REGISTER = "WORKER_REGISTER";
    public static final String MSG_WORKER_UNREGISTER = "WORKER_UNREGISTER";
    public static final String MSG_STATE_SYNC = "STATE_SYNC";
    public static final String MSG_AUTH_REQUEST = "AUTH_REQUEST";
    public static final String MSG_AUTH_RESPONSE = "AUTH_RESPONSE";
    public static final String MSG_STATUS_REQUEST = "STATUS_REQUEST";
    public static final String MSG_STATUS_RESPONSE = "STATUS_RESPONSE";
    public static final String MSG_PING = "PING";
    public static final String MSG_PONG = "PONG";
    
    public static String build(String type, String data) {
        return type + "|" + data;
    }
    
    public static String[] parse(String message) {
        return message.split("\\|", 2);
    }
}