import { describe, expect, it } from 'vitest';
import { matchRoute } from './router';

describe('matchRoute', () => {
  it('matches stable project and task routes', () => {
    expect(matchRoute('/projects/42')).toEqual({ name: 'project-detail', projectId: 42 });
    expect(matchRoute('/projects/7/tasks/new')).toEqual({ name: 'task-new', projectId: 7 });
    expect(matchRoute('/tasks/9/edit')).toEqual({ name: 'task-edit', taskId: 9 });
  });

  it('does not treat unknown paths as a valid screen', () => {
    expect(matchRoute('/missing')).toEqual({ name: 'not-found' });
  });
});
