select id, project_id, title, description, status, assignee_id, due_date, created_at, updated_at
from tasks
where project_id = /* projectId */0
order by created_at desc, id desc
