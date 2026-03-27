package common.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final String LOG_DIR = "logs";
    
    static {
        File logDir = new File(LOG_DIR);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
    }
    
    public static void info(String component, String message) {
        log(component, "INFO", message);
    }
    
    public static void warning(String component, String message) {
        log(component, "WARN", message);
    }
    
    public static void error(String component, String message) {
        log(component, "ERROR", message);
    }
    
    private static void log(String component, String level, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        String logMessage = String.format("[%s] [%s] [%s] %s", timestamp, level, component, message);
        
        // Console
        System.out.println(logMessage);
        
        // Arquivo
        String fileName = String.format("%s/%s_%s.log", LOG_DIR, component, 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName, true))) {
            writer.println(logMessage);
        } catch (IOException e) {
            System.err.println("Erro ao escrever log: " + e.getMessage());
        }
    }
}