-- Data-quality quarantine table. Every violation observed while an API request runs in
-- validation mode (?validate=true or loanservice.validation.mode=on) is written here.
CREATE TABLE IF NOT EXISTS DQ_INVALID_INPUT (
    ID              BIGINT AUTO_INCREMENT PRIMARY KEY,
    RECORDED_AT     TIMESTAMP NOT NULL,
    ENDPOINT        VARCHAR(100) NOT NULL,   -- e.g. GET /api/loans/{id}
    INPUT_NAME      VARCHAR(50),             -- request input that led here (id, loanId, ...)
    INPUT_VALUE     VARCHAR(200),
    ENTITY_TYPE     VARCHAR(30) NOT NULL,    -- CDW table or API_INPUT
    RECORD_KEY      VARCHAR(50),
    FIELD           VARCHAR(50) NOT NULL,
    RAW_VALUE       VARCHAR(500),
    RULE_ID         VARCHAR(40) NOT NULL,
    SEVERITY        VARCHAR(5) NOT NULL,     -- ERROR / WARN
    MESSAGE         VARCHAR(500) NOT NULL
);

CREATE INDEX IF NOT EXISTS IDX_DQ_INVALID_INPUT_RULE ON DQ_INVALID_INPUT(RULE_ID);
CREATE INDEX IF NOT EXISTS IDX_DQ_INVALID_INPUT_RECORD ON DQ_INVALID_INPUT(ENTITY_TYPE, RECORD_KEY);
