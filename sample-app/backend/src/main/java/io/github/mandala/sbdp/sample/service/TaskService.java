package io.github.mandala.sbdp.sample.service;

import io.github.mandala.sbdp.sample.database.dao.TaskDao;
import io.github.mandala.sbdp.sample.database.entity.TaskEntity;
import io.github.mandala.sbdp.sample.domain.ResourceNotFoundException;
import io.github.mandala.sbdp.sample.domain.TaskStatus;
import io.github.mandala.sbdp.sample.security.AppUserPrincipal;
import io.github.mandala.sbdp.starter.MandalaApplicationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@MandalaApplicationService
public class TaskService {
    private final TaskDao taskDao;
    private final ProjectAccessPolicy accessPolicy;
    private final AuditLogService auditLogService;

    public TaskService(TaskDao taskDao, ProjectAccessPolicy accessPolicy, AuditLogService auditLogService) {
        this.taskDao = taskDao;
        this.accessPolicy = accessPolicy;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<TaskEntity> list(Long projectId, AppUserPrincipal actor) {
        accessPolicy.requireAccessible(projectId, actor);
        return taskDao.selectByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public TaskEntity get(Long id, AppUserPrincipal actor) {
        TaskEntity task = requireTask(id);
        accessPolicy.requireAccessible(task.getProjectId(), actor);
        return task;
    }

    @Transactional
    public TaskEntity create(
            Long projectId,
            String title,
            String description,
            Long assigneeId,
            LocalDate dueDate,
            AppUserPrincipal actor) {
        accessPolicy.requireAccessible(projectId, actor);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        TaskEntity task = new TaskEntity();
        task.setProjectId(projectId);
        task.setTitle(title.trim());
        task.setDescription(normalize(description));
        task.setStatus(TaskStatus.TODO.name());
        task.setAssigneeId(assigneeId);
        task.setDueDate(dueDate);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskDao.insert(task);
        task.setId(taskDao.selectCurrentIdentity());
        auditLogService.record(actor.id(), "CREATE", "task", task.getId(), Map.of("title", task.getTitle()));
        return task;
    }

    @Transactional
    public TaskEntity update(
            Long id,
            String title,
            String description,
            TaskStatus status,
            Long assigneeId,
            LocalDate dueDate,
            AppUserPrincipal actor) {
        TaskEntity task = requireTask(id);
        accessPolicy.requireAccessible(task.getProjectId(), actor);
        task.setTitle(title.trim());
        task.setDescription(normalize(description));
        task.setStatus(status.name());
        task.setAssigneeId(assigneeId);
        task.setDueDate(dueDate);
        task.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        taskDao.update(task);
        auditLogService.record(actor.id(), "UPDATE", "task", id, Map.of("status", status.name()));
        return task;
    }

    @Transactional
    public TaskEntity changeStatus(Long id, TaskStatus status, AppUserPrincipal actor) {
        TaskEntity task = requireTask(id);
        accessPolicy.requireAccessible(task.getProjectId(), actor);
        task.setStatus(status.name());
        task.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        taskDao.update(task);
        auditLogService.record(actor.id(), "STATUS_CHANGE", "task", id, Map.of("status", status.name()));
        return task;
    }

    @Transactional
    public void delete(Long id, AppUserPrincipal actor) {
        TaskEntity task = requireTask(id);
        accessPolicy.requireAccessible(task.getProjectId(), actor);
        taskDao.delete(task);
        auditLogService.record(actor.id(), "DELETE", "task", id, Map.of("title", task.getTitle()));
    }

    private TaskEntity requireTask(Long id) {
        return taskDao.selectById(id).orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
