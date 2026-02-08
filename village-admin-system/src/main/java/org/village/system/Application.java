package org.village.system;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.security.SecureRandom;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

public class Application {
    // persistence config (environment or defaults)
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", System.getenv().getOrDefault("MYSQL_HOST", "127.0.0.1"));
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "village_db");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "village");
    private static final String DB_PASS = System.getenv().getOrDefault("DB_PASS", "villagepass");

    // local-only DB editor auth (root)
    private static final String DB_EDITOR_ROOT_PASSWORD = System.getenv().getOrDefault("DB_EDITOR_ROOT_PASSWORD", "lydlg");
    private static final long DB_EDITOR_TOKEN_TTL_MS = 12L * 60L * 60L * 1000L;
    private static volatile String dbEditorRootToken = null;
    private static volatile long dbEditorRootTokenExpMs = 0L;

    private static javax.sql.DataSource dataSource = null;
    private static volatile boolean tablesEnsured = false;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CAPTCHA_EXPIRES_SECONDS = 120;
    private static final Map<String, CaptchaEntry> CAPTCHA_STORE = new ConcurrentHashMap<>();
    private static final int ARGON2_ITERATIONS = 3;
    private static final int ARGON2_MEMORY_KB = 65536;
    private static final int ARGON2_PARALLELISM = 1;

    private static class CaptchaEntry {
        final String code;
        final long expiresAt;
        CaptchaEntry(String code, long expiresAt){ this.code = code; this.expiresAt = expiresAt; }
    }

    static {
        try {
            initializeDataSource();
            ensureTables();
            ensureColumns();
            seedIfEmpty();
        } catch (Exception e) {
            System.err.println("DB init failed: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/api/", exchange -> {
            // handle preflight
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; }
            if ("GET".equals(exchange.getRequestMethod())) {
                writeText(exchange, 200, "Village 管理系统 - API (Java JDBC) 已启动");
            } else {
                writeText(exchange, 405, "Method Not Allowed");
            }
        });

        // users
        server.createContext("/api/users", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleUsersRoot(exchange); });
        server.createContext("/api/finance/transactions", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleTransactions(exchange); });
        server.createContext("/api/finance/transactions/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleTransactionById(exchange); });
        server.createContext("/api/warnings/events", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleWarnings(exchange); });
        server.createContext("/api/warnings/events/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleWarningById(exchange); });
        server.createContext("/api/warnings/rules", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleWarningRules(exchange); });
        server.createContext("/api/warnings/logs", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleWarningLogs(exchange); });
        server.createContext("/api/warnings/stats", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleWarningStats(exchange); });
        server.createContext("/api/auth/login", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleLogin(exchange); });
        server.createContext("/api/auth/captcha", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleCaptcha(exchange); });
        server.createContext("/api/auth/password", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handlePasswordChange(exchange); });
        server.createContext("/api/industry/metrics", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleIndustryMetrics(exchange); });
        server.createContext("/api/industry/metrics/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleIndustryMetricById(exchange); });
        server.createContext("/api/map", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleMapData(exchange); });
        server.createContext("/api/residents", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleResidents(exchange); });
        server.createContext("/api/residents/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleResidentById(exchange); });
        server.createContext("/api/ai/records", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleAiRecords(exchange); });
        server.createContext("/api/ai/records/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleAiRecordById(exchange); });
        server.createContext("/api/ai/ask", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleAiAsk(exchange); });
        server.createContext("/api/ai/summarize", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleAiSummarize(exchange); });
        server.createContext("/api/ops/monitor", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleOpsMonitor(exchange); });
        server.createContext("/api/ops/health", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleOpsHealth(exchange); });
        server.createContext("/api/ops/logs", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleOpsLogs(exchange); });
        server.createContext("/api/ops/logs/report", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleOpsLogsReport(exchange); });
        server.createContext("/api/ops/backups", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleOpsBackups(exchange); });
        server.createContext("/api/ops/restores", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleOpsRestores(exchange); });
        server.createContext("/api/ops/audit", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleOpsAudit(exchange); });
        server.createContext("/api/ops/audit/report", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleOpsAuditReport(exchange); });
        // generic DB editor (local only)
        server.createContext("/api/ops/db/login", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleDbEditorLogin(exchange); });
        server.createContext("/api/ops/db/tables", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleDbEditorTables(exchange); });
        server.createContext("/api/ops/db/schema/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleDbEditorSchema(exchange); });
        server.createContext("/api/ops/db/rows/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleDbEditorRows(exchange); });
        server.createContext("/api/gov/tasks", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleGovTasks(exchange); });
        server.createContext("/api/gov/tasks/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleGovTaskById(exchange); });
        server.createContext("/api/gov/checkins", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleGovCheckins(exchange); });
        server.createContext("/api/gov/acceptance", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleGovAcceptance(exchange); });
        server.createContext("/api/gov/point-rules", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleGovPointRules(exchange); });
        server.createContext("/api/gov/point-rules/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleGovPointRuleById(exchange); });
        server.createContext("/api/gov/point-audit", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleGovPointAudit(exchange); });
        server.createContext("/api/gov/point-audit/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleGovPointAuditById(exchange); });
        server.createContext("/api/gov/activities", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleGovActivities(exchange); });
        server.createContext("/api/gov/activities/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleGovActivityById(exchange); });
        server.createContext("/api/feedback/items", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleFeedbackItems(exchange); });
        server.createContext("/api/feedback/items/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleFeedbackItemById(exchange); });
        server.createContext("/api/feedback/flow", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleFeedbackFlow(exchange); });
        server.createContext("/api/feedback/announcements", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleFeedbackAnnouncements(exchange); });
        server.createContext("/api/feedback/announcements/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleFeedbackAnnouncementById(exchange); });
        server.createContext("/api/feedback/stats", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleFeedbackStats(exchange); });

        // dynamic id handlers for users
        server.createContext("/api/users/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleUserById(exchange); });

        server.setExecutor(null);
        server.start();
        System.out.println("Village 管理系统 已启动，监听端口 8080");
    }

    // helper to read request body
    private static String readBody(HttpExchange ex) throws IOException {
        InputStream in = ex.getRequestBody();
        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r = 0;
        while ((r = in.read(buf)) != -1) {
            bout.write(buf, 0, r);
        }
        return new String(bout.toByteArray(), StandardCharsets.UTF_8);
    }

    // DataSource init
    private static void initializeDataSource() throws Exception {
        com.mysql.cj.jdbc.MysqlDataSource ds = new com.mysql.cj.jdbc.MysqlDataSource();
        String url = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", DB_HOST, DB_PORT, DB_NAME);
        ds.setURL(url);
        ds.setUser(DB_USER);
        ds.setPassword(DB_PASS);
        dataSource = ds;
        // quick test
        try (java.sql.Connection c = dataSource.getConnection()) {
            System.out.println("Connected to MySQL: " + c.getMetaData().getURL());
        }
    }

    private static void ensureTables() throws Exception {
        String u = "CREATE TABLE IF NOT EXISTS users (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), role VARCHAR(100), username VARCHAR(100), password VARCHAR(100)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String t = "CREATE TABLE IF NOT EXISTS transactions (id INT AUTO_INCREMENT PRIMARY KEY, description VARCHAR(255), amount INT, category VARCHAR(32), owner VARCHAR(64), status VARCHAR(32), time VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String w = "CREATE TABLE IF NOT EXISTS warnings (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255), msg TEXT, severity VARCHAR(50), status VARCHAR(50), assignee VARCHAR(64), handler VARCHAR(64), notify_status VARCHAR(32), handled_at VARCHAR(64), triggered_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String wl = "CREATE TABLE IF NOT EXISTS warning_logs (id INT AUTO_INCREMENT PRIMARY KEY, warning_id INT, action VARCHAR(64), actor VARCHAR(64), note VARCHAR(255), created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String im = "CREATE TABLE IF NOT EXISTS industry_metrics (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), value_num INT, unit VARCHAR(32), updated_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String md = "CREATE TABLE IF NOT EXISTS map_data (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), content LONGTEXT, map_type VARCHAR(16), created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String rs = "CREATE TABLE IF NOT EXISTS residents (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), address VARCHAR(255), phone VARCHAR(64), x_num INT, y_num INT) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String ai = "CREATE TABLE IF NOT EXISTS ai_records (id INT AUTO_INCREMENT PRIMARY KEY, type VARCHAR(32), question TEXT, answer TEXT, created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String oa = "CREATE TABLE IF NOT EXISTS ops_audit (id INT AUTO_INCREMENT PRIMARY KEY, action_desc VARCHAR(255), actor VARCHAR(64), status VARCHAR(32), created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String om = "CREATE TABLE IF NOT EXISTS ops_monitor (id INT AUTO_INCREMENT PRIMARY KEY, metric_name VARCHAR(64), metric_value VARCHAR(64), status VARCHAR(32), created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String oh = "CREATE TABLE IF NOT EXISTS ops_health (id INT AUTO_INCREMENT PRIMARY KEY, service_name VARCHAR(64), status VARCHAR(32), detail VARCHAR(255), checked_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String ol = "CREATE TABLE IF NOT EXISTS ops_logs (id INT AUTO_INCREMENT PRIMARY KEY, level VARCHAR(16), source VARCHAR(64), message TEXT, created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String ob = "CREATE TABLE IF NOT EXISTS ops_backups (id INT AUTO_INCREMENT PRIMARY KEY, target VARCHAR(128), backup_type VARCHAR(32), status VARCHAR(32), operator VARCHAR(64), started_at VARCHAR(64), finished_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String orr = "CREATE TABLE IF NOT EXISTS ops_restores (id INT AUTO_INCREMENT PRIMARY KEY, backup_id INT, status VARCHAR(32), operator VARCHAR(64), started_at VARCHAR(64), finished_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String ak = "CREATE TABLE IF NOT EXISTS api_keys (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64), key_value TEXT, created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String gt = "CREATE TABLE IF NOT EXISTS gov_tasks (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255), description TEXT, assignee VARCHAR(64), status VARCHAR(32), due_at VARCHAR(64), created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String gc = "CREATE TABLE IF NOT EXISTS gov_checkins (id INT AUTO_INCREMENT PRIMARY KEY, task_id INT, user_name VARCHAR(64), note VARCHAR(255), checkin_time VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String ga = "CREATE TABLE IF NOT EXISTS gov_acceptance (id INT AUTO_INCREMENT PRIMARY KEY, task_id INT, result VARCHAR(32), reviewer VARCHAR(64), note VARCHAR(255), accepted_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String gpr = "CREATE TABLE IF NOT EXISTS gov_point_rules (id INT AUTO_INCREMENT PRIMARY KEY, rule_name VARCHAR(255), points INT, status VARCHAR(32), created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String gpa = "CREATE TABLE IF NOT EXISTS gov_point_audit (id INT AUTO_INCREMENT PRIMARY KEY, user_name VARCHAR(64), rule_name VARCHAR(255), points INT, status VARCHAR(32), applied_at VARCHAR(64), approved_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String gact = "CREATE TABLE IF NOT EXISTS gov_activities (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255), organizer VARCHAR(64), status VARCHAR(32), start_at VARCHAR(64), end_at VARCHAR(64), created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String fb = "CREATE TABLE IF NOT EXISTS feedback_items (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255), content TEXT, reporter VARCHAR(64), type VARCHAR(32), status VARCHAR(32), created_at VARCHAR(64), updated_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String ff = "CREATE TABLE IF NOT EXISTS feedback_flow (id INT AUTO_INCREMENT PRIMARY KEY, step_name VARCHAR(255), owner VARCHAR(64), status VARCHAR(32), updated_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String fa = "CREATE TABLE IF NOT EXISTS feedback_announcements (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255), content TEXT, publisher VARCHAR(64), status VARCHAR(32), published_at VARCHAR(64), created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (java.sql.Connection c = dataSource.getConnection(); java.sql.Statement s = c.createStatement()){
            s.execute(u); s.execute(t); s.execute(w); s.execute(wl); s.execute(im); s.execute(md); s.execute(rs); s.execute(ai); s.execute(oa); s.execute(om); s.execute(oh); s.execute(ol); s.execute(ob); s.execute(orr); s.execute(ak);
            s.execute(gt); s.execute(gc); s.execute(ga); s.execute(gpr); s.execute(gpa); s.execute(gact);
            s.execute(fb); s.execute(ff); s.execute(fa);
        }
    }

    private static void ensureColumns() throws Exception {
        try (java.sql.Connection c = dataSource.getConnection(); java.sql.Statement s = c.createStatement()){
            try { s.execute("ALTER TABLE users ADD COLUMN username VARCHAR(100)"); } catch (Exception ignored) {}
            try { s.execute("ALTER TABLE users ADD COLUMN password VARCHAR(100)"); } catch (Exception ignored) {}
            try { s.execute("ALTER TABLE ai_records ADD COLUMN type VARCHAR(32)"); } catch (Exception ignored) {}
            try { s.execute("ALTER TABLE transactions ADD COLUMN category VARCHAR(32)"); } catch (Exception ignored) {}
            try { s.execute("ALTER TABLE transactions ADD COLUMN owner VARCHAR(64)"); } catch (Exception ignored) {}
            try { s.execute("ALTER TABLE transactions ADD COLUMN status VARCHAR(32)"); } catch (Exception ignored) {}
            try { s.execute("ALTER TABLE warnings ADD COLUMN assignee VARCHAR(64)"); } catch (Exception ignored) {}
            try { s.execute("ALTER TABLE warnings ADD COLUMN handler VARCHAR(64)"); } catch (Exception ignored) {}
            try { s.execute("ALTER TABLE warnings ADD COLUMN notify_status VARCHAR(32)"); } catch (Exception ignored) {}
            try { s.execute("ALTER TABLE warnings ADD COLUMN handled_at VARCHAR(64)"); } catch (Exception ignored) {}
            try { s.execute("ALTER TABLE map_data ADD COLUMN map_type VARCHAR(16)"); } catch (Exception ignored) {}
        }
    }

    private static void ensureTablesSafe(){
        if (tablesEnsured) return;
        synchronized (Application.class){
            if (tablesEnsured) return;
            try { ensureTables(); ensureColumns(); tablesEnsured = true; } catch (Exception e) { /* ignore and retry later */ }
        }
    }

    private static java.sql.Connection openConnection() throws Exception {
        if (dataSource == null) {
            initializeDataSource();
        }
        ensureTablesSafe();
        return dataSource.getConnection();
    }

    private static String loadSvgFromFile(){
        String[] candidates = new String[]{
                "地图.svg",
                Paths.get(System.getProperty("user.dir"), "地图.svg").toString(),
                Paths.get(System.getProperty("user.dir"), "..", "地图.svg").toString(),
                Paths.get(System.getProperty("user.dir"), "..", "..", "地图.svg").toString()
        };
        for (String p : candidates){
            try {
                Path path = Paths.get(p).toAbsolutePath().normalize();
                if (Files.exists(path)){
                    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String loadSvgFromFile(){
        String[] candidates = new String[]{
                "1.svg",
                Paths.get(System.getProperty("user.dir"), "1.svg").toString(),
                Paths.get(System.getProperty("user.dir"), "..", "1.svg").toString(),
                Paths.get(System.getProperty("user.dir"), "..", "..", "1.svg").toString()
        };
        for (String p : candidates){
            try {
                Path path = Paths.get(p).toAbsolutePath().normalize();
                if (Files.exists(path)){
                    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static void ensureSampleMapData(java.sql.Connection c){
        try {
<<<<<<< HEAD
            String sampleGeo = loadGeoJsonFromFile();
            if (sampleGeo != null && !sampleGeo.trim().isEmpty()){
                try (java.sql.PreparedStatement ps = c.prepareStatement("SELECT id FROM map_data WHERE name=? ORDER BY id DESC LIMIT 1")){
                    ps.setString(1, "雨湖区示例地图");
                    java.sql.ResultSet rs = ps.executeQuery();
                    if (rs.next()){
                        int id = rs.getInt("id");
                        try (java.sql.PreparedStatement ups = c.prepareStatement("UPDATE map_data SET content=?, created_at=? WHERE id=?")){
                            ups.setString(1, sampleGeo);
                            ups.setString(2, java.time.Instant.now().toString());
                            ups.setInt(3, id);
                            ups.executeUpdate();
                        }
                    } else {
                        try (java.sql.PreparedStatement ins = c.prepareStatement("INSERT INTO map_data (name,content,created_at) VALUES (?,?,?)")){
                            ins.setString(1, "雨湖区示例地图");
                            ins.setString(2, sampleGeo);
                            ins.setString(3, java.time.Instant.now().toString());
                            ins.executeUpdate();
                        }
=======
            String sampleSvg = loadSvgFromFile();
            if (sampleSvg == null || sampleSvg.trim().isEmpty()) return;
            try (java.sql.PreparedStatement ps = c.prepareStatement("SELECT id FROM map_data WHERE name=? ORDER BY id DESC LIMIT 1")){
                ps.setString(1, "主目录地图");
                java.sql.ResultSet rs = ps.executeQuery();
                if (rs.next()){
                    int id = rs.getInt("id");
                    try (java.sql.PreparedStatement ups = c.prepareStatement("UPDATE map_data SET content=?, map_type=?, created_at=? WHERE id=?")){
                        ups.setString(1, sampleSvg);
                        ups.setString(2, "svg");
                        ups.setString(3, java.time.Instant.now().toString());
                        ups.setInt(4, id);
                        ups.executeUpdate();
>>>>>>> 3a01a2e11c406534f45cd83922e505d01c297aaa
                    }
                }
            }
<<<<<<< HEAD

            String sampleSvg = loadSvgFromFile();
            if (sampleSvg != null && !sampleSvg.trim().isEmpty()){
                try (java.sql.PreparedStatement ps = c.prepareStatement("SELECT id FROM map_data WHERE name=? ORDER BY id DESC LIMIT 1")){
                    ps.setString(1, "1.svg");
                    java.sql.ResultSet rs = ps.executeQuery();
                    if (rs.next()){
                        int id = rs.getInt("id");
                        try (java.sql.PreparedStatement ups = c.prepareStatement("UPDATE map_data SET content=?, created_at=? WHERE id=?")){
                            ups.setString(1, sampleSvg);
                            ups.setString(2, java.time.Instant.now().toString());
                            ups.setInt(3, id);
                            ups.executeUpdate();
                        }
                    } else {
                        try (java.sql.PreparedStatement ins = c.prepareStatement("INSERT INTO map_data (name,content,created_at) VALUES (?,?,?)")){
                            ins.setString(1, "1.svg");
                            ins.setString(2, sampleSvg);
                            ins.setString(3, java.time.Instant.now().toString());
                            ins.executeUpdate();
                        }
                    }
                }
=======
            try (java.sql.PreparedStatement ins = c.prepareStatement("INSERT INTO map_data (name,content,map_type,created_at) VALUES (?,?,?,?)")){
                ins.setString(1, "主目录地图");
                ins.setString(2, sampleSvg);
                ins.setString(3, "svg");
                ins.setString(4, java.time.Instant.now().toString());
                ins.executeUpdate();
>>>>>>> 3a01a2e11c406534f45cd83922e505d01c297aaa
            }
        } catch (Exception ignored) {
        }
    }

    private static void seedIfEmpty() throws Exception {
        try (java.sql.Connection c = dataSource.getConnection(); java.sql.Statement s = c.createStatement()){
            java.sql.ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO users (name,role,username,password) VALUES ('张三','管理员',NULL,NULL),('李四','普通用户',NULL,NULL)");
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM users WHERE username='admin'"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO users (name,role,username,password) VALUES ('文磊','管理员','admin','123456')");
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM transactions"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO transactions (description,amount,time) VALUES ('初始化余额',1000,'"+java.time.Instant.now().toString()+"')");
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM warnings"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO warnings (title,msg,severity,status,triggered_at) VALUES ('高温','后山区域温度异常升高','高','未处理','"+java.time.Instant.now().toString()+"')");
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM industry_metrics"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO industry_metrics (name,value_num,unit,updated_at) VALUES ('基地产量',120,'吨','"+java.time.Instant.now().toString()+"'),('农业产值',560,'万元','"+java.time.Instant.now().toString()+"')");
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM map_data"); rs.next();
            int mapCount = rs.getInt(1);
<<<<<<< HEAD
            String sampleGeo = loadGeoJsonFromFile();
            String sampleSvg = loadSvgFromFile();
            if (mapCount == 0){
                String content = sampleSvg != null ? sampleSvg : (sampleGeo != null ? sampleGeo : "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"name\":\"村域\"},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1000,0],[1000,600],[0,600],[0,0]]]}}]}");
                try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO map_data (name,content,created_at) VALUES (?,?,?)")){
                    if (sampleSvg != null) ps.setString(1, "1.svg");
                    else ps.setString(1, sampleGeo != null ? "雨湖区示例地图" : "默认村落地图");
                    ps.setString(2, content);
                    ps.setString(3, java.time.Instant.now().toString());
=======
            String sampleSvg = loadSvgFromFile();
            if (mapCount == 0){
                String svg = sampleSvg != null ? sampleSvg : "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1000 600\"><rect x=\"0\" y=\"0\" width=\"1000\" height=\"600\" fill=\"#e2e8f0\" stroke=\"#94a3b8\" stroke-width=\"2\"/></svg>";
                try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO map_data (name,content,map_type,created_at) VALUES (?,?,?,?)")){
                    ps.setString(1, sampleSvg != null ? "主目录地图" : "默认村落地图");
                    ps.setString(2, svg);
                    ps.setString(3, "svg");
                    ps.setString(4, java.time.Instant.now().toString());
>>>>>>> 3a01a2e11c406534f45cd83922e505d01c297aaa
                    ps.executeUpdate();
                }
            } else if (sampleSvg != null) {
                rs = s.executeQuery("SELECT COUNT(*) FROM map_data WHERE name='主目录地图'"); rs.next();
                if (rs.getInt(1) == 0){
                    try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO map_data (name,content,map_type,created_at) VALUES (?,?,?,?)")){
                        ps.setString(1, "主目录地图");
                        ps.setString(2, sampleSvg);
                        ps.setString(3, "svg");
                        ps.setString(4, java.time.Instant.now().toString());
                        ps.executeUpdate();
                    }
                }
            }
            if (sampleSvg != null) {
                rs = s.executeQuery("SELECT COUNT(*) FROM map_data WHERE name='1.svg'"); rs.next();
                if (rs.getInt(1) == 0){
                    try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO map_data (name,content,created_at) VALUES (?,?,?)")){
                        ps.setString(1, "1.svg");
                        ps.setString(2, sampleSvg);
                        ps.setString(3, java.time.Instant.now().toString());
                        ps.executeUpdate();
                    }
                }
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM residents"); rs.next(); if (rs.getInt(1)==0){
                // no default residents; user adds via map
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM ai_records"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO ai_records (question,answer,created_at) VALUES ('今日天气适合播种吗？','建议关注气象预警后开展播种。','"+java.time.Instant.now().toString()+"')");
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM ops_audit"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO ops_audit (action_desc,actor,status,created_at) VALUES ('登录系统','admin','成功','"+java.time.Instant.now().toString()+"'),('修改预警规则','admin','成功','"+java.time.Instant.now().toString()+"')");
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM ops_monitor"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO ops_monitor (metric_name,metric_value,status,created_at) VALUES " +
                        "('CPU 使用率','32%','正常','"+java.time.Instant.now().toString()+"')," +
                        "('内存占用','58%','正常','"+java.time.Instant.now().toString()+"')," +
                        "('磁盘占用','71%','关注','"+java.time.Instant.now().toString()+"')");
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM ops_health"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO ops_health (service_name,status,detail,checked_at) VALUES " +
                        "('后端服务','正常','端口 8080 可用','"+java.time.Instant.now().toString()+"')," +
                        "('数据库','正常','MySQL 连接正常','"+java.time.Instant.now().toString()+"')");
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM ops_logs"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO ops_logs (level,source,message,created_at) VALUES " +
                        "('INFO','system','系统启动完成','"+java.time.Instant.now().toString()+"')," +
                        "('WARN','backup','备份空间不足预警','"+java.time.Instant.now().toString()+"')");
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM ops_backups"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO ops_backups (target,backup_type,status,operator,started_at,finished_at) VALUES " +
                        "('village_db','全量','成功','admin','"+java.time.Instant.now().toString()+"','"+java.time.Instant.now().toString()+"')");
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM ops_restores"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO ops_restores (backup_id,status,operator,started_at,finished_at) VALUES " +
                        "(1,'成功','admin','"+java.time.Instant.now().toString()+"','"+java.time.Instant.now().toString()+"')");
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM api_keys WHERE name='deepseek'"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO api_keys (name,key_value,created_at) VALUES ('deepseek','sk-15e1de1fac9146ef885da23e36669a4c','"+java.time.Instant.now().toString()+"')");
            }
        }
    }

    private static void writeJson(HttpExchange ex, int status, String json) throws IOException {
        addCorsHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static void writeText(HttpExchange ex, int status, String txt) throws IOException {
        addCorsHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        byte[] b = txt.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    // add basic CORS headers
    private static void addCorsHeaders(HttpExchange ex){
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    // Users root handler
    private static void handleUsersRoot(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)) {
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,name,role,username,password FROM users");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"name\":\"").append(escape(rs.getString("name"))).append("\",")
                      .append("\"role\":\"").append(escape(rs.getString("role"))).append("\",")
                      .append("\"username\":\"").append(escape(rs.getString("username"))).append("\",")
                      .append("\"password\":\"").append(escape(rs.getString("password"))).append("\"")
                      .append('}');
                }
                sb.append(']');
                writeJson(ex,200,sb.toString());
                return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }

        if ("POST".equals(method)){
            String body = readBody(ex);
            String name = extractJsonString(body, "name");
            String role = extractJsonString(body, "role");
            String username = extractJsonString(body, "username");
            String password = extractJsonString(body, "password");
            String passwordHash = password == null ? null : hashPassword(password);
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO users (name,role,username,password) VALUES (?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, name==null?"用户":name);
                ps.setString(2, role==null?"普通用户":role);
                ps.setString(3, username);
                ps.setString(4, passwordHash);
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id = -1; if (g.next()) id = g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"name\":\""+escape(name==null?"用户":name)+"\",\"role\":\""+escape(role==null?"普通用户":role)+"\"}");
                return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }

        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleUserById(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath(); // /api/users/{id}
        String idStr = path.replaceFirst(".*/api/users/","");
        int id = -1;
        try { id = Integer.parseInt(idStr); } catch(Exception e){ writeText(ex,400,"invalid id"); return; }
        Map<String,Object> found = null;
        try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("SELECT id,name,role,username,password FROM users WHERE id=?")){
            ps.setInt(1,id);
            java.sql.ResultSet rs = ps.executeQuery();
            if (rs.next()){
                found = new HashMap<>();
                found.put("id", rs.getInt("id"));
                found.put("name", rs.getString("name"));
                found.put("role", rs.getString("role"));
                found.put("username", rs.getString("username"));
                found.put("password", rs.getString("password"));
            }
        } catch(Exception exx){ writeText(ex,500,"db error: "+exx.getMessage()); return; }
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            if (found==null) { writeText(ex,404,"not found"); return; }
            String name = found.get("name")==null?"":String.valueOf(found.get("name"));
            String role = found.get("role")==null?"":String.valueOf(found.get("role"));
            String username = found.get("username")==null?"":String.valueOf(found.get("username"));
            String password = found.get("password")==null?"":String.valueOf(found.get("password"));
            String json = "{\"id\":"+found.get("id")+",\"name\":\""+escape(name)+"\",\"role\":\""+escape(role)+"\",\"username\":\""+escape(username)+"\",\"password\":\""+escape(password)+"\"}";
            writeJson(ex,200,json); return;
        }
        if ("PUT".equals(method)){
            if (found==null) { writeText(ex,404,"not found"); return; }
            String body = readBody(ex);
            String name = extractJsonField(body, "name");
            String role = extractJsonField(body, "role");
            String username = extractJsonField(body, "username");
            String password = extractJsonField(body, "password");
            String oldName = found.get("name")==null?"":String.valueOf(found.get("name"));
            String oldRole = found.get("role")==null?"":String.valueOf(found.get("role"));
            String oldUsername = found.get("username")==null?null:String.valueOf(found.get("username"));
            String oldPassword = found.get("password")==null?null:String.valueOf(found.get("password"));
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("UPDATE users SET name=?,role=?,username=?,password=? WHERE id=?")){
                ps.setString(1, name==null?oldName:name);
                ps.setString(2, role==null?oldRole:role);
                ps.setString(3, username==null?oldUsername:username);
                ps.setString(4, password==null?oldPassword:password);
                ps.setInt(5, id);
                ps.executeUpdate();
                writeJson(ex,200,"{\"ok\":true}"); return;
            } catch(Exception exx){ writeText(ex,500,"db error: "+exx.getMessage()); return; }
        }
        if ("DELETE".equals(method)){
            if (found==null) { writeText(ex,404,"not found"); return; }
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id=?")){
                ps.setInt(1,id); ps.executeUpdate(); writeText(ex,204,""); return;
            } catch(Exception exx){ writeText(ex,500,"db error: "+exx.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleTransactions(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,description,amount,category,owner,status,time FROM transactions ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"description\":\"").append(escape(rs.getString("description"))).append("\",")
                      .append("\"amount\":").append(rs.getInt("amount")).append(',')
                      .append("\"category\":\"").append(escape(rs.getString("category"))).append("\",")
                      .append("\"owner\":\"").append(escape(rs.getString("owner"))).append("\",")
                      .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                      .append("\"time\":\"").append(escape(rs.getString("time"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }

        if ("POST".equals(method)){
            String body = readBody(ex);
            String description = extractJsonField(body, "description");
            if (description == null) description = extractJsonField(body, "desc");
            String amount = extractJsonField(body, "amount");
            String category = extractJsonField(body, "category");
            String owner = extractJsonField(body, "owner");
            String status = extractJsonField(body, "status");
            int amountValue = 0;
            try { amountValue = amount==null?0:Integer.parseInt(amount); } catch(Exception ignored) { amountValue = 0; }
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO transactions (description,amount,category,owner,status,time) VALUES (?,?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, description==null?"交易":description);
                ps.setInt(2, amountValue);
                ps.setString(3, category==null?"收入":category);
                ps.setString(4, owner);
                ps.setString(5, status==null?"待审核":status);
                ps.setString(6, java.time.Instant.now().toString());
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id = -1; if (g.next()) id = g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"description\":\""+escape(description==null?"交易":description)+"\",\"amount\":"+amountValue+",\"category\":\""+escape(category==null?"收入":category)+"\",\"owner\":\""+escape(owner)+"\",\"status\":\""+escape(status==null?"待审核":status)+"\"}");
                return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }

        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleTransactionById(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String path = ex.getRequestURI().getPath();
        String idStr = path.replaceFirst(".*/api/finance/transactions/", "");
        int id = -1;
        try { id = Integer.parseInt(idStr); } catch(Exception e){ writeText(ex,400,"invalid id"); return; }

        Map<String,Object> found = null;
        try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("SELECT id,description,amount,category,owner,status,time FROM transactions WHERE id=?")){
            ps.setInt(1, id);
            java.sql.ResultSet rs = ps.executeQuery();
            if (rs.next()){
                found = new HashMap<>();
                found.put("id", rs.getInt("id"));
                found.put("description", rs.getString("description"));
                found.put("amount", rs.getInt("amount"));
                found.put("category", rs.getString("category"));
                found.put("owner", rs.getString("owner"));
                found.put("status", rs.getString("status"));
                found.put("time", rs.getString("time"));
            }
        } catch(Exception exx){ writeText(ex,500,"db error: "+exx.getMessage()); return; }

        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            if (found==null) { writeText(ex,404,"not found"); return; }
            String json = "{\"id\":"+found.get("id")+",\"description\":\""+escape(found.get("description").toString())+"\",\"amount\":"+found.get("amount")+",\"category\":\""+escape(String.valueOf(found.get("category")))+"\",\"owner\":\""+escape(String.valueOf(found.get("owner")))+"\",\"status\":\""+escape(String.valueOf(found.get("status")))+"\",\"time\":\""+escape(String.valueOf(found.get("time")))+"\"}";
            writeJson(ex,200,json); return;
        }
        if ("PUT".equals(method)){
            if (found==null) { writeText(ex,404,"not found"); return; }
            String body = readBody(ex);
            String description = extractJsonField(body, "description");
            String amount = extractJsonField(body, "amount");
            String category = extractJsonField(body, "category");
            String owner = extractJsonField(body, "owner");
            String status = extractJsonField(body, "status");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("UPDATE transactions SET description=?,amount=?,category=?,owner=?,status=? WHERE id=?")){
                ps.setString(1, description==null?found.get("description").toString():description);
                int amountValue = (Integer)found.get("amount");
                try { amountValue = amount==null?amountValue:Integer.parseInt(amount); } catch(Exception ignored) {}
                ps.setInt(2, amountValue);
                ps.setString(3, category==null?String.valueOf(found.get("category")):category);
                ps.setString(4, owner==null?String.valueOf(found.get("owner")):owner);
                ps.setString(5, status==null?String.valueOf(found.get("status")):status);
                ps.setInt(6, id);
                ps.executeUpdate();
                writeJson(ex,200,"{\"ok\":true}"); return;
            } catch(Exception exx){ writeText(ex,500,"db error: "+exx.getMessage()); return; }
        }
        if ("DELETE".equals(method)){
            if (found==null) { writeText(ex,404,"not found"); return; }
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM transactions WHERE id=?")){
                ps.setInt(1, id);
                ps.executeUpdate();
                writeText(ex,204,""); return;
            } catch(Exception exx){ writeText(ex,500,"db error: "+exx.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleWarnings(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,title,msg,severity,status,assignee,handler,notify_status,handled_at,triggered_at FROM warnings ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"title\":\"").append(escape(rs.getString("title"))).append("\",")
                      .append("\"msg\":\"").append(escape(rs.getString("msg"))).append("\",")
                      .append("\"severity\":\"").append(escape(rs.getString("severity"))).append("\",")
                      .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                      .append("\"assignee\":\"").append(escape(rs.getString("assignee"))).append("\",")
                      .append("\"handler\":\"").append(escape(rs.getString("handler"))).append("\",")
                      .append("\"notify_status\":\"").append(escape(rs.getString("notify_status"))).append("\",")
                      .append("\"handled_at\":\"").append(escape(rs.getString("handled_at"))).append("\",")
                      .append("\"triggered_at\":\"").append(escape(rs.getString("triggered_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String title = extractJsonField(body,"title");
            String msg = extractJsonField(body,"msg");
            String severity = extractJsonField(body,"severity");
            String assignee = extractJsonField(body,"assignee");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO warnings (title,msg,severity,status,assignee,notify_status,triggered_at) VALUES (?,?,?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, title==null?"未命名":title);
                ps.setString(2, msg==null?"":msg);
                ps.setString(3, severity==null?"中":severity);
                ps.setString(4, "未处理");
                ps.setString(5, assignee);
                ps.setString(6, "未通知");
                ps.setString(7, java.time.Instant.now().toString());
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id = -1; if (g.next()) id = g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"title\":\""+escape(title==null?"未命名":title)+"\",\"msg\":\""+escape(msg==null?"":msg)+"\"}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleWarningRules(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        String body = readBody(ex);
        String rule = extractJsonField(body, "rule");
        try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO warnings (title,msg,severity,status,triggered_at) VALUES (?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
            ps.setString(1, rule==null?"rule":rule); ps.setString(2, "rule-created"); ps.setString(3, "中"); ps.setString(4, "未生效"); ps.setString(5, java.time.Instant.now().toString()); ps.executeUpdate();
            java.sql.ResultSet g = ps.getGeneratedKeys(); int id = -1; if (g.next()) id = g.getInt(1);
            writeJson(ex,201,"{\"id\":"+id+",\"rule\":\""+escape(rule==null?"rule":rule)+"\"}"); return;
        } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
    }

    private static void handleWarningLogs(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            String action = getQueryParam(ex, "action");
            String warningId = getQueryParam(ex, "warning_id");
            String sql = "SELECT id,warning_id,action,actor,note,created_at FROM warning_logs";
            List<Object> params = new ArrayList<>();
            if ((action!=null && !action.isEmpty()) || (warningId!=null && !warningId.isEmpty())){
                StringBuilder where = new StringBuilder();
                if (action!=null && !action.isEmpty()) { where.append("action=?"); params.add(action); }
                if (warningId!=null && !warningId.isEmpty()) { if (where.length()>0) where.append(" AND "); where.append("warning_id=?"); params.add(Integer.parseInt(warningId)); }
                sql += " WHERE " + where;
            }
            sql += " ORDER BY id DESC";
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement(sql)){
                for (int i=0;i<params.size();i++) ps.setObject(i+1, params.get(i));
                java.sql.ResultSet rs = ps.executeQuery();
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"warning_id\":").append(rs.getInt("warning_id")).append(',')
                      .append("\"action\":\"").append(escape(rs.getString("action"))).append("\",")
                      .append("\"actor\":\"").append(escape(rs.getString("actor"))).append("\",")
                      .append("\"note\":\"").append(escape(rs.getString("note"))).append("\",")
                      .append("\"created_at\":\"").append(escape(rs.getString("created_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String warningId = extractJsonField(body, "warning_id");
            String action = extractJsonField(body, "action");
            String actor = extractJsonField(body, "actor");
            String note = extractJsonField(body, "note");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO warning_logs (warning_id,action,actor,note,created_at) VALUES (?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setInt(1, warningId==null?0:Integer.parseInt(warningId));
                ps.setString(2, action==null?"":action);
                ps.setString(3, actor);
                ps.setString(4, note);
                ps.setString(5, java.time.Instant.now().toString());
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id = -1; if (g.next()) id = g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"ok\":true}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleWarningStats(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!"GET".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
            java.sql.ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM warnings"); rs.next(); int total = rs.getInt(1);
            rs = s.executeQuery("SELECT COUNT(*) FROM warnings WHERE status='未处理'"); rs.next(); int pending = rs.getInt(1);
            rs = s.executeQuery("SELECT COUNT(*) FROM warnings WHERE status='已处理'"); rs.next(); int handled = rs.getInt(1);
            rs = s.executeQuery("SELECT COUNT(*) FROM warnings WHERE severity='高'"); rs.next(); int high = rs.getInt(1);
            rs = s.executeQuery("SELECT COUNT(*) FROM warnings WHERE severity='中'"); rs.next(); int mid = rs.getInt(1);
            rs = s.executeQuery("SELECT COUNT(*) FROM warnings WHERE severity='低'"); rs.next(); int low = rs.getInt(1);
            String json = "{\"total\":"+total+",\"pending\":"+pending+",\"handled\":"+handled+",\"severity\":{\"high\":"+high+",\"mid\":"+mid+",\"low\":"+low+"}}";
            writeJson(ex,200,json); return;
        } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); }
    }

    private static void handleGovTasks(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,title,description,assignee,status,due_at,created_at FROM gov_tasks ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"title\":\"").append(escape(rs.getString("title"))).append("\",")
                      .append("\"description\":\"").append(escape(rs.getString("description"))).append("\",")
                      .append("\"assignee\":\"").append(escape(rs.getString("assignee"))).append("\",")
                      .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                      .append("\"due_at\":\"").append(escape(rs.getString("due_at"))).append("\",")
                      .append("\"created_at\":\"").append(escape(rs.getString("created_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String title = extractJsonField(body, "title");
            String description = extractJsonField(body, "description");
            String assignee = extractJsonField(body, "assignee");
            String status = extractJsonField(body, "status");
            String dueAt = extractJsonField(body, "due_at");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO gov_tasks (title,description,assignee,status,due_at,created_at) VALUES (?,?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, title==null?"任务":title);
                ps.setString(2, description==null?"":description);
                ps.setString(3, assignee);
                ps.setString(4, status==null?"待执行":status);
                ps.setString(5, dueAt);
                ps.setString(6, java.time.Instant.now().toString());
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id=-1; if (g.next()) id=g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"title\":\""+escape(title==null?"任务":title)+"\"}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleGovTaskById(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String path = ex.getRequestURI().getPath();
        String idStr = path.replaceFirst(".*/api/gov/tasks/", "");
        int id = -1;
        try { id = Integer.parseInt(idStr); } catch(Exception e){ writeText(ex,400,"invalid id"); return; }
        String method = ex.getRequestMethod();
        if ("PUT".equals(method)){
            String body = readBody(ex);
            String title = extractJsonField(body, "title");
            String description = extractJsonField(body, "description");
            String assignee = extractJsonField(body, "assignee");
            String status = extractJsonField(body, "status");
            String dueAt = extractJsonField(body, "due_at");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("UPDATE gov_tasks SET title=COALESCE(?,title), description=COALESCE(?,description), assignee=COALESCE(?,assignee), status=COALESCE(?,status), due_at=COALESCE(?,due_at) WHERE id=?")){
                ps.setString(1, title);
                ps.setString(2, description);
                ps.setString(3, assignee);
                ps.setString(4, status);
                ps.setString(5, dueAt);
                ps.setInt(6, id);
                int rows = ps.executeUpdate();
                if (rows==0){ writeText(ex,404,"not found"); return; }
                writeJson(ex,200,"{\"ok\":true}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("DELETE".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM gov_tasks WHERE id=?")){
                ps.setInt(1, id);
                int rows = ps.executeUpdate();
                if (rows==0){ writeText(ex,404,"not found"); return; }
                writeText(ex,204,""); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleGovCheckins(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,task_id,user_name,note,checkin_time FROM gov_checkins ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"task_id\":").append(rs.getInt("task_id")).append(',')
                      .append("\"user_name\":\"").append(escape(rs.getString("user_name"))).append("\",")
                      .append("\"note\":\"").append(escape(rs.getString("note"))).append("\",")
                      .append("\"checkin_time\":\"").append(escape(rs.getString("checkin_time"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String taskId = extractJsonField(body, "task_id");
            String user = extractJsonField(body, "user_name");
            String note = extractJsonField(body, "note");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO gov_checkins (task_id,user_name,note,checkin_time) VALUES (?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setInt(1, taskId==null?0:Integer.parseInt(taskId));
                ps.setString(2, user);
                ps.setString(3, note);
                ps.setString(4, java.time.Instant.now().toString());
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id=-1; if (g.next()) id=g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"ok\":true}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleGovAcceptance(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,task_id,result,reviewer,note,accepted_at FROM gov_acceptance ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"task_id\":").append(rs.getInt("task_id")).append(',')
                      .append("\"result\":\"").append(escape(rs.getString("result"))).append("\",")
                      .append("\"reviewer\":\"").append(escape(rs.getString("reviewer"))).append("\",")
                      .append("\"note\":\"").append(escape(rs.getString("note"))).append("\",")
                      .append("\"accepted_at\":\"").append(escape(rs.getString("accepted_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String taskId = extractJsonField(body, "task_id");
            String result = extractJsonField(body, "result");
            String reviewer = extractJsonField(body, "reviewer");
            String note = extractJsonField(body, "note");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO gov_acceptance (task_id,result,reviewer,note,accepted_at) VALUES (?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setInt(1, taskId==null?0:Integer.parseInt(taskId));
                ps.setString(2, result==null?"通过":result);
                ps.setString(3, reviewer);
                ps.setString(4, note);
                ps.setString(5, java.time.Instant.now().toString());
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id=-1; if (g.next()) id=g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"ok\":true}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleGovPointRules(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,rule_name,points,status,created_at FROM gov_point_rules ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"rule_name\":\"").append(escape(rs.getString("rule_name"))).append("\",")
                      .append("\"points\":").append(rs.getInt("points")).append(',')
                      .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                      .append("\"created_at\":\"").append(escape(rs.getString("created_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String name = extractJsonField(body, "rule_name");
            String points = extractJsonField(body, "points");
            String status = extractJsonField(body, "status");
            int p = 0; try { p = points==null?0:Integer.parseInt(points); } catch(Exception ignored) {}
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO gov_point_rules (rule_name,points,status,created_at) VALUES (?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, name==null?"规则":name);
                ps.setInt(2, p);
                ps.setString(3, status==null?"启用":status);
                ps.setString(4, java.time.Instant.now().toString());
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id=-1; if (g.next()) id=g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"ok\":true}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleGovPointRuleById(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String path = ex.getRequestURI().getPath();
        String idStr = path.replaceFirst(".*/api/gov/point-rules/", "");
        int id = -1;
        try { id = Integer.parseInt(idStr); } catch(Exception e){ writeText(ex,400,"invalid id"); return; }
        if (!"PUT".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        String body = readBody(ex);
        String status = extractJsonField(body, "status");
        try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("UPDATE gov_point_rules SET status=COALESCE(?,status) WHERE id=?")){
            ps.setString(1, status);
            ps.setInt(2, id);
            int rows = ps.executeUpdate();
            if (rows==0){ writeText(ex,404,"not found"); return; }
            writeJson(ex,200,"{\"ok\":true}");
        } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); }
    }

    private static void handleGovPointAudit(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,user_name,rule_name,points,status,applied_at,approved_at FROM gov_point_audit ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"user_name\":\"").append(escape(rs.getString("user_name"))).append("\",")
                      .append("\"rule_name\":\"").append(escape(rs.getString("rule_name"))).append("\",")
                      .append("\"points\":").append(rs.getInt("points")).append(',')
                      .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                      .append("\"applied_at\":\"").append(escape(rs.getString("applied_at"))).append("\",")
                      .append("\"approved_at\":\"").append(escape(rs.getString("approved_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String user = extractJsonField(body, "user_name");
            String rule = extractJsonField(body, "rule_name");
            String points = extractJsonField(body, "points");
            int p=0; try { p = points==null?0:Integer.parseInt(points); } catch(Exception ignored) {}
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO gov_point_audit (user_name,rule_name,points,status,applied_at) VALUES (?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, user);
                ps.setString(2, rule);
                ps.setInt(3, p);
                ps.setString(4, "待审核");
                ps.setString(5, java.time.Instant.now().toString());
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id=-1; if (g.next()) id=g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"ok\":true}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleGovPointAuditById(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String path = ex.getRequestURI().getPath();
        String idStr = path.replaceFirst(".*/api/gov/point-audit/", "");
        int id = -1;
        try { id = Integer.parseInt(idStr); } catch(Exception e){ writeText(ex,400,"invalid id"); return; }
        if (!"PUT".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        String body = readBody(ex);
        String status = extractJsonField(body, "status");
        String approvedAt = extractJsonField(body, "approved_at");
        try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("UPDATE gov_point_audit SET status=COALESCE(?,status), approved_at=COALESCE(?,approved_at) WHERE id=?")){
            ps.setString(1, status);
            ps.setString(2, approvedAt);
            ps.setInt(3, id);
            int rows = ps.executeUpdate();
            if (rows==0){ writeText(ex,404,"not found"); return; }
            writeJson(ex,200,"{\"ok\":true}");
        } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); }
    }

    private static void handleGovActivities(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,title,organizer,status,start_at,end_at,created_at FROM gov_activities ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"title\":\"").append(escape(rs.getString("title"))).append("\",")
                      .append("\"organizer\":\"").append(escape(rs.getString("organizer"))).append("\",")
                      .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                      .append("\"start_at\":\"").append(escape(rs.getString("start_at"))).append("\",")
                      .append("\"end_at\":\"").append(escape(rs.getString("end_at"))).append("\",")
                      .append("\"created_at\":\"").append(escape(rs.getString("created_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String title = extractJsonField(body, "title");
            String organizer = extractJsonField(body, "organizer");
            String status = extractJsonField(body, "status");
            String startAt = extractJsonField(body, "start_at");
            String endAt = extractJsonField(body, "end_at");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO gov_activities (title,organizer,status,start_at,end_at,created_at) VALUES (?,?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, title==null?"活动":title);
                ps.setString(2, organizer);
                ps.setString(3, status==null?"筹备":status);
                ps.setString(4, startAt);
                ps.setString(5, endAt);
                ps.setString(6, java.time.Instant.now().toString());
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id=-1; if (g.next()) id=g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"ok\":true}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleGovActivityById(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String path = ex.getRequestURI().getPath();
        String idStr = path.replaceFirst(".*/api/gov/activities/", "");
        int id = -1;
        try { id = Integer.parseInt(idStr); } catch(Exception e){ writeText(ex,400,"invalid id"); return; }
        String method = ex.getRequestMethod();
        if ("PUT".equals(method)){
            String body = readBody(ex);
            String title = extractJsonField(body, "title");
            String organizer = extractJsonField(body, "organizer");
            String status = extractJsonField(body, "status");
            String startAt = extractJsonField(body, "start_at");
            String endAt = extractJsonField(body, "end_at");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("UPDATE gov_activities SET title=COALESCE(?,title), organizer=COALESCE(?,organizer), status=COALESCE(?,status), start_at=COALESCE(?,start_at), end_at=COALESCE(?,end_at) WHERE id=?")){
                ps.setString(1, title);
                ps.setString(2, organizer);
                ps.setString(3, status);
                ps.setString(4, startAt);
                ps.setString(5, endAt);
                ps.setInt(6, id);
                int rows = ps.executeUpdate();
                if (rows==0){ writeText(ex,404,"not found"); return; }
                writeJson(ex,200,"{\"ok\":true}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("DELETE".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM gov_activities WHERE id=?")){
                ps.setInt(1, id);
                int rows = ps.executeUpdate();
                if (rows==0){ writeText(ex,404,"not found"); return; }
                writeText(ex,204,""); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleFeedbackItems(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            String status = getQueryParam(ex, "status");
            String type = getQueryParam(ex, "type");
            String sql = "SELECT id,title,content,reporter,type,status,created_at,updated_at FROM feedback_items";
            List<Object> params = new ArrayList<>();
            if ((status!=null && !status.isEmpty()) || (type!=null && !type.isEmpty())){
                StringBuilder where = new StringBuilder();
                if (status!=null && !status.isEmpty()) { where.append("status=?"); params.add(status); }
                if (type!=null && !type.isEmpty()) { if (where.length()>0) where.append(" AND "); where.append("type=?"); params.add(type); }
                sql += " WHERE " + where;
            }
            sql += " ORDER BY id DESC";
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement(sql)){
                for (int i=0;i<params.size();i++) ps.setObject(i+1, params.get(i));
                java.sql.ResultSet rs = ps.executeQuery();
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"title\":\"").append(escape(rs.getString("title"))).append("\",")
                      .append("\"content\":\"").append(escape(rs.getString("content"))).append("\",")
                      .append("\"reporter\":\"").append(escape(rs.getString("reporter"))).append("\",")
                      .append("\"type\":\"").append(escape(rs.getString("type"))).append("\",")
                      .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                      .append("\"created_at\":\"").append(escape(rs.getString("created_at"))).append("\",")
                      .append("\"updated_at\":\"").append(escape(rs.getString("updated_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String title = extractJsonField(body, "title");
            String content = extractJsonField(body, "content");
            String reporter = extractJsonField(body, "reporter");
            String type = extractJsonField(body, "type");
            String status = extractJsonField(body, "status");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO feedback_items (title,content,reporter,type,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, title==null?"反馈":title);
                ps.setString(2, content==null?"":content);
                ps.setString(3, reporter);
                ps.setString(4, type==null?"民情反馈":type);
                ps.setString(5, status==null?"待处理":status);
                String now = java.time.Instant.now().toString();
                ps.setString(6, now);
                ps.setString(7, now);
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id=-1; if (g.next()) id=g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"ok\":true}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleFeedbackItemById(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String path = ex.getRequestURI().getPath();
        String idStr = path.replaceFirst(".*/api/feedback/items/", "");
        int id = -1;
        try { id = Integer.parseInt(idStr); } catch(Exception e){ writeText(ex,400,"invalid id"); return; }
        String method = ex.getRequestMethod();
        if ("PUT".equals(method)){
            String body = readBody(ex);
            String title = extractJsonField(body, "title");
            String content = extractJsonField(body, "content");
            String reporter = extractJsonField(body, "reporter");
            String type = extractJsonField(body, "type");
            String status = extractJsonField(body, "status");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("UPDATE feedback_items SET title=COALESCE(?,title), content=COALESCE(?,content), reporter=COALESCE(?,reporter), type=COALESCE(?,type), status=COALESCE(?,status), updated_at=? WHERE id=?")){
                ps.setString(1, title);
                ps.setString(2, content);
                ps.setString(3, reporter);
                ps.setString(4, type);
                ps.setString(5, status);
                ps.setString(6, java.time.Instant.now().toString());
                ps.setInt(7, id);
                int rows = ps.executeUpdate();
                if (rows==0){ writeText(ex,404,"not found"); return; }
                writeJson(ex,200,"{\"ok\":true}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("DELETE".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM feedback_items WHERE id=?")){
                ps.setInt(1, id);
                int rows = ps.executeUpdate();
                if (rows==0){ writeText(ex,404,"not found"); return; }
                writeText(ex,204,""); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleFeedbackFlow(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,step_name,owner,status,updated_at FROM feedback_flow ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"step_name\":\"").append(escape(rs.getString("step_name"))).append("\",")
                      .append("\"owner\":\"").append(escape(rs.getString("owner"))).append("\",")
                      .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                      .append("\"updated_at\":\"").append(escape(rs.getString("updated_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String step = extractJsonField(body, "step_name");
            String owner = extractJsonField(body, "owner");
            String status = extractJsonField(body, "status");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO feedback_flow (step_name,owner,status,updated_at) VALUES (?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, step==null?"流程":step);
                ps.setString(2, owner);
                ps.setString(3, status==null?"启用":status);
                ps.setString(4, java.time.Instant.now().toString());
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id=-1; if (g.next()) id=g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"ok\":true}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleFeedbackAnnouncements(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,title,content,publisher,status,published_at,created_at FROM feedback_announcements ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"title\":\"").append(escape(rs.getString("title"))).append("\",")
                      .append("\"content\":\"").append(escape(rs.getString("content"))).append("\",")
                      .append("\"publisher\":\"").append(escape(rs.getString("publisher"))).append("\",")
                      .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                      .append("\"published_at\":\"").append(escape(rs.getString("published_at"))).append("\",")
                      .append("\"created_at\":\"").append(escape(rs.getString("created_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String title = extractJsonField(body, "title");
            String content = extractJsonField(body, "content");
            String publisher = extractJsonField(body, "publisher");
            String status = extractJsonField(body, "status");
            String publishedAt = extractJsonField(body, "published_at");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO feedback_announcements (title,content,publisher,status,published_at,created_at) VALUES (?,?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, title==null?"公告":title);
                ps.setString(2, content==null?"":content);
                ps.setString(3, publisher);
                ps.setString(4, status==null?"草稿":status);
                ps.setString(5, publishedAt);
                ps.setString(6, java.time.Instant.now().toString());
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id=-1; if (g.next()) id=g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"ok\":true}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleFeedbackAnnouncementById(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String path = ex.getRequestURI().getPath();
        String idStr = path.replaceFirst(".*/api/feedback/announcements/", "");
        int id = -1;
        try { id = Integer.parseInt(idStr); } catch(Exception e){ writeText(ex,400,"invalid id"); return; }
        String method = ex.getRequestMethod();
        if ("PUT".equals(method)){
            String body = readBody(ex);
            String title = extractJsonField(body, "title");
            String content = extractJsonField(body, "content");
            String publisher = extractJsonField(body, "publisher");
            String status = extractJsonField(body, "status");
            String publishedAt = extractJsonField(body, "published_at");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("UPDATE feedback_announcements SET title=COALESCE(?,title), content=COALESCE(?,content), publisher=COALESCE(?,publisher), status=COALESCE(?,status), published_at=COALESCE(?,published_at) WHERE id=?")){
                ps.setString(1, title);
                ps.setString(2, content);
                ps.setString(3, publisher);
                ps.setString(4, status);
                ps.setString(5, publishedAt);
                ps.setInt(6, id);
                int rows = ps.executeUpdate();
                if (rows==0){ writeText(ex,404,"not found"); return; }
                writeJson(ex,200,"{\"ok\":true}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("DELETE".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM feedback_announcements WHERE id=?")){
                ps.setInt(1, id);
                int rows = ps.executeUpdate();
                if (rows==0){ writeText(ex,404,"not found"); return; }
                writeText(ex,204,""); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleFeedbackStats(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!"GET".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
            java.sql.ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM feedback_items"); rs.next(); int total = rs.getInt(1);
            rs = s.executeQuery("SELECT COUNT(*) FROM feedback_items WHERE status='待处理'"); rs.next(); int pending = rs.getInt(1);
            rs = s.executeQuery("SELECT COUNT(*) FROM feedback_items WHERE status='处理中'"); rs.next(); int processing = rs.getInt(1);
            rs = s.executeQuery("SELECT COUNT(*) FROM feedback_items WHERE status='已完成'"); rs.next(); int done = rs.getInt(1);
            rs = s.executeQuery("SELECT COUNT(*) FROM feedback_items WHERE type='民情反馈'"); rs.next(); int feedback = rs.getInt(1);
            rs = s.executeQuery("SELECT COUNT(*) FROM feedback_items WHERE type='政务公开'"); rs.next(); int govt = rs.getInt(1);
            String json = "{\"total\":"+total+",\"pending\":"+pending+",\"processing\":"+processing+",\"done\":"+done+",\"types\":{\"feedback\":"+feedback+",\"public\":"+govt+"}}";
            writeJson(ex,200,json); return;
        } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); }
    }

    private static void handleWarningById(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String path = ex.getRequestURI().getPath(); // /api/warnings/events/{id}
        String idStr = path.replaceFirst(".*/api/warnings/events/","" );
        int id = -1;
        try { id = Integer.parseInt(idStr); } catch(Exception e){ writeText(ex,400,"invalid id"); return; }

        String method = ex.getRequestMethod();
        if ("PUT".equals(method)){
            String body = readBody(ex);
            String title = extractJsonField(body,"title");
            String msg = extractJsonField(body,"msg");
            String severity = extractJsonField(body,"severity");
            String status = extractJsonField(body,"status");
            String assignee = extractJsonField(body,"assignee");
            String handler = extractJsonField(body,"handler");
            String notify = extractJsonField(body,"notify_status");
            String handledAt = extractJsonField(body,"handled_at");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("UPDATE warnings SET title=COALESCE(?,title), msg=COALESCE(?,msg), severity=COALESCE(?,severity), status=COALESCE(?,status), assignee=COALESCE(?,assignee), handler=COALESCE(?,handler), notify_status=COALESCE(?,notify_status), handled_at=COALESCE(?,handled_at) WHERE id=?")){
                ps.setString(1, title);
                ps.setString(2, msg);
                ps.setString(3, severity);
                ps.setString(4, status);
                ps.setString(5, assignee);
                ps.setString(6, handler);
                ps.setString(7, notify);
                ps.setString(8, handledAt);
                ps.setInt(9, id);
                int rows = ps.executeUpdate();
                if (rows==0){ writeText(ex,404,"not found"); return; }
                writeJson(ex,200,"{\"ok\":true}");
                return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("DELETE".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM warnings WHERE id=?")){
                ps.setInt(1,id);
                int rows = ps.executeUpdate();
                if (rows==0){ writeText(ex,404,"not found"); return; }
                writeText(ex,204,"");
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); }
            return;
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleLogin(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!"POST".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        String body = readBody(ex);
        String username = extractJsonString(body,"username");
        String password = extractJsonString(body,"password");
        String captchaToken = extractJsonString(body,"captchaToken");
        String captchaCode = extractJsonString(body,"captchaCode");
        if (username==null || username.isEmpty()) { writeJson(ex,400,"{\"error\":\"username required\"}"); return; }
        if (password==null || password.isEmpty()) { writeJson(ex,400,"{\"error\":\"password required\"}"); return; }
        if (!verifyCaptcha(captchaToken, captchaCode)) { writeJson(ex,401,"{\"error\":\"captcha invalid\"}"); return; }
        try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("SELECT id,name,role,username,password FROM users WHERE username=? LIMIT 1")){
            ps.setString(1, username);
            java.sql.ResultSet rs = ps.executeQuery();
            int id=-1; String name=null; String role=null; String storedPass=null;
            if (rs.next()){
                id = rs.getInt("id");
                name = rs.getString("name");
                role = rs.getString("role");
                storedPass = rs.getString("password");
            }
            if (id==-1){ writeJson(ex,401,"{\"error\":\"invalid credentials\"}"); return; }
            if (!verifyPassword(password, storedPass)) { writeJson(ex,401,"{\"error\":\"invalid credentials\"}"); return; }
            if (storedPass == null || !storedPass.startsWith("$argon2")) {
                String newHash = hashPassword(password);
                if (newHash != null){
                    try (java.sql.PreparedStatement ups = c.prepareStatement("UPDATE users SET password=? WHERE id=?")){
                        ups.setString(1, newHash);
                        ups.setInt(2, id);
                        ups.executeUpdate();
                    }
                }
            }
            writeJson(ex,200,"{\"token\":\"mock-token-123\",\"user\":{\"id\":"+id+",\"name\":\""+escape(name==null?"user":name)+"\",\"role\":\""+escape(role==null?"":role)+"\",\"username\":\""+escape(username)+"\"}}"); return;
        } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
    }

    private static void handlePasswordChange(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!"POST".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        String body = readBody(ex);
        String username = extractJsonString(body,"username");
        String oldPassword = extractJsonString(body,"oldPassword");
        String newPassword = extractJsonString(body,"newPassword");
        if (username==null || username.isEmpty()) { writeJson(ex,400,"{\"error\":\"username required\"}"); return; }
        if (oldPassword==null || oldPassword.isEmpty()) { writeJson(ex,400,"{\"error\":\"oldPassword required\"}"); return; }
        if (newPassword==null || newPassword.isEmpty()) { writeJson(ex,400,"{\"error\":\"newPassword required\"}"); return; }
        try (java.sql.Connection c = openConnection()){
            String storedPass = null;
            try (java.sql.PreparedStatement ps = c.prepareStatement("SELECT password FROM users WHERE username=? LIMIT 1")){
                ps.setString(1, username);
                java.sql.ResultSet rs = ps.executeQuery();
                if (!rs.next()){ writeJson(ex,401,"{\"error\":\"invalid credentials\"}"); return; }
                storedPass = rs.getString("password");
            }
            if (!verifyPassword(oldPassword, storedPass)) { writeJson(ex,401,"{\"error\":\"invalid credentials\"}"); return; }
            String newHash = hashPassword(newPassword);
            try (java.sql.PreparedStatement ps = c.prepareStatement("UPDATE users SET password=? WHERE username=?")){
                ps.setString(1, newHash);
                ps.setString(2, username);
                ps.executeUpdate();
                writeJson(ex,200,"{\"ok\":true}"); return;
            }
        } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
    }

    private static void handleCaptcha(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        String code = randomDigits(4);
        String token = randomToken(24);
        long expiresAt = System.currentTimeMillis() + CAPTCHA_EXPIRES_SECONDS * 1000L;
        CAPTCHA_STORE.put(token, new CaptchaEntry(code, expiresAt));
        String image = createCaptchaImageBase64(code);
        writeJson(ex,200,"{\"token\":\""+escape(token)+"\",\"image\":\""+image+"\",\"expiresIn\":"+CAPTCHA_EXPIRES_SECONDS+"}");
    }

    private static String loadApiKey(String name) throws Exception {
        try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("SELECT key_value FROM api_keys WHERE name=? LIMIT 1")){
            ps.setString(1, name);
            java.sql.ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("key_value");
        }
        return null;
    }

    private static void handleAiAsk(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!"POST".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        String body = readBody(ex);
        String question = extractJsonField(body, "question");
        if (question==null || question.isEmpty()) { writeJson(ex,400,"{\"error\":\"question required\"}"); return; }
        try {
            String apiKey = loadApiKey("deepseek");
            if (apiKey==null || apiKey.isEmpty()) { writeJson(ex,500,"{\"error\":\"api key missing\"}"); return; }

            String payload = "{\"model\":\"deepseek-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"" + escape(question) + "\"}]}";
            java.net.URL url = new java.net.URL("https://api.deepseek.com/v1/chat/completions");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            byte[] out = payload.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) { os.write(out); }
            InputStream in = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
            java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int r;
            while ((r = in.read(buf)) != -1) { bout.write(buf, 0, r); }
            String resp = new String(bout.toByteArray(), StandardCharsets.UTF_8);

            // naive parse: extract "content":"..." from first choice
            String answer = "";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\\"content\\\"\\s*:\\s*\\\"(.*?)\\\"", java.util.regex.Pattern.DOTALL).matcher(resp);
            if (m.find()) answer = m.group(1).replace("\\n", "\n");
            if (answer.isEmpty()) answer = "（未获取到答案）";

            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO ai_records (type,question,answer,created_at) VALUES (?,?,?,?)")){
                ps.setString(1, "chat");
                ps.setString(2, question);
                ps.setString(3, answer);
                ps.setString(4, java.time.Instant.now().toString());
                ps.executeUpdate();
            }
            writeJson(ex,200,"{\"question\":\""+escape(question)+"\",\"answer\":\""+escape(answer)+"\"}");
        } catch(Exception e){
            writeText(ex,500,"ai error: "+e.getMessage());
        }
    }

    private static void handleAiSummarize(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!"POST".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        String body = readBody(ex);
        String content = extractJsonField(body, "content");
        if (content==null || content.isEmpty()) { writeJson(ex,400,"{\"error\":\"content required\"}"); return; }
        try {
            String apiKey = loadApiKey("deepseek");
            if (apiKey==null || apiKey.isEmpty()) { writeJson(ex,500,"{\"error\":\"api key missing\"}"); return; }
            String prompt = "请对以下内容生成简洁摘要：" + content;
            String payload = "{\"model\":\"deepseek-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"" + escape(prompt) + "\"}]}";
            java.net.URL url = new java.net.URL("https://api.deepseek.com/v1/chat/completions");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            byte[] out = payload.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) { os.write(out); }
            InputStream in = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
            java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int r;
            while ((r = in.read(buf)) != -1) { bout.write(buf, 0, r); }
            String resp = new String(bout.toByteArray(), StandardCharsets.UTF_8);
            String summary = "";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\\"content\\\"\\s*:\\s*\\\"(.*?)\\\"", java.util.regex.Pattern.DOTALL).matcher(resp);
            if (m.find()) summary = m.group(1).replace("\\n", "\n");
            if (summary.isEmpty()) summary = "（未获取到摘要）";
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO ai_records (type,question,answer,created_at) VALUES (?,?,?,?)")){
                ps.setString(1, "summary");
                ps.setString(2, "摘要");
                ps.setString(3, summary);
                ps.setString(4, java.time.Instant.now().toString());
                ps.executeUpdate();
            }
            writeJson(ex,200,"{\"summary\":\""+escape(summary)+"\"}");
        } catch(Exception e){
            writeText(ex,500,"ai error: "+e.getMessage());
        }
    }

    private static void handleIndustryMetrics(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,name,value_num,unit,updated_at FROM industry_metrics ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"name\":\"").append(escape(rs.getString("name"))).append("\",")
                      .append("\"value\":").append(rs.getInt("value_num")).append(',')
                      .append("\"unit\":\"").append(escape(rs.getString("unit"))).append("\",")
                      .append("\"updated_at\":\"").append(escape(rs.getString("updated_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String name = extractJsonField(body, "name");
            String value = extractJsonField(body, "value");
            String unit = extractJsonField(body, "unit");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO industry_metrics (name,value_num,unit,updated_at) VALUES (?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, name==null?"指标":name); ps.setInt(2, value==null?0:Integer.parseInt(value)); ps.setString(3, unit==null?"":unit); ps.setString(4, java.time.Instant.now().toString());
                ps.executeUpdate(); java.sql.ResultSet g = ps.getGeneratedKeys(); int id = -1; if (g.next()) id = g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"name\":\""+escape(name==null?"指标":name)+"\",\"value\":"+(value==null?0:Integer.parseInt(value))+",\"unit\":\""+escape(unit==null?"":unit)+"\"}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleIndustryMetricById(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String path = ex.getRequestURI().getPath(); // /api/industry/metrics/{id}
        String idStr = path.replaceFirst(".*/api/industry/metrics/","");
        int id = -1;
        try { id = Integer.parseInt(idStr); } catch(Exception e){ writeText(ex,400,"invalid id"); return; }
        Map<String,Object> found = null;
        try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("SELECT id,name,value_num,unit,updated_at FROM industry_metrics WHERE id=?")){
            ps.setInt(1,id); java.sql.ResultSet rs = ps.executeQuery();
            if (rs.next()){
                found = new HashMap<>();
                found.put("id", rs.getInt("id"));
                found.put("name", rs.getString("name"));
                found.put("value_num", rs.getInt("value_num"));
                found.put("unit", rs.getString("unit"));
                found.put("updated_at", rs.getString("updated_at"));
            }
        } catch(Exception exx){ writeText(ex,500,"db error: "+exx.getMessage()); return; }
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            if (found==null){ writeText(ex,404,"not found"); return; }
            String json = "{\"id\":"+found.get("id")+",\"name\":\""+escape(found.get("name").toString())+"\",\"value\":"+found.get("value_num")+",\"unit\":\""+escape(found.get("unit").toString())+"\",\"updated_at\":\""+escape(found.get("updated_at").toString())+"\"}";
            writeJson(ex,200,json); return;
        }
        if ("PUT".equals(method)){
            if (found==null){ writeText(ex,404,"not found"); return; }
            String body = readBody(ex);
            String name = extractJsonField(body, "name");
            String value = extractJsonField(body, "value");
            String unit = extractJsonField(body, "unit");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("UPDATE industry_metrics SET name=?,value_num=?,unit=?,updated_at=? WHERE id=?")){
                ps.setString(1, name==null?found.get("name").toString():name);
                ps.setInt(2, value==null?((Integer)found.get("value_num")):Integer.parseInt(value));
                ps.setString(3, unit==null?found.get("unit").toString():unit);
                ps.setString(4, java.time.Instant.now().toString());
                ps.setInt(5, id);
                ps.executeUpdate();
                writeJson(ex,200,"{\"ok\":true}"); return;
            } catch(Exception exx){ writeText(ex,500,"db error: "+exx.getMessage()); return; }
        }
        if ("DELETE".equals(method)){
            if (found==null){ writeText(ex,404,"not found"); return; }
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM industry_metrics WHERE id=?")){
                ps.setInt(1,id); ps.executeUpdate(); writeText(ex,204,""); return;
            } catch(Exception exx){ writeText(ex,500,"db error: "+exx.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleMapData(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection()){
                ensureSampleMapData(c);
<<<<<<< HEAD
=======
                try (java.sql.PreparedStatement ps = c.prepareStatement("SELECT id,name,content,map_type,created_at FROM map_data WHERE name=? ORDER BY id DESC LIMIT 1")){
                    ps.setString(1, "主目录地图");
                    java.sql.ResultSet rs = ps.executeQuery();
                    if (rs.next()){
                        String content = rs.getString("content");
                        String type = rs.getString("map_type");
                        if (type == null || type.trim().isEmpty()) type = "svg";
                        String json = "{\"id\":"+rs.getInt("id")+",\"name\":\""+escape(rs.getString("name"))+"\",\"type\":\""+escape(type)+"\",\"content\":\""+escape(content)+"\",\"created_at\":\""+escape(rs.getString("created_at"))+"\"}";
                        writeJson(ex,200,json); return;
                    }
                }
                try (java.sql.PreparedStatement ps = c.prepareStatement("SELECT id,name,content,map_type,created_at FROM map_data WHERE map_type='svg' ORDER BY id DESC LIMIT 1")){
                    java.sql.ResultSet rs = ps.executeQuery();
                    if (rs.next()){
                        String content = rs.getString("content");
                        String type = rs.getString("map_type");
                        if (type == null || type.trim().isEmpty()) type = "svg";
                        String json = "{\"id\":"+rs.getInt("id")+",\"name\":\""+escape(rs.getString("name"))+"\",\"type\":\""+escape(type)+"\",\"content\":\""+escape(content)+"\",\"created_at\":\""+escape(rs.getString("created_at"))+"\"}";
                        writeJson(ex,200,json); return;
                    }
                }
>>>>>>> 3a01a2e11c406534f45cd83922e505d01c297aaa
                try (java.sql.Statement s = c.createStatement()){
                    java.sql.ResultSet rs = s.executeQuery("SELECT id,name,content,map_type,created_at FROM map_data ORDER BY id DESC LIMIT 1");
                    if (rs.next()){
                        String content = rs.getString("content");
                        String type = rs.getString("map_type");
                        if (type == null || type.trim().isEmpty()) type = "svg";
                        String json = "{\"id\":"+rs.getInt("id")+",\"name\":\""+escape(rs.getString("name"))+"\",\"type\":\""+escape(type)+"\",\"content\":\""+escape(content)+"\",\"created_at\":\""+escape(rs.getString("created_at"))+"\"}";
                        writeJson(ex,200,json); return;
                    }
                }
                writeJson(ex,200,"{\"id\":0,\"name\":\"\",\"type\":\"svg\",\"content\":\"\"}");
                return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
<<<<<<< HEAD
            java.util.Map<String, String> payload = parseJsonObjectToStrings(readBody(ex));
            String name = payload.get("name");
            String content = payload.get("content");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO map_data (name,content,created_at) VALUES (?,?,?)")){
=======
            String body = readBody(ex);
            String name = extractJsonString(body, "name");
            String content = extractJsonString(body, "content");
            String type = extractJsonString(body, "type");
            if (type == null || type.trim().isEmpty()) {
                type = "svg";
            }
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO map_data (name,content,map_type,created_at) VALUES (?,?,?,?)")){
>>>>>>> 3a01a2e11c406534f45cd83922e505d01c297aaa
                ps.setString(1, name==null?"地图":name);
                ps.setString(2, content==null?"{}":content);
                ps.setString(3, type);
                ps.setString(4, java.time.Instant.now().toString());
                ps.executeUpdate();
                writeJson(ex,200,"{\"ok\":true}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleResidents(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,name,address,phone,x_num,y_num FROM residents ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"name\":\"").append(escape(rs.getString("name"))).append("\",")
                      .append("\"address\":\"").append(escape(rs.getString("address"))).append("\",")
                      .append("\"phone\":\"").append(escape(rs.getString("phone"))).append("\",")
                      .append("\"x\":").append(rs.getInt("x_num")).append(',')
                      .append("\"y\":").append(rs.getInt("y_num"))
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String name = extractJsonField(body, "name");
            String address = extractJsonField(body, "address");
            String phone = extractJsonField(body, "phone");
            String xStr = extractJsonField(body, "x");
            String yStr = extractJsonField(body, "y");
            int x = 0; int y = 0;
            try { if (xStr != null) x = Integer.parseInt(xStr); } catch(Exception ignored) {}
            try { if (yStr != null) y = Integer.parseInt(yStr); } catch(Exception ignored) {}
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO residents (name,address,phone,x_num,y_num) VALUES (?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, name==null?"村民":name);
                ps.setString(2, address==null?"":address);
                ps.setString(3, phone==null?"":phone);
                ps.setInt(4, x);
                ps.setInt(5, y);
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id = -1; if (g.next()) id = g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"name\":\""+escape(name==null?"村民":name)+"\",\"address\":\""+escape(address==null?"":address)+"\",\"phone\":\""+escape(phone==null?"":phone)+"\",\"x\":"+x+",\"y\":"+y+"}");
                return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleResidentById(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String path = ex.getRequestURI().getPath(); // /api/residents/{id}
        String idStr = path.replaceFirst(".*/api/residents/", "");
        int id = -1;
        try { id = Integer.parseInt(idStr); } catch(Exception e){ writeText(ex,400,"invalid id"); return; }
        String method = ex.getRequestMethod();

        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("SELECT id,name,address,phone,x_num,y_num FROM residents WHERE id=?")){
                ps.setInt(1, id);
                java.sql.ResultSet rs = ps.executeQuery();
                if (!rs.next()) { writeText(ex,404,"not found"); return; }
                String json = "{\"id\":"+rs.getInt("id")+",\"name\":\""+escape(rs.getString("name"))+"\",\"address\":\""+escape(rs.getString("address"))+"\",\"phone\":\""+escape(rs.getString("phone"))+"\",\"x\":"+rs.getInt("x_num")+",\"y\":"+rs.getInt("y_num")+"}";
                writeJson(ex,200,json); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }

        if ("PUT".equals(method)){
            String body = readBody(ex);
            String name = extractJsonField(body, "name");
            String address = extractJsonField(body, "address");
            String phone = extractJsonField(body, "phone");
            String xStr = extractJsonField(body, "x");
            String yStr = extractJsonField(body, "y");
            try (java.sql.Connection c = openConnection()){
                java.sql.PreparedStatement psFind = c.prepareStatement("SELECT id,name,address,phone,x_num,y_num FROM residents WHERE id=?");
                psFind.setInt(1, id);
                java.sql.ResultSet rs = psFind.executeQuery();
                if (!rs.next()) { writeText(ex,404,"not found"); return; }
                int x = rs.getInt("x_num");
                int y = rs.getInt("y_num");
                try { if (xStr != null) x = Integer.parseInt(xStr); } catch(Exception ignored) {}
                try { if (yStr != null) y = Integer.parseInt(yStr); } catch(Exception ignored) {}
                java.sql.PreparedStatement ps = c.prepareStatement("UPDATE residents SET name=?,address=?,phone=?,x_num=?,y_num=? WHERE id=?");
                ps.setString(1, name==null?rs.getString("name"):name);
                ps.setString(2, address==null?rs.getString("address"):address);
                ps.setString(3, phone==null?rs.getString("phone"):phone);
                ps.setInt(4, x);
                ps.setInt(5, y);
                ps.setInt(6, id);
                ps.executeUpdate();
                writeJson(ex,200,"{\"ok\":true}");
                return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }

        if ("DELETE".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM residents WHERE id=?")){
                ps.setInt(1, id);
                int rows = ps.executeUpdate();
                if (rows==0){ writeText(ex,404,"not found"); return; }
                writeText(ex,204,""); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }

        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleAiRecords(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        String type = getQueryParam(ex, "type");
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection()){
                java.sql.PreparedStatement ps;
                if (type!=null && !type.isEmpty()){
                    ps = c.prepareStatement("SELECT id,type,question,answer,created_at FROM ai_records WHERE type=? ORDER BY id DESC");
                    ps.setString(1, type);
                } else {
                    ps = c.prepareStatement("SELECT id,type,question,answer,created_at FROM ai_records ORDER BY id DESC");
                }
                java.sql.ResultSet rs = ps.executeQuery();
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"type\":\"").append(escape(rs.getString("type"))).append("\",")
                      .append("\"question\":\"").append(escape(rs.getString("question"))).append("\",")
                      .append("\"answer\":\"").append(escape(rs.getString("answer"))).append("\",")
                      .append("\"created_at\":\"").append(escape(rs.getString("created_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String question = extractJsonField(body, "question");
            String answer = extractJsonField(body, "answer");
            String t = extractJsonField(body, "type");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO ai_records (type,question,answer,created_at) VALUES (?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, t==null?"chat":t);
                ps.setString(2, question==null?"问题":question);
                ps.setString(3, answer==null?"":answer);
                ps.setString(4, java.time.Instant.now().toString());
                ps.executeUpdate(); java.sql.ResultSet g = ps.getGeneratedKeys(); int id = -1; if (g.next()) id = g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"type\":\""+escape(t==null?"chat":t)+"\",\"question\":\""+escape(question==null?"问题":question)+"\",\"answer\":\""+escape(answer==null?"":answer)+"\"}"); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleAiRecordById(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!"DELETE".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        String path = ex.getRequestURI().getPath(); // /api/ai/records/{id}
        String idStr = path.replaceFirst(".*/api/ai/records/", "");
        int id = -1;
        try { id = Integer.parseInt(idStr); } catch(Exception e){ writeText(ex,400,"invalid id"); return; }
        try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM ai_records WHERE id=?")){
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            if (rows==0){ writeText(ex,404,"not found"); return; }
            writeText(ex,204,"");
        } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); }
    }

    private static void handleOpsAudit(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!"GET".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
            java.sql.ResultSet rs = s.executeQuery("SELECT id,action_desc,actor,status,created_at FROM ops_audit ORDER BY id DESC");
            StringBuilder sb = new StringBuilder(); sb.append('[');
            boolean first=true;
            while(rs.next()){
                if(!first) sb.append(','); first=false;
                sb.append('{')
                  .append("\"id\":").append(rs.getInt("id")).append(',')
                  .append("\"action\":\"").append(escape(rs.getString("action_desc"))).append("\",")
                  .append("\"actor\":\"").append(escape(rs.getString("actor"))).append("\",")
                  .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                  .append("\"created_at\":\"").append(escape(rs.getString("created_at"))).append("\"")
                  .append('}');
            }
            sb.append(']'); writeJson(ex,200,sb.toString()); return;
        } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
    }

    private static void handleOpsAuditReport(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!"GET".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
            java.sql.ResultSet rs = s.executeQuery("SELECT status, COUNT(*) as cnt FROM ops_audit GROUP BY status");
            StringBuilder sb = new StringBuilder(); sb.append('[');
            boolean first=true;
            while(rs.next()){
                if(!first) sb.append(','); first=false;
                sb.append('{')
                  .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                  .append("\"count\":").append(rs.getInt("cnt"))
                  .append('}');
            }
            sb.append(']'); writeJson(ex,200,sb.toString()); return;
        } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
    }

    private static void handleOpsMonitor(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,metric_name,metric_value,status,created_at FROM ops_monitor ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"metric\":\"").append(escape(rs.getString("metric_name"))).append("\",")
                      .append("\"value\":\"").append(escape(rs.getString("metric_value"))).append("\",")
                      .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                      .append("\"created_at\":\"").append(escape(rs.getString("created_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String metric = extractJsonField(body, "metric");
            String value = extractJsonField(body, "value");
            String status = extractJsonField(body, "status");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO ops_monitor (metric_name,metric_value,status,created_at) VALUES (?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, metric==null?"指标":metric);
                ps.setString(2, value==null?"":value);
                ps.setString(3, status==null?"正常":status);
                ps.setString(4, java.time.Instant.now().toString());
                ps.executeUpdate(); java.sql.ResultSet g = ps.getGeneratedKeys(); int id = -1; if (g.next()) id = g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"metric\":\""+escape(metric==null?"指标":metric)+"\",\"value\":\""+escape(value==null?"":value)+"\",\"status\":\""+escape(status==null?"正常":status)+"\"}");
                return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleOpsHealth(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,service_name,status,detail,checked_at FROM ops_health ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"service\":\"").append(escape(rs.getString("service_name"))).append("\",")
                      .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                      .append("\"detail\":\"").append(escape(rs.getString("detail"))).append("\",")
                      .append("\"checked_at\":\"").append(escape(rs.getString("checked_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String service = extractJsonField(body, "service");
            String status = extractJsonField(body, "status");
            String detail = extractJsonField(body, "detail");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO ops_health (service_name,status,detail,checked_at) VALUES (?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, service==null?"服务":service);
                ps.setString(2, status==null?"正常":status);
                ps.setString(3, detail==null?"":detail);
                ps.setString(4, java.time.Instant.now().toString());
                ps.executeUpdate(); java.sql.ResultSet g = ps.getGeneratedKeys(); int id = -1; if (g.next()) id = g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"service\":\""+escape(service==null?"服务":service)+"\",\"status\":\""+escape(status==null?"正常":status)+"\",\"detail\":\""+escape(detail==null?"":detail)+"\"}");
                return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleOpsLogs(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,level,source,message,created_at FROM ops_logs ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"level\":\"").append(escape(rs.getString("level"))).append("\",")
                      .append("\"source\":\"").append(escape(rs.getString("source"))).append("\",")
                      .append("\"message\":\"").append(escape(rs.getString("message"))).append("\",")
                      .append("\"created_at\":\"").append(escape(rs.getString("created_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String level = extractJsonField(body, "level");
            String source = extractJsonField(body, "source");
            String message = extractJsonField(body, "message");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO ops_logs (level,source,message,created_at) VALUES (?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, level==null?"INFO":level);
                ps.setString(2, source==null?"system":source);
                ps.setString(3, message==null?"":message);
                ps.setString(4, java.time.Instant.now().toString());
                ps.executeUpdate(); java.sql.ResultSet g = ps.getGeneratedKeys(); int id = -1; if (g.next()) id = g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"level\":\""+escape(level==null?"INFO":level)+"\",\"source\":\""+escape(source==null?"system":source)+"\",\"message\":\""+escape(message==null?"":message)+"\"}");
                return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleOpsLogsReport(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!"GET".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
            java.sql.ResultSet rs = s.executeQuery("SELECT level, COUNT(*) as cnt FROM ops_logs GROUP BY level");
            StringBuilder sb = new StringBuilder(); sb.append('[');
            boolean first=true;
            while(rs.next()){
                if(!first) sb.append(','); first=false;
                sb.append('{')
                  .append("\"level\":\"").append(escape(rs.getString("level"))).append("\",")
                  .append("\"count\":").append(rs.getInt("cnt"))
                  .append('}');
            }
            sb.append(']'); writeJson(ex,200,sb.toString()); return;
        } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
    }

    private static void handleOpsBackups(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,target,backup_type,status,operator,started_at,finished_at FROM ops_backups ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"target\":\"").append(escape(rs.getString("target"))).append("\",")
                      .append("\"type\":\"").append(escape(rs.getString("backup_type"))).append("\",")
                      .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                      .append("\"operator\":\"").append(escape(rs.getString("operator"))).append("\",")
                      .append("\"started_at\":\"").append(escape(rs.getString("started_at"))).append("\",")
                      .append("\"finished_at\":\"").append(escape(rs.getString("finished_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String target = extractJsonField(body, "target");
            String type = extractJsonField(body, "type");
            String status = extractJsonField(body, "status");
            String operator = extractJsonField(body, "operator");
            String started = java.time.Instant.now().toString();
            String finished = extractJsonField(body, "finished_at");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO ops_backups (target,backup_type,status,operator,started_at,finished_at) VALUES (?,?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, target==null?"village_db":target);
                ps.setString(2, type==null?"全量":type);
                ps.setString(3, status==null?"进行中":status);
                ps.setString(4, operator==null?"admin":operator);
                ps.setString(5, started);
                ps.setString(6, finished);
                ps.executeUpdate(); java.sql.ResultSet g = ps.getGeneratedKeys(); int id = -1; if (g.next()) id = g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"target\":\""+escape(target==null?"village_db":target)+"\",\"type\":\""+escape(type==null?"全量":type)+"\",\"status\":\""+escape(status==null?"进行中":status)+"\",\"operator\":\""+escape(operator==null?"admin":operator)+"\"}");
                return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleOpsRestores(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,backup_id,status,operator,started_at,finished_at FROM ops_restores ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"backup_id\":").append(rs.getInt("backup_id")).append(',')
                      .append("\"status\":\"").append(escape(rs.getString("status"))).append("\",")
                      .append("\"operator\":\"").append(escape(rs.getString("operator"))).append("\",")
                      .append("\"started_at\":\"").append(escape(rs.getString("started_at"))).append("\",")
                      .append("\"finished_at\":\"").append(escape(rs.getString("finished_at"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String backupIdStr = extractJsonField(body, "backup_id");
            String status = extractJsonField(body, "status");
            String operator = extractJsonField(body, "operator");
            int backupId = 0;
            try { if (backupIdStr != null) backupId = Integer.parseInt(backupIdStr); } catch(Exception ignored) {}
            String started = java.time.Instant.now().toString();
            String finished = extractJsonField(body, "finished_at");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO ops_restores (backup_id,status,operator,started_at,finished_at) VALUES (?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setInt(1, backupId);
                ps.setString(2, status==null?"进行中":status);
                ps.setString(3, operator==null?"admin":operator);
                ps.setString(4, started);
                ps.setString(5, finished);
                ps.executeUpdate(); java.sql.ResultSet g = ps.getGeneratedKeys(); int id = -1; if (g.next()) id = g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"backup_id\":"+backupId+",\"status\":\""+escape(status==null?"进行中":status)+"\",\"operator\":\""+escape(operator==null?"admin":operator)+"\"}");
                return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleDbEditorLogin(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!requireLoopback(ex)) return;
        String method = ex.getRequestMethod();
        if (!"POST".equalsIgnoreCase(method)) { writeText(ex,405,"Method Not Allowed"); return; }
        try {
            java.util.Map<String, String> payload = parseJsonObjectToStrings(readBody(ex));
            String password = payload.get("password");
            if (password == null) password = payload.get("rootPassword");
            if (password == null || !password.equals(DB_EDITOR_ROOT_PASSWORD)) {
                writeJson(ex,401,"{\"error\":\"invalid password\"}");
                return;
            }
            String token = java.util.UUID.randomUUID().toString().replace("-", "");
            long exp = System.currentTimeMillis() + DB_EDITOR_TOKEN_TTL_MS;
            dbEditorRootToken = token;
            dbEditorRootTokenExpMs = exp;
            writeJson(ex,200,"{\"token\":\""+escape(token)+"\",\"expires_at\":"+exp+"}");
        } catch (Exception e) {
            writeText(ex,500,"error: "+e.getMessage());
        }
    }

    private static void handleDbEditorTables(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!requireDbEditorAuth(ex)) return;
        String method = ex.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method)) { writeText(ex,405,"Method Not Allowed"); return; }
        try (java.sql.Connection c = openConnection()) {
            java.sql.DatabaseMetaData md = c.getMetaData();
            java.sql.ResultSet rs = md.getTables(c.getCatalog(), null, "%", new String[]{"TABLE"});
            java.util.List<String> tables = new java.util.ArrayList<>();
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name != null && !name.trim().isEmpty()) tables.add(name);
            }
            tables.sort(String.CASE_INSENSITIVE_ORDER);
            StringBuilder sb = new StringBuilder();
            sb.append("{\"db\":\"").append(escape(DB_NAME)).append("\",\"tables\":[");
            for (int i = 0; i < tables.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(escape(tables.get(i))).append('"');
            }
            sb.append("]}");
            writeJson(ex,200,sb.toString());
        } catch (Exception e) {
            writeText(ex,500,"db error: "+e.getMessage());
        }
    }

    private static void handleDbEditorSchema(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!requireDbEditorAuth(ex)) return;
        String method = ex.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method)) { writeText(ex,405,"Method Not Allowed"); return; }
        String path = ex.getRequestURI().getPath(); // /api/ops/db/schema/{table}
        String table = path.replaceFirst(".*/api/ops/db/schema/", "");
        if (table == null) table = "";
        if (table.contains("/")) table = table.substring(0, table.indexOf('/'));
        if (!isSafeIdentifier(table)) { writeJson(ex,400,"{\"error\":\"invalid table\"}"); return; }

        try (java.sql.Connection c = openConnection()) {
            java.sql.DatabaseMetaData md = c.getMetaData();
            java.util.List<String> pk = getPrimaryKeys(md, c.getCatalog(), table);

            java.sql.ResultSet cols = md.getColumns(c.getCatalog(), null, table, "%");
            java.util.List<java.util.Map<String,String>> colList = new java.util.ArrayList<>();
            while (cols.next()) {
                java.util.Map<String,String> col = new java.util.HashMap<>();
                col.put("name", cols.getString("COLUMN_NAME"));
                col.put("type", cols.getString("TYPE_NAME"));
                col.put("nullable", String.valueOf(cols.getInt("NULLABLE")));
                col.put("default", cols.getString("COLUMN_DEF"));
                col.put("auto", cols.getString("IS_AUTOINCREMENT"));
                colList.add(col);
            }
            if (colList.isEmpty()) { writeJson(ex,404,"{\"error\":\"table not found\"}"); return; }

            StringBuilder sb = new StringBuilder();
            sb.append("{\"table\":\"").append(escape(table)).append("\",\"primaryKey\":").append(toJsonStringArray(pk)).append(",\"columns\":[");
            boolean first = true;
            for (java.util.Map<String,String> col : colList) {
                if (!first) sb.append(','); first = false;
                sb.append('{')
                  .append("\"name\":\"").append(escape(col.get("name"))).append("\",")
                  .append("\"type\":\"").append(escape(col.get("type"))).append("\",")
                  .append("\"nullable\":").append(col.get("nullable")==null?"1":col.get("nullable")).append(',')
                  .append("\"auto\":\"").append(escape(col.get("auto"))).append("\",");
                if (col.get("default") == null) sb.append("\"default\":null");
                else sb.append("\"default\":\"").append(escape(col.get("default"))).append('"');
                sb.append('}');
            }
            sb.append("]}");
            writeJson(ex,200,sb.toString());
        } catch (Exception e) {
            writeText(ex,500,"db error: "+e.getMessage());
        }
    }

    private static void handleDbEditorRows(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!requireDbEditorAuth(ex)) return;
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath(); // /api/ops/db/rows/{table}[/pk]
        String rest = path.replaceFirst(".*/api/ops/db/rows/", "");
        if (rest == null) rest = "";
        String[] parts = rest.split("/");
        String table = parts.length > 0 ? parts[0] : "";
        String pkValue = parts.length > 1 ? parts[1] : null;
        if (!isSafeIdentifier(table)) { writeJson(ex,400,"{\"error\":\"invalid table\"}"); return; }

        try (java.sql.Connection c = openConnection()) {
            java.sql.DatabaseMetaData md = c.getMetaData();
            java.util.List<String> pkCols = getPrimaryKeys(md, c.getCatalog(), table);
            java.util.List<String> columns = getColumns(md, c.getCatalog(), table);
            if (columns.isEmpty()) { writeJson(ex,404,"{\"error\":\"table not found\"}"); return; }

            if ("GET".equalsIgnoreCase(method) && pkValue == null) {
                int limit = clampInt(getQueryParam(ex, "limit"), 200, 1, 1000);
                int offset = clampInt(getQueryParam(ex, "offset"), 0, 0, 1_000_000);
                String orderCol = (pkCols.size() == 1) ? pkCols.get(0) : columns.get(0);

                long total = 0;
                try (java.sql.Statement s = c.createStatement()) {
                    java.sql.ResultSet rsc = s.executeQuery("SELECT COUNT(*) FROM " + quoteIdent(table));
                    if (rsc.next()) total = rsc.getLong(1);
                }

                String sql = "SELECT * FROM " + quoteIdent(table) + " ORDER BY " + quoteIdent(orderCol) + " DESC LIMIT ? OFFSET ?";
                try (java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, limit);
                    ps.setInt(2, offset);
                    java.sql.ResultSet rs = ps.executeQuery();
                    StringBuilder sb = new StringBuilder();
                    sb.append("{\"table\":\"").append(escape(table)).append("\",\"primaryKey\":").append(toJsonStringArray(pkCols))
                      .append(",\"columns\":").append(toJsonStringArray(columns))
                      .append(",\"limit\":").append(limit).append(",\"offset\":").append(offset).append(",\"total\":").append(total)
                      .append(",\"rows\":[");
                    boolean firstRow = true;
                    while (rs.next()) {
                        if (!firstRow) sb.append(','); firstRow = false;
                        sb.append('{');
                        for (int i = 0; i < columns.size(); i++) {
                            if (i > 0) sb.append(',');
                            String col = columns.get(i);
                            Object v = rs.getObject(col);
                            sb.append('"').append(escape(col)).append("\":");
                            if (v == null) sb.append("null");
                            else sb.append('"').append(escape(String.valueOf(v))).append('"');
                        }
                        sb.append('}');
                    }
                    sb.append("]}");
                    writeJson(ex,200,sb.toString());
                    return;
                }
            }

            if ("GET".equalsIgnoreCase(method) && pkValue != null) {
                if (pkCols.size() != 1) { writeJson(ex,400,"{\"error\":\"composite primary key not supported\"}"); return; }
                String pkCol = pkCols.get(0);
                String sql = "SELECT * FROM " + quoteIdent(table) + " WHERE " + quoteIdent(pkCol) + "=? LIMIT 1";
                try (java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, pkValue);
                    java.sql.ResultSet rs = ps.executeQuery();
                    if (!rs.next()) { writeJson(ex,404,"{\"error\":\"row not found\"}"); return; }
                    StringBuilder sb = new StringBuilder();
                    sb.append("{\"table\":\"").append(escape(table)).append("\",\"row\":{");
                    for (int i = 0; i < columns.size(); i++) {
                        if (i > 0) sb.append(',');
                        String col = columns.get(i);
                        Object v = rs.getObject(col);
                        sb.append('"').append(escape(col)).append("\":");
                        if (v == null) sb.append("null");
                        else sb.append('"').append(escape(String.valueOf(v))).append('"');
                    }
                    sb.append("}}");
                    writeJson(ex,200,sb.toString());
                    return;
                }
            }

            if ("POST".equalsIgnoreCase(method) && pkValue == null) {
                java.util.Map<String, String> row = parseJsonObjectToStrings(readBody(ex));
                if (row.isEmpty()) { writeJson(ex,400,"{\"error\":\"empty body\"}"); return; }
                java.util.Map<String, String> allowed = filterAllowedColumns(row, columns);
                if (allowed.isEmpty()) { writeJson(ex,400,"{\"error\":\"no valid columns\"}"); return; }

                StringBuilder sql = new StringBuilder();
                sql.append("INSERT INTO ").append(quoteIdent(table)).append(" (");
                java.util.List<String> cols = new java.util.ArrayList<>(allowed.keySet());
                cols.sort(String.CASE_INSENSITIVE_ORDER);
                for (int i = 0; i < cols.size(); i++) {
                    if (i > 0) sql.append(',');
                    sql.append(quoteIdent(cols.get(i)));
                }
                sql.append(") VALUES (");
                for (int i = 0; i < cols.size(); i++) {
                    if (i > 0) sql.append(',');
                    sql.append('?');
                }
                sql.append(')');

                try (java.sql.PreparedStatement ps = c.prepareStatement(sql.toString(), java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    for (int i = 0; i < cols.size(); i++) {
                        String v = allowed.get(cols.get(i));
                        ps.setObject(i + 1, v);
                    }
                    ps.executeUpdate();
                    Object newId = null;
                    java.sql.ResultSet g = ps.getGeneratedKeys();
                    if (g != null && g.next()) newId = g.getObject(1);
                    if (newId == null) writeJson(ex,201,"{\"ok\":true}");
                    else writeJson(ex,201,"{\"ok\":true,\"id\":\""+escape(String.valueOf(newId))+"\"}");
                    return;
                }
            }

            if ("PUT".equalsIgnoreCase(method) && pkValue != null) {
                if (pkCols.size() != 1) { writeJson(ex,400,"{\"error\":\"composite primary key not supported\"}"); return; }
                String pkCol = pkCols.get(0);
                java.util.Map<String, String> row = parseJsonObjectToStrings(readBody(ex));
                row.remove(pkCol);
                java.util.Map<String, String> allowed = filterAllowedColumns(row, columns);
                if (allowed.isEmpty()) { writeJson(ex,400,"{\"error\":\"no valid columns\"}"); return; }

                java.util.List<String> cols = new java.util.ArrayList<>(allowed.keySet());
                cols.sort(String.CASE_INSENSITIVE_ORDER);
                StringBuilder sql = new StringBuilder();
                sql.append("UPDATE ").append(quoteIdent(table)).append(" SET ");
                for (int i = 0; i < cols.size(); i++) {
                    if (i > 0) sql.append(',');
                    sql.append(quoteIdent(cols.get(i))).append("=?");
                }
                sql.append(" WHERE ").append(quoteIdent(pkCol)).append("=?");

                try (java.sql.PreparedStatement ps = c.prepareStatement(sql.toString())) {
                    for (int i = 0; i < cols.size(); i++) {
                        String v = allowed.get(cols.get(i));
                        ps.setObject(i + 1, v);
                    }
                    ps.setString(cols.size() + 1, pkValue);
                    int updated = ps.executeUpdate();
                    writeJson(ex,200,"{\"ok\":true,\"updated\":"+updated+"}");
                    return;
                }
            }

            if ("DELETE".equalsIgnoreCase(method) && pkValue != null) {
                if (pkCols.size() != 1) { writeJson(ex,400,"{\"error\":\"composite primary key not supported\"}"); return; }
                String pkCol = pkCols.get(0);
                String sql = "DELETE FROM " + quoteIdent(table) + " WHERE " + quoteIdent(pkCol) + "=?";
                try (java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, pkValue);
                    int deleted = ps.executeUpdate();
                    writeJson(ex,200,"{\"ok\":true,\"deleted\":"+deleted+"}");
                    return;
                }
            }

            writeText(ex,405,"Method Not Allowed");
        } catch (Exception e) {
            writeText(ex,500,"db error: "+e.getMessage());
        }
    }

    private static boolean requireLoopback(HttpExchange ex) throws IOException {
        try {
            java.net.InetSocketAddress remote = ex.getRemoteAddress();
            if (remote == null) { writeJson(ex,403,"{\"error\":\"forbidden\"}"); return false; }
            java.net.InetAddress addr = remote.getAddress();
            if (addr != null && (addr.isLoopbackAddress() || addr.isAnyLocalAddress())) return true;
        } catch (Exception ignored) {}
        writeJson(ex,403,"{\"error\":\"forbidden\"}");
        return false;
    }

    private static boolean requireDbEditorAuth(HttpExchange ex) throws IOException {
        if (!requireLoopback(ex)) return false;
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null) { writeJson(ex,401,"{\"error\":\"missing token\"}"); return false; }
        auth = auth.trim();
        String token = auth.startsWith("Bearer ") ? auth.substring("Bearer ".length()).trim() : auth;
        if (token.isEmpty()) { writeJson(ex,401,"{\"error\":\"missing token\"}"); return false; }
        String expected = dbEditorRootToken;
        long exp = dbEditorRootTokenExpMs;
        if (expected == null || System.currentTimeMillis() > exp || !expected.equals(token)) {
            writeJson(ex,401,"{\"error\":\"invalid token\"}");
            return false;
        }
        return true;
    }

    private static boolean isSafeIdentifier(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_';
            if (!ok) return false;
        }
        return true;
    }

    private static String quoteIdent(String ident) {
        return "`" + ident + "`";
    }

    private static int clampInt(String s, int def, int min, int max) {
        int v = def;
        try { if (s != null) v = Integer.parseInt(s); } catch (Exception ignored) {}
        if (v < min) v = min;
        if (v > max) v = max;
        return v;
    }

    private static java.util.List<String> getPrimaryKeys(java.sql.DatabaseMetaData md, String catalog, String table) throws Exception {
        java.util.List<String> pk = new java.util.ArrayList<>();
        java.sql.ResultSet rs = md.getPrimaryKeys(catalog, null, table);
        java.util.Map<Integer, String> ordered = new java.util.HashMap<>();
        while (rs.next()) {
            String col = rs.getString("COLUMN_NAME");
            int seq = 0;
            try { seq = rs.getInt("KEY_SEQ"); } catch (Exception ignored) {}
            if (col != null) ordered.put(seq, col);
        }
        if (!ordered.isEmpty()) {
            java.util.List<Integer> keys = new java.util.ArrayList<>(ordered.keySet());
            keys.sort(Integer::compareTo);
            for (Integer k : keys) pk.add(ordered.get(k));
        }
        return pk;
    }

    private static java.util.List<String> getColumns(java.sql.DatabaseMetaData md, String catalog, String table) throws Exception {
        java.util.List<String> cols = new java.util.ArrayList<>();
        java.sql.ResultSet rs = md.getColumns(catalog, null, table, "%");
        while (rs.next()) {
            String col = rs.getString("COLUMN_NAME");
            if (col != null) cols.add(col);
        }
        return cols;
    }

    private static String toJsonStringArray(java.util.List<String> items) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escape(items.get(i))).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private static java.util.Map<String, String> parseJsonObjectToStrings(String json) {
        java.util.Map<String, String> out = new java.util.HashMap<>();
        if (json == null) return out;
        int[] idx = new int[]{0};
        skipWs(json, idx);
        if (idx[0] >= json.length() || json.charAt(idx[0]) != '{') return out;
        idx[0]++;
        while (true) {
            skipWs(json, idx);
            if (idx[0] >= json.length()) break;
            if (json.charAt(idx[0]) == '}') { idx[0]++; break; }
            String key = parseJsonString(json, idx);
            if (key == null) break;
            skipWs(json, idx);
            if (idx[0] >= json.length() || json.charAt(idx[0]) != ':') break;
            idx[0]++;
            skipWs(json, idx);
            String value = parseJsonValueAsString(json, idx);
            out.put(key, value);
            skipWs(json, idx);
            if (idx[0] < json.length() && json.charAt(idx[0]) == ',') { idx[0]++; continue; }
            if (idx[0] < json.length() && json.charAt(idx[0]) == '}') { idx[0]++; break; }
        }
        return out;
    }

    private static void skipWs(String s, int[] idx) {
        while (idx[0] < s.length()) {
            char c = s.charAt(idx[0]);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') idx[0]++;
            else break;
        }
    }

    private static String parseJsonString(String s, int[] idx) {
        if (idx[0] >= s.length() || s.charAt(idx[0]) != '"') return null;
        idx[0]++;
        StringBuilder sb = new StringBuilder();
        while (idx[0] < s.length()) {
            char c = s.charAt(idx[0]++);
            if (c == '"') return sb.toString();
            if (c == '\\' && idx[0] < s.length()) {
                char e = s.charAt(idx[0]++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (idx[0] + 4 <= s.length()) {
                            String hex = s.substring(idx[0], idx[0] + 4);
                            try { sb.append((char) Integer.parseInt(hex, 16)); } catch (Exception ignored) {}
                            idx[0] += 4;
                        }
                        break;
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        return null;
    }

    private static String parseJsonValueAsString(String s, int[] idx) {
        if (idx[0] >= s.length()) return null;
        char c = s.charAt(idx[0]);
        if (c == '"') return parseJsonString(s, idx);
        if (c == 'n' && s.startsWith("null", idx[0])) { idx[0] += 4; return null; }
        if (c == 't' && s.startsWith("true", idx[0])) { idx[0] += 4; return "true"; }
        if (c == 'f' && s.startsWith("false", idx[0])) { idx[0] += 5; return "false"; }
        int start = idx[0];
        while (idx[0] < s.length()) {
            char ch = s.charAt(idx[0]);
            if ((ch >= '0' && ch <= '9') || ch == '-' || ch == '.' || ch == 'e' || ch == 'E' || ch == '+') idx[0]++;
            else break;
        }
        if (idx[0] > start) return s.substring(start, idx[0]);
        // unsupported type (object/array) -> return raw token until delimiter
        while (idx[0] < s.length()) {
            char ch = s.charAt(idx[0]);
            if (ch == ',' || ch == '}' || ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') break;
            idx[0]++;
        }
        return s.substring(start, idx[0]);
    }

    private static java.util.Map<String, String> filterAllowedColumns(java.util.Map<String, String> row, java.util.List<String> columns) {
        java.util.Set<String> allowed = new java.util.HashSet<>(columns);
        java.util.Map<String, String> out = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, String> e : row.entrySet()) {
            String k = e.getKey();
            if (k == null) continue;
            if (!isSafeIdentifier(k)) continue;
            if (!allowed.contains(k)) continue;
            out.put(k, e.getValue());
        }
        return out;
    }

    // very small helpers - not robust JSON parsing but enough for mock
    private static String extractJsonField(String json, String key){
        if (json==null) return null;
        String pattern = "\""+key+"\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) return m.group(1);
        // also try number
        pattern = "\""+key+"\"\\s*:\\s*([0-9]+)";
        m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String escape(String s){
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n").replace("\t","\\t");
    }

    private static String hashPassword(String password){
        if (password == null || password.isEmpty()) return null;
        Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
        char[] pwd = password.toCharArray();
        try {
            return argon2.hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KB, ARGON2_PARALLELISM, pwd);
        } finally {
            argon2.wipeArray(pwd);
        }
    }

    private static boolean verifyPassword(String password, String storedHash){
        if (password == null || storedHash == null || storedHash.isEmpty()) return false;
        if (!storedHash.startsWith("$argon2")) {
            return storedHash.equals(password);
        }
        Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
        char[] pwd = password.toCharArray();
        try {
            return argon2.verify(storedHash, pwd);
        } finally {
            argon2.wipeArray(pwd);
        }
    }

    private static String randomDigits(int len){
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<len;i++){
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private static String randomToken(int len){
        final String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<len;i++){
            sb.append(alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static boolean verifyCaptcha(String token, String code){
        if (token == null || code == null) return false;
        CaptchaEntry entry = CAPTCHA_STORE.remove(token);
        if (entry == null) return false;
        if (System.currentTimeMillis() > entry.expiresAt) return false;
        return entry.code.equalsIgnoreCase(code.trim());
    }

    private static String createCaptchaImageBase64(String code){
        int width = 120;
        int height = 40;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(245, 247, 251));
        g.fillRect(0, 0, width, height);
        g.setFont(new Font("Arial", Font.BOLD, 22));
        g.setColor(new Color(37, 99, 235));
        g.drawString(code, 18, 28);
        g.setColor(new Color(148, 163, 184));
        for (int i=0;i<4;i++){
            int x1 = SECURE_RANDOM.nextInt(width);
            int y1 = SECURE_RANDOM.nextInt(height);
            int x2 = SECURE_RANDOM.nextInt(width);
            int y2 = SECURE_RANDOM.nextInt(height);
            g.drawLine(x1, y1, x2, y2);
        }
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()){
            javax.imageio.ImageIO.write(img, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e){
            return "";
        }
    }

    private static String extractJsonString(String json, String key){
        if (json == null || key == null) return null;
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        idx = json.indexOf(':', idx + needle.length());
        if (idx < 0) return null;
        idx++;
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) idx++;
        if (idx >= json.length() || json.charAt(idx) != '"') return null;
        idx++;
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (; idx < json.length(); idx++){
            char ch = json.charAt(idx);
            if (escaped){
                switch (ch){
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (idx + 4 < json.length()){
                            String hex = json.substring(idx + 1, idx + 5);
                            try { sb.append((char) Integer.parseInt(hex, 16)); } catch (Exception ignored) {}
                            idx += 4;
                        }
                        break;
                    default: sb.append(ch); break;
                }
                escaped = false;
                continue;
            }
            if (ch == '\\'){
                escaped = true;
                continue;
            }
            if (ch == '"'){
                return sb.toString();
            }
            sb.append(ch);
        }
        return null;
    }

    private static String getQueryParam(HttpExchange ex, String key){
        String q = ex.getRequestURI().getQuery();
        if (q==null || q.isEmpty()) return null;
        String[] parts = q.split("&");
        for (String p : parts){
            String[] kv = p.split("=",2);
            if (kv.length==2 && key.equals(kv[0])) return kv[1];
        }
        return null;
    }
}
