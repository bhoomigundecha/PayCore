-- Payment links: a merchant asks for an amount; anyone pays it by card on the hosted checkout page.

create table checkout_sessions (
    id                   uuid         primary key,
    merchant_account_id  uuid         not null references accounts (id),
    created_by_user_id   uuid         not null references users (id),
    amount               bigint       not null check (amount > 0),
    currency             varchar(3)   not null,
    description          varchar(200) not null,
    created_at           timestamptz  not null,
    expires_at           timestamptz  not null
);
create index checkout_sessions_merchant_idx on checkout_sessions (merchant_account_id, created_at desc);

alter table payments add column checkout_session_id uuid references checkout_sessions (id);

-- At most one payment per checkout that is not FAILED. Two cards paying the same link at the same
-- moment cannot both succeed: the database rejects the second insert. A declined attempt (FAILED)
-- doesn't count, so the customer can try again with another card.
create unique index payments_one_live_payment_per_checkout
    on payments (checkout_session_id)
    where checkout_session_id is not null and status <> 'FAILED';
