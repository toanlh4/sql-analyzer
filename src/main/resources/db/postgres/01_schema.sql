create schema if not exists sample;

-- Core lookup / dimension tables

CREATE TABLE if not exists sample.customers (
    customer_id     BIGINT PRIMARY KEY,
    name            TEXT        NOT NULL,
    email           TEXT        UNIQUE,
    tier            TEXT        DEFAULT 'standard',   -- 'standard' | 'premium' | 'vip'
    created_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE if not exists sample.categories (
    category_id     BIGINT PRIMARY KEY,
    parent_id       BIGINT REFERENCES categories(category_id),  -- self-join
    name            TEXT        NOT NULL,
    slug            TEXT        UNIQUE,
    created_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE if not exists sample.products (
    product_id      BIGINT PRIMARY KEY,
    category_id     BIGINT      NOT NULL REFERENCES categories(category_id),
    name            TEXT        NOT NULL,
    price           NUMERIC(18, 2),
    is_active       BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE if not exists sample.warehouses (
    warehouse_id    BIGINT PRIMARY KEY,
    name            TEXT        NOT NULL,
    country_code    CHAR(2),
    created_at      TIMESTAMP   NOT NULL DEFAULT now()
);


-- Junction / bridge tables (many-to-many)

-- Products <-> Warehouses with extra payload (composite PK join)
CREATE TABLE if not exists sample.inventory (
    product_id      BIGINT      NOT NULL REFERENCES products(product_id),
    warehouse_id    BIGINT      NOT NULL REFERENCES warehouses(warehouse_id),
    quantity        INT         NOT NULL DEFAULT 0,
    reorder_level   INT,
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, warehouse_id)             -- composite PK
);


-- Transactional tables

CREATE TABLE if not exists sample.orders (
    order_id        BIGINT PRIMARY KEY,
    customer_id     BIGINT      NOT NULL REFERENCES customers(customer_id),
    status          TEXT        NOT NULL DEFAULT 'pending',
    total_amount    NUMERIC(18, 2),
    ordered_at      TIMESTAMP   NOT NULL DEFAULT now(),
    shipped_at      TIMESTAMP
);

-- Order <-> Product line items (composite PK + 3-way join)
CREATE TABLE if not exists sample.order_items (
    order_id        BIGINT      NOT NULL REFERENCES orders(order_id),
    product_id      BIGINT      NOT NULL REFERENCES products(product_id),
    quantity        INT         NOT NULL,
    unit_price      NUMERIC(18, 2)  NOT NULL,           -- price snapshot at order time
    discount        NUMERIC(5, 2)   DEFAULT 0,
    PRIMARY KEY (order_id, product_id)                  -- composite PK
);

-- Payment per order (1-to-1 join on order_id)
CREATE TABLE if not exists sample.payments (
    payment_id      BIGINT PRIMARY KEY,
    order_id        BIGINT      NOT NULL UNIQUE REFERENCES orders(order_id),
    method          TEXT,                               -- 'card' | 'bank_transfer' | 'wallet'
    amount          NUMERIC(18, 2),
    paid_at         TIMESTAMP
);


-- Self-referential + multi-column join examples

-- Employees with manager relationship (self-join on manager_id -> employee_id)
CREATE TABLE if not exists sample.employees (
    employee_id     BIGINT PRIMARY KEY,
    manager_id      BIGINT REFERENCES employees(employee_id),  -- self-join
    name            TEXT        NOT NULL,
    department      TEXT,
    hired_at        DATE
);

-- Warehouse transfers - composite FK join (two warehouse roles in one row)
CREATE TABLE if not exists sample.stock_transfers (
    transfer_id     BIGINT PRIMARY KEY,
    product_id      BIGINT      NOT NULL REFERENCES products(product_id),
    from_warehouse  BIGINT      NOT NULL REFERENCES warehouses(warehouse_id),
    to_warehouse    BIGINT      NOT NULL REFERENCES warehouses(warehouse_id),
    quantity        INT         NOT NULL,
    transferred_at  TIMESTAMP   NOT NULL DEFAULT now()
);

-- Reviews - join on two parent tables (customer + product)
CREATE TABLE if not exists sample.reviews (
    review_id       BIGINT PRIMARY KEY,
    customer_id     BIGINT      NOT NULL REFERENCES customers(customer_id),
    product_id      BIGINT      NOT NULL REFERENCES products(product_id),
    rating          SMALLINT    CHECK (rating BETWEEN 1 AND 5),
    body            TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    UNIQUE (customer_id, product_id)                   -- one review per customer per product
);
