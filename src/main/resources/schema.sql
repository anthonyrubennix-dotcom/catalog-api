CREATE TABLE IF NOT EXISTS api_client (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  name         VARCHAR(100) NOT NULL UNIQUE,
  token_sha256 CHAR(64)     NOT NULL UNIQUE,
  created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked_at   TIMESTAMP    NULL
);

CREATE TABLE IF NOT EXISTS book (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  isbn       VARCHAR(13)  NOT NULL UNIQUE,
  title      VARCHAR(200) NOT NULL,
  author     VARCHAR(120) NOT NULL,
  year       SMALLINT     NOT NULL,
  created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_book_author (author),
  INDEX idx_book_year (year)
);
