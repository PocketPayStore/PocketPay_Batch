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
    expires_at      DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT uk_orders_member_idempotency_key UNIQUE (member_id, idempotency_key),
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT ck_orders_status CHECK (status IN
        ('CREATED', 'STOCK_RESERVED', 'PAYMENT_PENDING', 'PAID', 'FAILED', 'CANCELED', 'PARTIAL_CANCELED', 'EXPIRED'))
) ENGINE = InnoDB;

CREATE INDEX idx_orders_member_id ON orders (member_id);
CREATE INDEX idx_orders_status_updated_at ON orders (status, updated_at);
CREATE INDEX idx_orders_status_expires_at ON orders (status, expires_at);

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
    refundable_point_amount BIGINT NOT NULL DEFAULT 0,
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
CREATE INDEX idx_payment_status_id ON payment (status, id);

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

CREATE TABLE refund
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id           BIGINT       NOT NULL,
    request_amount       BIGINT       NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    pg_cancel_confirmed  BOOLEAN      NOT NULL DEFAULT FALSE,
    idempotency_key      VARCHAR(100) NOT NULL,
    requested_at         DATETIME(6)  NOT NULL,
    processed_at         DATETIME(6),
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_refund_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payment (id)
) ENGINE = InnoDB;

CREATE INDEX idx_refund_payment_id ON refund (payment_id);

CREATE TABLE payment_cancel
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id        BIGINT      NOT NULL,
    refund_id         BIGINT      NULL,
    cancel_amount     BIGINT      NOT NULL,
    reason            VARCHAR(200),
    canceled_at       DATETIME(6) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    is_deleted        BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_payment_cancel_payment FOREIGN KEY (payment_id) REFERENCES payment (id)
) ENGINE = InnoDB;

CREATE INDEX idx_payment_cancel_payment_id ON payment_cancel (payment_id);

CREATE TABLE point_balance
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id  BIGINT      NOT NULL,
    balance    BIGINT      NOT NULL DEFAULT 0,
    reserved_amount BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_point_balance_member_id UNIQUE (member_id),
    CONSTRAINT fk_point_balance_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB;

CREATE TABLE point_reservation
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT      NOT NULL,
    member_id  BIGINT      NOT NULL,
    amount     BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_point_reservation_payment UNIQUE (payment_id),
    CONSTRAINT fk_point_reservation_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT fk_point_reservation_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT ck_point_reservation_status CHECK (status IN ('RESERVED', 'USED', 'RELEASED'))
) ENGINE = InnoDB;

CREATE TABLE point_ledger
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT      NOT NULL,
    order_id      BIGINT,
    type          VARCHAR(20) NOT NULL,
    amount        BIGINT      NOT NULL,
    balance_after BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    is_deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_point_ledger_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_point_ledger_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_point_ledger_type CHECK (type IN ('EARN', 'USE', 'CANCEL_RESTORE', 'EARN_REVERSAL'))
) ENGINE = InnoDB;

CREATE TABLE outbox_event
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSON         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count    INT          NOT NULL DEFAULT 0,
    published_at   DATETIME(6),
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE
) ENGINE = InnoDB;

CREATE INDEX idx_outbox_event_status_id ON outbox_event (status, id);

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

CREATE TABLE point_earn_log
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id   BIGINT      NOT NULL,
    order_id    BIGINT      NOT NULL,
    payment_id  BIGINT      NOT NULL,
    amount      BIGINT      NOT NULL,
    reversed_amount BIGINT  NOT NULL DEFAULT 0,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resolved_at DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    is_deleted  BOOLEAN     NOT NULL DEFAULT FALSE
) ENGINE = InnoDB;

CREATE INDEX idx_point_earn_log_order_id ON point_earn_log (order_id);
CREATE INDEX idx_point_earn_log_payment_id ON point_earn_log (payment_id);
CREATE INDEX idx_point_earn_log_status_id ON point_earn_log (status, id);

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
    CONSTRAINT uk_settlement_payment_id UNIQUE (payment_id),
    CONSTRAINT fk_settlement_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id),
    CONSTRAINT ck_settlement_status CHECK (status IN ('PENDING', 'SETTLED', 'FAILED'))
) ENGINE = InnoDB;

CREATE INDEX idx_settlement_payment_id ON settlement (payment_id);
