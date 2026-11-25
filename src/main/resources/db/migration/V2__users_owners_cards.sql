-- Users own accounts; customers are issued virtual cards.

create table users (
    id             uuid         primary key,
    email          varchar(320) not null unique,  -- stored lower-case
    name           varchar(100) not null,
    password_hash  varchar(100),                  -- BCrypt; null for guest users
    guest          boolean      not null,
    created_at     timestamptz  not null
);

alter table accounts add column owner_user_id uuid references users (id);
create index accounts_owner_idx on accounts (owner_user_id);

-- Card vault. The full card number is only ever stored encrypted (AES-GCM); lookups use a keyed
-- fingerprint (HMAC-SHA256). The CVC is never stored: it is derived from the number when needed.
create table cards (
    id               uuid         primary key,
    account_id       uuid         not null references accounts (id),
    user_id          uuid         not null references users (id),
    brand            varchar(20)  not null,
    last4            varchar(4)   not null,
    exp_month        integer      not null check (exp_month between 1 and 12),
    exp_year         integer      not null,
    pan_encrypted    varchar(200) not null,
    pan_fingerprint  varchar(64)  not null unique,
    status           varchar(20)  not null check (status in ('ACTIVE', 'FROZEN')),
    created_at       timestamptz  not null
);
create index cards_user_idx on cards (user_id);

create index payments_customer_idx on payments (customer_account_id, created_at desc);
create index payments_merchant_idx on payments (merchant_account_id, created_at desc);

-- Idempotency keys are stored scoped to the user ("<user id>:<key>"), so allow for the prefix.
alter table payments alter column idempotency_key type varchar(300);
alter table top_ups alter column idempotency_key type varchar(300);
