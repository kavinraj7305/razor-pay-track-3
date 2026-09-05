CREATE TABLE desk_user (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(50) NOT NULL UNIQUE,
    email           VARCHAR(120) NOT NULL UNIQUE,
    display_name    VARCHAR(120) NOT NULL,
    password_hash   VARCHAR(200) NOT NULL,
    role            VARCHAR(20) NOT NULL,
    session_token   VARCHAR(80),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_desk_user_role
        CHECK (role IN ('ADMIN', 'APPROVER', 'OPERATOR'))
);

CREATE INDEX idx_desk_user_role ON desk_user(role);
CREATE INDEX idx_desk_user_token ON desk_user(session_token);
