-- Local-only credentials are documented in sample-app/backend/README.md.
-- BCrypt cost is 12. These accounts must not be reused outside local development.
insert into users (username, password_hash, role, enabled, created_at, updated_at)
values
    ('local-admin', '$2a$12$4ZPZKUni5EFbYRE2AifWIOHdPxJO7BWYzlAymjSiB/iJQHNE.ye/m', 'ADMIN', true, current_timestamp, current_timestamp),
    ('local-user', '$2a$12$fVj3OE8Wn7N66avMooSSLur0poZjp58RTnAS3nQLIDBxGcv.9L6Ya', 'USER', true, current_timestamp, current_timestamp);

insert into projects (owner_id, name, description, created_at, updated_at)
select id, 'Mandala sample project', 'Seed data for API and runtime-trace collection', current_timestamp, current_timestamp
from users
where username = 'local-admin';

insert into tasks (project_id, title, description, status, assignee_id, due_date, created_at, updated_at)
select p.id, 'Inspect generated Mandala', 'Exercise task detail and state changes', 'IN_PROGRESS', u.id,
       current_date + 7, current_timestamp, current_timestamp
from projects p
join users u on u.username = 'local-admin'
where p.name = 'Mandala sample project';
