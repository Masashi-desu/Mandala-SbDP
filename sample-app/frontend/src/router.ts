export type Route =
  | { name: 'login' }
  | { name: 'projects' }
  | { name: 'project-new' }
  | { name: 'project-detail'; projectId: number }
  | { name: 'project-edit'; projectId: number }
  | { name: 'task-new'; projectId: number }
  | { name: 'task-detail'; taskId: number }
  | { name: 'task-edit'; taskId: number }
  | { name: 'not-found' };

export const declaredRoutes = [
  '/login', '/projects', '/projects/new', '/projects/:id', '/projects/:id/edit',
  '/projects/:id/tasks/new', '/tasks/:id', '/tasks/:id/edit',
] as const;

export function matchRoute(pathname: string): Route {
  if (pathname === '/' || pathname === '/projects') return { name: 'projects' };
  if (pathname === '/login') return { name: 'login' };
  if (pathname === '/projects/new') return { name: 'project-new' };
  let match = pathname.match(/^\/projects\/(\d+)$/);
  if (match?.[1]) return { name: 'project-detail', projectId: Number(match[1]) };
  match = pathname.match(/^\/projects\/(\d+)\/edit$/);
  if (match?.[1]) return { name: 'project-edit', projectId: Number(match[1]) };
  match = pathname.match(/^\/projects\/(\d+)\/tasks\/new$/);
  if (match?.[1]) return { name: 'task-new', projectId: Number(match[1]) };
  match = pathname.match(/^\/tasks\/(\d+)$/);
  if (match?.[1]) return { name: 'task-detail', taskId: Number(match[1]) };
  match = pathname.match(/^\/tasks\/(\d+)\/edit$/);
  if (match?.[1]) return { name: 'task-edit', taskId: Number(match[1]) };
  return { name: 'not-found' };
}
