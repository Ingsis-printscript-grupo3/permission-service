-- generica: no sabe que es un snippet. resource_type dice de que recurso se habla
create table permission (
    id            uuid primary key,
    resource_type varchar(50)              not null,
    resource_id   varchar(255)             not null,
    -- el sub del usuario en Auth0
    user_id       varchar(255)             not null,
    type          varchar(20)              not null,
    created_at    timestamp with time zone not null,
    -- un usuario tiene un solo permiso por recurso
    constraint uk_permission_resource_user unique (resource_type, resource_id, user_id)
);
