-- Reset admin password to 123456 (local demo)
CREATE DATABASE IF NOT EXISTS village_db DEFAULT CHARSET utf8mb4;
USE village_db;

CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255),
  role VARCHAR(100),
  username VARCHAR(100),
  password VARCHAR(100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

UPDATE users SET password='123456' WHERE username='admin';

INSERT INTO users (name,role,username,password)
SELECT '文磊','管理员','admin','123456'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='admin');

