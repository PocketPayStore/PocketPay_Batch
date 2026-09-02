CREATE TABLE member
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_member_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE vendor
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE
) ENGINE = InnoDB;

CREATE TABLE product
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    vendor_id  BIGINT       NOT NULL,
    name       VARCHAR(200) NOT NULL,
    price      BIGINT       NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_product_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id)
) ENGINE = InnoDB;

CREATE TABLE stock
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id        BIGINT      NOT NULL,
    total_quantity    INT         NOT NULL,
    reserved_quantity INT         NOT NULL DEFAULT 0,
    sold_quantity     INT         NOT NULL DEFAULT 0,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    is_deleted        BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_stock_product_id UNIQUE (product_id),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB;

CREATE TABLE orders
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number    VARCHAR(50)  NOT NULL,
    member_id       BIGINT       NOT NULL,
    total_amount    BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT uk_orders_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT ck_orders_status CHECK (status IN
        ('CREATED', 'STOCK_RESERVED', 'PAYMENT_PENDING', 'PAID', 'FAILED', 'CANCELED', 'PARTIAL_CANCELED', 'EXPIRED'))
) ENGINE = InnoDB;

CREATE INDEX idx_orders_status_id ON orders (status, id);

CREATE TABLE order_item
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    unit_price BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB;

CREATE TABLE payment
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id          BIGINT       NOT NULL,
    payment_method    VARCHAR(20)  NOT NULL,
    pg_provider       VARCHAR(50)  NOT NULL,
    pg_transaction_id VARCHAR(100),
    idempotency_key   VARCHAR(100) NOT NULL,
    amount            BIGINT       NOT NULL,
    used_point_amount BIGINT       NOT NULL DEFAULT 0,
    refundable_amount BIGINT       NOT NULL DEFAULT 0,
    status            VARCHAR(20)  NOT NULL,
    failure_code      VARCHAR(50),
    failure_message   VARCHAR(500),
    approved_at       DATETIME(6),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_payment_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_payment_status CHECK (status IN
        ('READY', 'IN_PROGRESS', 'DONE', 'FAILED', 'CANCELED', 'PARTIAL_CANCELED', 'TIMEOUT_UNKNOWN'))
) ENGINE = InnoDB;

CREATE INDEX idx_payment_order_id ON payment (order_id);

CREATE TABLE payment_status_history
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id      BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    is_deleted      BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_payment_status_history_payment FOREIGN KEY (payment_id) REFERENCES payment (id)
) ENGINE = InnoDB;

CREATE INDEX idx_payment_status_history_payment_id_id
    ON payment_status_history (payment_id, id);

CREATE TABLE payment_alert_log
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type  VARCHAR(50)  NOT NULL,
    severity    VARCHAR(20)  NOT NULL,
    payment_id  BIGINT       NULL,
    order_id    BIGINT       NULL,
    message     VARCHAR(500) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count INT          NOT NULL DEFAULT 0,
    resolved_at DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE
) ENGINE = InnoDB;

CREATE TABLE settlement
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id          BIGINT      NOT NULL,
    vendor_id            BIGINT      NOT NULL,
    amount              BIGINT      NOT NULL,
    pg_fee_amount       BIGINT      NOT NULL,
    platform_fee_amount BIGINT      NOT NULL,
    net_amount          BIGINT      NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    settled_at          DATETIME(6),
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    is_deleted           BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_settlement_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT fk_settlement_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id),
    CONSTRAINT ck_settlement_status CHECK (status IN ('PENDING', 'SETTLED', 'FAILED'))
) ENGINE = InnoDB;

CREATE INDEX idx_settlement_payment_id ON settlement (payment_id);
