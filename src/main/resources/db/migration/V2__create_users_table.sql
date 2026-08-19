create collation case_insensitive(
    provider = icu,
    locale = 'und-u-ks-level2',
    deterministic = false
    );


create table users (
    id    uuid default uuidv7() primary key,
    username  varchar(30) not null,
    email varchar(255) not null collate case_insensitive,
    password_hash varchar(255) not null,
    role varchar(255) not null default 'USER',
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    constraint unique_email unique(email),
    constraint check_role check(role in ('USER', 'ADMIN'))
);