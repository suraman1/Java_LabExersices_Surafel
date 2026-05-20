CREATE DATABASE chat_app;
USE chat_app;

CREATE TABLE messages (
        id INT AUTO_INCREMENT PRIMARY KEY,
        group_name VARCHAR(50) NOT NULL,
        username VARCHAR(50) NOT NULL,
        content TEXT NOT NULL,
        type VARCHAR(10) DEFAULT 'TEXT',
        file_name VARCHAR(255),
        file_data LONGTEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 );
