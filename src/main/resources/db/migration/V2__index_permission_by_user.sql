-- el listado busca por usuario (todos los recursos de un tipo que tiene alguien) y el unique
-- de V1 empieza por resource_type + resource_id, asi que no sirve para esa consulta
create index ix_permission_user_resource_type on permission (user_id, resource_type);
