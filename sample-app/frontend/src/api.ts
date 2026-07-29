export type Project = {
  id: number;
  name: string;
  description: string;
  ownerId: number;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'BLOCKED';

export type Task = {
  id: number;
  projectId: number;
  title: string;
  description: string;
  status: TaskStatus;
  assigneeId: number | null;
  createdAt: string;
  updatedAt: string;
};

export type Session = { id: number; username: string; displayName: string; role: 'ADMIN' | 'USER' };
export type ValidationProblem = { status: number; message?: string; fieldErrors?: Record<string, string> };

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly body: unknown) {
    super(`API request failed: ${status}`);
  }
}

export function normalizeApiPath(input: string): string {
  const url = new URL(input, window.location.origin);
  return url.pathname
    .replace(/\/projects\/\d+/g, '/projects/{id}')
    .replace(/\/tasks\/\d+/g, '/tasks/{id}');
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method,
    credentials: 'include',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const contentType = response.headers.get('content-type') ?? '';
  const value: unknown = response.status === 204
    ? undefined
    : contentType.includes('json')
      ? await response.json()
      : await response.text();
  if (!response.ok) throw new ApiError(response.status, value);
  return value as T;
}

export const api = {
  login: (username: string, password: string) => request<Session>('POST', '/api/auth/login', { username, password }),
  logout: () => request<void>('POST', '/api/auth/logout'),
  session: () => request<Session>('GET', '/api/auth/me'),
  projects: () => request<Project[]>('GET', '/api/projects'),
  project: (id: number) => request<Project>('GET', `/api/projects/${id}`),
  createProject: (input: Pick<Project, 'name' | 'description'>) => request<Project>('POST', '/api/projects', input),
  updateProject: (id: number, input: Pick<Project, 'name' | 'description'>) => request<Project>('PUT', `/api/projects/${id}`, input),
  deleteProject: (id: number) => request<void>('DELETE', `/api/projects/${id}`),
  tasks: (projectId: number) => request<Task[]>('GET', `/api/projects/${projectId}/tasks`),
  task: (id: number) => request<Task>('GET', `/api/tasks/${id}`),
  createTask: (projectId: number, input: Pick<Task, 'title' | 'description'>) => request<Task>('POST', `/api/projects/${projectId}/tasks`, input),
  updateTask: (id: number, input: Pick<Task, 'title' | 'description' | 'status'> & { assigneeId?: number | null; dueDate?: string | null }) => request<Task>('PUT', `/api/tasks/${id}`, input),
  changeTaskStatus: (id: number, status: TaskStatus) => request<Task>('PATCH', `/api/tasks/${id}/status`, { status }),
  deleteTask: (id: number) => request<void>('DELETE', `/api/tasks/${id}`),
};
