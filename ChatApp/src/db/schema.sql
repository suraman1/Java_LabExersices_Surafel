CREATE DATABASE  chat_app;
USE chat_app;

CREATE TABLE users (
    user_id  INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password   VARCHAR(64)  NOT NULL,
    created_at DATETIME DEFAULT NOW()
);
CREATE TABLE direct_chats (
    chat_id INT AUTO_INCREMENT PRIMARY KEY,
    user_a  INT NOT NULL,
    user_b  INT NOT NULL,
    created_at DATETIME DEFAULT NOW(),
    UNIQUE KEY uniq_pair (user_a, user_b),
    FOREIGN KEY (user_a) REFERENCES users(user_id),
    FOREIGN KEY (user_b) REFERENCES users(user_id)
);

CREATE TABLE groups_ (
    group_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    owner_id INT NOT NULL,
    created_at DATETIME DEFAULT NOW(),
    FOREIGN KEY (owner_id) REFERENCES users(user_id)
);

CREATE TABLE group_members (
    group_id INT NOT NULL,
    user_id  INT NOT NULL,
    PRIMARY KEY (group_id, user_id),
    FOREIGN KEY (group_id) REFERENCES groups_(group_id),
    FOREIGN KEY (user_id)  REFERENCES users(user_id)
);

CREATE TABLE messages (
    msg_id INT AUTO_INCREMENT PRIMARY KEY,
    scope ENUM('direct','group') NOT NULL,
    scope_id   INT NOT NULL,
    sender_id  INT NOT NULL,
    body   TEXT NOT NULL,
    msg_type ENUM('text','file') DEFAULT 'text',
    created_at DATETIME DEFAULT NOW(),
    FOREIGN KEY (sender_id) REFERENCES users(user_id)
);
