CREATE TABLE refresh_tokens (
    id UUID default uuidv7() PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash char(64) NOT NULL,
    status varchar(255) NOT NULL,
    issued_at timestamptz default now() NOT NULL,
    expires_at timestamptz default (now() + INTERVAL '7 days') NOT NULL,
    absolute_expires_at timestamptz NOT NULL,
    rotated_at timestamptz default null,
    predecessor_id UUID default null,
    family_id UUID NOT NULL,
    constraint fk_user_id foreign key (user_id) references users(id) on delete cascade,
    constraint unique_token_hash unique (token_hash),
    constraint status_enum check (status in ('ACTIVE', 'REVOKED','ROTATED')),
    constraint unique_predecessor_id unique (predecessor_id),
    constraint self_fk_predecessor_id foreign key (predecessor_id) references refresh_tokens(id) on delete set null
);
create index idx_family_id on refresh_tokens(family_id);
create index idx_user_id on refresh_tokens(user_id);
create index idx_absolute_expires_at on refresh_tokens(absolute_expires_at);
