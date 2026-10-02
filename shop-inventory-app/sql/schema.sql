-- Run this in the Supabase SQL Editor (Project -> SQL Editor -> New query).
-- Recreates the full schema from scratch, including Lab 2 changes:
--   - orders no longer stores a single product_id/quantity; line items moved
--     to order_items (multi-item orders).
--   - orders.status now also allows CANCELLED.
--   - new order_items table (one row per line item per order).
--   - new notifications table (Notification module's event log).
-- Safe to re-run: drops everything first.

drop table if exists notifications;
drop table if exists order_items;
drop table if exists orders;
drop table if exists inventory;

create table inventory (
    product_id text primary key,
    name        text not null,
    stock       integer not null check (stock >= 0)
);

create table orders (
    order_id    bigint generated always as identity primary key,
    status      text not null check (status in ('CONFIRMED', 'REJECTED', 'CANCELLED')),
    reason      text,
    created_at  timestamptz not null default now()
);

create table order_items (
    order_item_id bigint generated always as identity primary key,
    order_id      bigint not null references orders(order_id),
    product_id    text not null,
    quantity      integer not null check (quantity > 0)
);

create table notifications (
    notification_id bigint generated always as identity primary key,
    type             text not null check (type in ('ORDER_CONFIRMED', 'ORDER_REJECTED', 'LOW_STOCK')),
    message          text not null,
    created_at       timestamptz not null default now()
);

-- Seed data required by the assignment spec (unchanged from Lab 1).
insert into inventory (product_id, name, stock) values
    ('P100', 'Wireless Mouse',      25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub',           0);
