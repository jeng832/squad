-- Squad Database Initialization Script
-- This script runs when the MySQL container is first created

-- Create database if not exists (already created by MYSQL_DATABASE env)
CREATE DATABASE IF NOT EXISTS squad CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE squad;

-- Grant all privileges to squad user
GRANT ALL PRIVILEGES ON squad.* TO 'squad'@'%';
FLUSH PRIVILEGES;

-- Note: Table schemas will be managed by JPA/Flyway or manual schema.sql
-- This init script only ensures the database and user are properly configured
