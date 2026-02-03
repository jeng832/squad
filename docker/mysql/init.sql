-- Squad 데이터베이스 초기화 스크립트
-- 이 스크립트는 MySQL 컨테이너 최초 실행 시 자동으로 실행됩니다.

-- 데이터베이스가 없으면 생성 (docker-compose에서 이미 생성하지만 명시적으로)
CREATE DATABASE IF NOT EXISTS squad
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- squad 사용자에게 squad 데이터베이스 전체 권한 부여
GRANT ALL PRIVILEGES ON squad.* TO 'squad'@'%';
FLUSH PRIVILEGES;

-- 테스트용 데이터베이스 (선택적)
CREATE DATABASE IF NOT EXISTS squad_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON squad_test.* TO 'squad'@'%';
FLUSH PRIVILEGES;
