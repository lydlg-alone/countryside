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

public class Application {
    // persistence config (environment or defaults)
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", System.getenv().getOrDefault("MYSQL_HOST", "mysql"));
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
        server.createContext("/api/warnings/events", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleWarnings(exchange); });
        server.createContext("/api/warnings/events/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleWarningById(exchange); });
        server.createContext("/api/warnings/rules", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleWarningRules(exchange); });
        server.createContext("/api/auth/login", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleLogin(exchange); });
        server.createContext("/api/auth/password", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handlePasswordChange(exchange); });
        server.createContext("/api/industry/metrics", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleIndustryMetrics(exchange); });
        server.createContext("/api/industry/metrics/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleIndustryMetricById(exchange); });
        server.createContext("/api/ai/records", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleAiRecords(exchange); });
        server.createContext("/api/ai/records/", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleAiRecordById(exchange); });
        server.createContext("/api/ai/ask", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleAiAsk(exchange); });
        server.createContext("/api/ai/summarize", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleAiSummarize(exchange); });
        server.createContext("/api/ops/audit", exchange -> { if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())){ addCorsHeaders(exchange); exchange.sendResponseHeaders(204, -1); return; } handleOpsAudit(exchange); });

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
        String t = "CREATE TABLE IF NOT EXISTS transactions (id INT AUTO_INCREMENT PRIMARY KEY, description VARCHAR(255), amount INT, time VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String w = "CREATE TABLE IF NOT EXISTS warnings (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255), msg TEXT, severity VARCHAR(50), status VARCHAR(50), triggered_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String im = "CREATE TABLE IF NOT EXISTS industry_metrics (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), value_num INT, unit VARCHAR(32), updated_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String ai = "CREATE TABLE IF NOT EXISTS ai_records (id INT AUTO_INCREMENT PRIMARY KEY, type VARCHAR(32), question TEXT, answer TEXT, created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String oa = "CREATE TABLE IF NOT EXISTS ops_audit (id INT AUTO_INCREMENT PRIMARY KEY, action_desc VARCHAR(255), actor VARCHAR(64), status VARCHAR(32), created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        String ak = "CREATE TABLE IF NOT EXISTS api_keys (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64), key_value TEXT, created_at VARCHAR(64)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (java.sql.Connection c = dataSource.getConnection(); java.sql.Statement s = c.createStatement()){
            s.execute(u); s.execute(t); s.execute(w); s.execute(im); s.execute(ai); s.execute(oa); s.execute(ak);
        }
    }

    private static void ensureColumns() throws Exception {
        try (java.sql.Connection c = dataSource.getConnection(); java.sql.Statement s = c.createStatement()){
            try { s.execute("ALTER TABLE users ADD COLUMN username VARCHAR(100)"); } catch (Exception ignored) {}
            try { s.execute("ALTER TABLE users ADD COLUMN password VARCHAR(100)"); } catch (Exception ignored) {}
            try { s.execute("ALTER TABLE ai_records ADD COLUMN type VARCHAR(32)"); } catch (Exception ignored) {}
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
            rs = s.executeQuery("SELECT COUNT(*) FROM ai_records"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO ai_records (question,answer,created_at) VALUES ('今日天气适合播种吗？','建议关注气象预警后开展播种。','"+java.time.Instant.now().toString()+"')");
            }
            rs = s.executeQuery("SELECT COUNT(*) FROM ops_audit"); rs.next(); if (rs.getInt(1)==0){
                s.execute("INSERT INTO ops_audit (action_desc,actor,status,created_at) VALUES ('登录系统','admin','成功','"+java.time.Instant.now().toString()+"'),('修改预警规则','admin','成功','"+java.time.Instant.now().toString()+"')");
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
                java.sql.ResultSet rs = s.executeQuery("SELECT id,description,amount,time FROM transactions ORDER BY id DESC");
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first=true;
                while(rs.next()){
                    if(!first) sb.append(','); first=false;
                    sb.append('{')
                      .append("\"id\":").append(rs.getInt("id")).append(',')
                      .append("\"description\":\"").append(escape(rs.getString("description"))).append("\",")
                      .append("\"amount\":").append(rs.getInt("amount")).append(',')
                      .append("\"time\":\"").append(escape(rs.getString("time"))).append("\"")
                      .append('}');
                }
                sb.append(']'); writeJson(ex,200,sb.toString()); return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }

        if ("POST".equals(method)){
            String body = readBody(ex);
            String description = extractJsonField(body, "description");
            String amount = extractJsonField(body, "amount");
            int amountValue = 0;
            try { amountValue = amount==null?0:Integer.parseInt(amount); } catch(Exception ignored) { amountValue = 0; }
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO transactions (description,amount,time) VALUES (?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, description==null?"交易":description);
                ps.setInt(2, amountValue);
                ps.setString(3, java.time.Instant.now().toString());
                ps.executeUpdate();
                java.sql.ResultSet g = ps.getGeneratedKeys(); int id = -1; if (g.next()) id = g.getInt(1);
                writeJson(ex,201,"{\"id\":"+id+",\"description\":\""+escape(description==null?"交易":description)+"\",\"amount\":"+amountValue+"}");
                return;
            } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); return; }
        }

        writeText(ex,405,"Method Not Allowed");
    }

    private static void handleWarnings(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("GET".equals(method)){
            try (java.sql.Connection c = openConnection(); java.sql.Statement s = c.createStatement()){
                java.sql.ResultSet rs = s.executeQuery("SELECT id,title,msg,severity,status,triggered_at FROM warnings ORDER BY id DESC");
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
            try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO warnings (title,msg,severity,status,triggered_at) VALUES (?,?,?,?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)){
                ps.setString(1, title==null?"未命名":title); ps.setString(2, msg==null?"":msg); ps.setString(3, "中"); ps.setString(4, "未处理"); ps.setString(5, java.time.Instant.now().toString()); ps.executeUpdate();
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

    private static void handleWarningById(HttpExchange ex) throws IOException {
        ensureTablesSafe();
        if (!"DELETE".equals(ex.getRequestMethod())) { writeText(ex,405,"Method Not Allowed"); return; }
        String path = ex.getRequestURI().getPath(); // /api/warnings/events/{id}
        String idStr = path.replaceFirst(".*/api/warnings/events/","");
        int id = -1;
        try { id = Integer.parseInt(idStr); } catch(Exception e){ writeText(ex,400,"invalid id"); return; }
        try (java.sql.Connection c = openConnection(); java.sql.PreparedStatement ps = c.prepareStatement("DELETE FROM warnings WHERE id=?")){
            ps.setInt(1,id);
            int rows = ps.executeUpdate();
            if (rows==0){ writeText(ex,404,"not found"); return; }
            writeText(ex,204,"");
        } catch(Exception e){ writeText(ex,500,"db error: "+e.getMessage()); }
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
