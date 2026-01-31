package org.village.system;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Application {
    // persistence config (environment or defaults)
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", System.getenv().getOrDefault("MYSQL_HOST", "127.0.0.1"));
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "3306");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "village_db");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "village");
    private static final String DB_PASS = System.getenv().getOrDefault("DB_PASS", "villagepass");

    private static javax.sql.DataSource dataSource = null;
    private static volatile boolean tablesEnsured = false;

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
        String md = "CREATE TABLE IF NOT EXISTS map_data (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), content LONGTEXT, created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
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

    private static String loadGeoJsonFromFile(){
        String[] candidates = new String[]{
                "雨湖区.geojson",
                Paths.get(System.getProperty("user.dir"), "雨湖区.geojson").toString(),
                Paths.get(System.getProperty("user.dir"), "..", "雨湖区.geojson").toString(),
                Paths.get(System.getProperty("user.dir"), "..", "..", "雨湖区.geojson").toString()
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
            String sampleGeo = loadGeoJsonFromFile();
            if (sampleGeo == null || sampleGeo.trim().isEmpty()) return;
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
                    return;
                }
            }
            try (java.sql.PreparedStatement ins = c.prepareStatement("INSERT INTO map_data (name,content,created_at) VALUES (?,?,?)")){
                ins.setString(1, "雨湖区示例地图");
                ins.setString(2, sampleGeo);
                ins.setString(3, java.time.Instant.now().toString());
                ins.executeUpdate();
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
            String sampleGeo = loadGeoJsonFromFile();
            if (mapCount == 0){
                String geo = sampleGeo != null ? sampleGeo : "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"name\":\"村域\"},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1000,0],[1000,600],[0,600],[0,0]]]}}]}";
                try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO map_data (name,content,created_at) VALUES (?,?,?)")){
                    ps.setString(1, sampleGeo != null ? "雨湖区示例地图" : "默认村落地图");
                    ps.setString(2, geo);
                    ps.setString(3, java.time.Instant.now().toString());
                    ps.executeUpdate();
                }
            } else if (sampleGeo != null) {
                rs = s.executeQuery("SELECT COUNT(*) FROM map_data WHERE name='雨湖区示例地图'"); rs.next();
                if (rs.getInt(1) == 0){
                    try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO map_data (name,content,created_at) VALUES (?,?,?)")){
                        ps.setString(1, "雨湖区示例地图");
                        ps.setString(2, sampleGeo);
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
            String name = extractJsonField(body, "name");
            String role = extractJsonField(body, "role");
            String username = extractJsonField(body, "username");
            String password = extractJsonField(body, "password");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO users (name,role,username,password) VALUES (?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, name==null?"用户":name);
                ps.setString(2, role==null?"普通用户":role);
                ps.setString(3, username);
                ps.setString(4, password);
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
            ps.setInt(1,id); java.sql.ResultSet rs = ps.executeQuery(); if (rs.next()){ found = new HashMap<>(); found.put("id", rs.getInt("id")); found.put("name", rs.getString("name")); found.put("role", rs.getString("role")); }
        } catch(Exception exx){ writeText(ex,500,"db error: "+exx.getMessage()); return; }
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            if (found==null) { writeText(ex,404,"not found"); return; }
            String json = "{\"id\":"+found.get("id")+",\"name\":\""+escape(found.get("name").toString())+"\",\"role\":\""+escape(found.get("role").toString())+"\"}";
            writeJson(ex,200,json); return;
        }
        if ("PUT".equals(method)){
            if (found==null) { writeText(ex,404,"not found"); return; }
            String body = readBody(ex);
            String name = extractJsonField(body, "name");
            String role = extractJsonField(body, "role");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("UPDATE users SET name=?,role=? WHERE id=?")){
                ps.setString(1, name==null?found.get("name").toString():name);
                ps.setString(2, role==null?found.get("role").toString():role);
                ps.setInt(3, id);
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
        String username = extractJsonField(body,"username");
        String password = extractJsonField(body,"password");
        if (username==null || username.isEmpty()) { writeJson(ex,400,"{\"error\":\"username required\"}"); return; }
        if (password==null || password.isEmpty()) { writeJson(ex,400,"{\"error\":\"password required\"}"); return; }
        try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("SELECT id,name,role FROM users WHERE username=? AND password=? LIMIT 1")){
            ps.setString(1, username);
            ps.setString(2, password);
            java.sql.ResultSet rs = ps.executeQuery();
            int id=-1; String name=null; String role=null;
            if (rs.next()){ id = rs.getInt("id"); name = rs.getString("name"); role = rs.getString("role"); }
            if (id==-1){ writeJson(ex,401,"{\"error\":\"invalid credentials\"}"); return; }
            writeJson(ex,200,"{\"token\":\"mock-token-123\",\"user\":{\"id\":"+id+",\"name\":\""+escape(name==null?"user":name)+"\",\"role\":\""+escape(role==null?"":role)+"\",\"username\":\""+escape(username)+"\"}}"); return;
        } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
    }

    private static void handlePasswordChange(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!"POST".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        String body = readBody(ex);
        String username = extractJsonField(body,"username");
        String oldPassword = extractJsonField(body,"oldPassword");
        String newPassword = extractJsonField(body,"newPassword");
        if (username==null || username.isEmpty()) { writeJson(ex,400,"{\"error\":\"username required\"}"); return; }
        if (oldPassword==null || oldPassword.isEmpty()) { writeJson(ex,400,"{\"error\":\"oldPassword required\"}"); return; }
        if (newPassword==null || newPassword.isEmpty()) { writeJson(ex,400,"{\"error\":\"newPassword required\"}"); return; }
        try (java.sql.Connection c = openConnection()){
            try (java.sql.PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE username=? AND password=? LIMIT 1")){
                ps.setString(1, username);
                ps.setString(2, oldPassword);
                java.sql.ResultSet rs = ps.executeQuery();
                if (!rs.next()){ writeJson(ex,401,"{\"error\":\"invalid credentials\"}"); return; }
            }
            try (java.sql.PreparedStatement ps = c.prepareStatement("UPDATE users SET password=? WHERE username=?")){
                ps.setString(1, newPassword);
                ps.setString(2, username);
                ps.executeUpdate();
                writeJson(ex,200,"{\"ok\":true}"); return;
            }
        } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
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
                try (java.sql.PreparedStatement ps = c.prepareStatement("SELECT id,name,content,created_at FROM map_data WHERE name=? ORDER BY id DESC LIMIT 1")){
                    ps.setString(1, "雨湖区示例地图");
                    java.sql.ResultSet rs = ps.executeQuery();
                    if (rs.next()){
                        String content = rs.getString("content");
                        String json = "{\"id\":"+rs.getInt("id")+",\"name\":\""+escape(rs.getString("name"))+"\",\"content\":\""+escape(content)+"\",\"created_at\":\""+escape(rs.getString("created_at"))+"\"}";
                        writeJson(ex,200,json); return;
                    }
                }
                try (java.sql.Statement s = c.createStatement()){
                    java.sql.ResultSet rs = s.executeQuery("SELECT id,name,content,created_at FROM map_data ORDER BY id DESC LIMIT 1");
                    if (rs.next()){
                        String content = rs.getString("content");
                        String json = "{\"id\":"+rs.getInt("id")+",\"name\":\""+escape(rs.getString("name"))+"\",\"content\":\""+escape(content)+"\",\"created_at\":\""+escape(rs.getString("created_at"))+"\"}";
                        writeJson(ex,200,json); return;
                    }
                }
                writeJson(ex,200,"{\"id\":0,\"name\":\"\",\"content\":\"\"}");
                return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }
        if ("POST".equals(method)){
            String body = readBody(ex);
            String name = extractJsonField(body, "name");
            String content = extractJsonField(body, "content");
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO map_data (name,content,created_at) VALUES (?,?,?)")){
                ps.setString(1, name==null?"地图":name);
                ps.setString(2, content==null?"{}":content);
                ps.setString(3, java.time.Instant.now().toString());
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
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");
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
