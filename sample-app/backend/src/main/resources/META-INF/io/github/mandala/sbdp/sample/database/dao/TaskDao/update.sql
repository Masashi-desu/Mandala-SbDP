update tasks
set title = /* task.title */'Task',
    description = /* task.description */'Description',
    status = /* task.status */'TODO',
    assignee_id = /* task.assigneeId */null,
    due_date = /* task.dueDate */null,
    updated_at = /* task.updatedAt */'2000-01-01 00:00:00'
where id = /* task.id */0
