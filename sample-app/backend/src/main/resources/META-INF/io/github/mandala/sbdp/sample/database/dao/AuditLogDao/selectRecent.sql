select id, user_id, action, entity_type, entity_id, details, created_at
from audit_logs
order by created_at desc, id desc
limit /* limit */100
