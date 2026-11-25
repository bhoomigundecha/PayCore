-- Transactional outbox + merchant webhooks.
--
-- A payment's status change and its event are written in the SAME database transaction, so an event
-- exists if and only if the change committed. A relay then publishes unpublished events to Kafka, and a
-- consumer turns each event into one delivery per webhook endpoint of the merchant.

create table outbox_events (
    id                   uuid         primary key,
    aggregate_id         uuid         not null,          -- the payment the event is about
    merchant_account_id  uuid         not null,          -- whose webhook endpoints receive it
    endpoint_id          uuid,                           -- set only for "send test event": one endpoint
    event_type           varchar(60)  not null,
    payload              text         not null,          -- the JSON body merchants receive
    created_at           timestamptz  not null,
    published_at         timestamptz                     -- null until the relay has put it on Kafka
);
-- The relay only ever looks at unpublished rows, oldest first. A partial index keeps that cheap
-- however large the table grows.
create index outbox_events_unpublished_idx on outbox_events (created_at) where published_at is null;

create table webhook_endpoints (
    id                 uuid          primary key,
    account_id         uuid          not null references accounts (id),
    url                varchar(500)  not null,
    test_receiver      boolean       not null default false, -- PayCore's built-in receiver
    secret_ciphertext  text          not null,               -- signing secret, AES-GCM encrypted
    created_at         timestamptz   not null
);
create index webhook_endpoints_account_idx on webhook_endpoints (account_id);

create table webhook_deliveries (
    id                uuid          primary key,
    event_id          uuid          not null,
    endpoint_id       uuid          not null references webhook_endpoints (id) on delete cascade,
    event_type        varchar(60)   not null,
    payload           text          not null,
    status            varchar(20)   not null,           -- PENDING, DELIVERED, FAILED
    attempts          int           not null default 0,
    next_attempt_at   timestamptz,
    locked_until      timestamptz,                      -- a worker is sending it right now
    last_status_code  int,
    last_error        varchar(500),
    created_at        timestamptz   not null,
    delivered_at      timestamptz,
    -- Kafka delivers at least once. If the consumer sees the same event twice, the second insert
    -- is a no-op: one delivery per (event, endpoint), ever.
    unique (event_id, endpoint_id)
);
create index webhook_deliveries_due_idx on webhook_deliveries (next_attempt_at) where status = 'PENDING';
create index webhook_deliveries_endpoint_idx on webhook_deliveries (endpoint_id, created_at desc);
