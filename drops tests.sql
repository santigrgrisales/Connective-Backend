drop table users_roles;
drop table users;
drop table roles;

DROP TABLE flyway_schema_history;

SELECT installed_rank, version, description, type, success, installed_on, installed_by
FROM flyway_schema_history
ORDER BY installed_rank;
