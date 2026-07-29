select id, username, password_hash, role, enabled, created_at, updated_at
from users
where username = /* username */'local-user'
