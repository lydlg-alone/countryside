package org.village.logging;

public class AuditService {
    public void record(String actor, String action, String detail) {
        // minimal placeholder - real impl should persist to DB or log system
        System.out.printf("[AUDIT] actor=%s action=%s detail=%s\n", actor, action, detail);
    }
}
