-- Postgres holds what each account/payment IS; TigerBeetle holds where the money IS.
-- accounts.id and top_ups.id are the same ids used in TigerBeetle.

create table accounts (
    id          uuid         primary key,
    type        varchar(32)  not null check (type in ('CUSTOMER_WALLET', 'MERCHANT')),
    name        varchar(200) not null,
    currency    varchar(3)   not null,
    created_at  timestamptz  not null
);

create table top_ups (
    id               uuid         primary key,
    idempotency_key  varchar(255) not null unique,
    account_id       uuid         not null references accounts (id),
    amount           bigint       not null check (amount > 0),
    currency         varchar(3)   not null,
    status           varchar(32)  not null check (status in ('PENDING', 'COMPLETED')),
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null
);

create table payments (
    id                   uuid         primary key,
    idempotency_key      varchar(255) not null unique,
    customer_account_id  uuid         not null references accounts (id),
    merchant_account_id  uuid         not null references accounts (id),
    amount               bigint       not null check (amount > 0),
    fee                  bigint       not null check (fee >= 0 and fee < amount),
    currency             varchar(3)   not null,
    auto_capture         boolean      not null,
    status               varchar(32)  not null,
    failure_reason       varchar(100),
    version              bigint       not null,
    created_at           timestamptz  not null,
    updated_at           timestamptz  not null,
    check (customer_account_id <> merchant_account_id)
);

-- The recovery job scans for payments stuck in an in-flight state.
create index payments_status_updated_at_idx on payments (status, updated_at);
create index top_ups_status_updated_at_idx on top_ups (status, updated_at);
