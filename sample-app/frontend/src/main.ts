import './styles.css';
import { api, ApiError, type Project, type Task, type TaskStatus, type ValidationProblem } from './api';
import { matchRoute, type Route } from './router';

const appElement = document.querySelector<HTMLDivElement>('#app');
if (!appElement) throw new Error('Missing #app');
const app: HTMLDivElement = appElement;

const escapeHtml = (value: unknown): string => String(value ?? '')
  .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;').replaceAll("'", '&#039;');

function layout(content: string, title = 'Mandala Tasks'): string {
  return `<header class="topbar"><a class="brand" href="/projects" data-link>Mandala Tasks</a>
    <nav aria-label="メイン"><a href="/projects" data-link>プロジェクト</a><button class="link-button" data-action="logout">ログアウト</button></nav></header>
    <main class="container"><div class="eyebrow">SAMPLE APPLICATION</div><h1>${escapeHtml(title)}</h1>${content}</main>`;
}

function setView(html: string, ready = true): void {
  app.innerHTML = html;
  document.body.dataset.docReady = String(ready);
}

function loading(title: string): void {
  setView(layout('<div class="state-card" role="status"><span class="spinner"></span><p>読み込み中です…</p></div>', title), false);
}

function errorView(error: unknown, retry = true): void {
  if (error instanceof ApiError && error.status === 401) {
    navigate('/login'); return;
  }
  if (error instanceof ApiError && error.status === 403) {
    setView(layout('<section class="state-card forbidden"><div class="state-icon">403</div><h2>権限がありません</h2><p>この操作を行う権限がありません。管理者へお問い合わせください。</p><a href="/projects" data-link>一覧へ戻る</a></section>', '権限不足'));
    return;
  }
  if (error instanceof ApiError && error.status === 404) {
    notFound(); return;
  }
  setView(layout(`<section class="state-card error"><div class="state-icon">!</div><h2>APIエラー</h2><p>データを取得できませんでした。</p>${retry ? '<button data-action="retry">再試行</button>' : ''}</section>`, 'エラー'));
}

function notFound(): void {
  setView(layout('<section class="state-card"><div class="state-icon">404</div><h2>ページが見つかりません</h2><p>指定されたページまたはデータは存在しません。</p><a href="/projects" data-link>プロジェクト一覧へ</a></section>', 'Not Found'));
}

function fieldErrors(problem: ValidationProblem | undefined): string {
  if (!problem?.fieldErrors) return '';
  return `<div class="validation-summary" role="alert"><strong>入力内容を確認してください</strong><ul>${Object.entries(problem.fieldErrors).map(([field, message]) => `<li><a href="#${escapeHtml(field)}">${escapeHtml(message)}</a></li>`).join('')}</ul></div>`;
}

function projectForm(project?: Project, problem?: ValidationProblem): string {
  const editing = Boolean(project);
  return layout(`${fieldErrors(problem)}<form class="form-card" data-form="project" data-id="${project?.id ?? ''}" novalidate>
    <label for="name">プロジェクト名 <span aria-hidden="true">*</span></label>
    <input id="name" name="name" required minlength="2" maxlength="100" value="${escapeHtml(project?.name)}" aria-describedby="name-help" />
    <small id="name-help">2〜100文字で入力してください。</small>
    <label for="description">説明</label><textarea id="description" name="description" maxlength="1000" rows="6">${escapeHtml(project?.description)}</textarea>
    <div class="actions"><button type="submit">${editing ? '変更を保存' : 'プロジェクトを作成'}</button><a href="${editing ? `/projects/${project?.id}` : '/projects'}" data-link>キャンセル</a></div>
  </form>`, editing ? 'プロジェクトを編集' : '新しいプロジェクト');
}

function taskForm(projectId: number, task?: Task, problem?: ValidationProblem): string {
  const editing = Boolean(task);
  return layout(`${fieldErrors(problem)}<form class="form-card" data-form="task" data-project-id="${projectId}" data-id="${task?.id ?? ''}" novalidate>
    <label for="title">タスク名 <span aria-hidden="true">*</span></label><input id="title" name="title" required minlength="2" maxlength="120" value="${escapeHtml(task?.title)}" />
    <label for="description">説明</label><textarea id="description" name="description" maxlength="2000" rows="6">${escapeHtml(task?.description)}</textarea>
    <input type="hidden" name="status" value="${task?.status ?? 'TODO'}" /><input type="hidden" name="assigneeId" value="${task?.assigneeId ?? ''}" />
    <div class="actions"><button type="submit">${editing ? '変更を保存' : 'タスクを作成'}</button><a href="${editing ? `/tasks/${task?.id}` : `/projects/${projectId}`}" data-link>キャンセル</a></div>
  </form>`, editing ? 'タスクを編集' : '新しいタスク');
}

async function renderProjects(): Promise<void> {
  loading('プロジェクト');
  try {
    const projects = await api.projects();
    const content = projects.length === 0
      ? '<section class="state-card empty"><div class="state-icon">0</div><h2>プロジェクトはまだありません</h2><p>最初のプロジェクトを作成して作業を始めましょう。</p><a class="button" href="/projects/new" data-link>プロジェクトを作成</a></section>'
      : `<div class="page-actions"><p>${projects.length}件のプロジェクト</p><a class="button" href="/projects/new" data-link>新規作成</a></div><div class="card-grid">${projects.map((p) => `<article class="card"><div class="card-meta">#${p.id}${p.archived ? ' · ARCHIVED' : ''}</div><h2><a href="/projects/${p.id}" data-link>${escapeHtml(p.name)}</a></h2><p>${escapeHtml(p.description)}</p><div class="card-footer">更新 ${formatDate(p.updatedAt)}</div></article>`).join('')}</div>`;
    setView(layout(content, 'プロジェクト'));
  } catch (error) { errorView(error); }
}

async function renderProject(id: number): Promise<void> {
  loading('プロジェクト詳細');
  try {
    const [project, tasks] = await Promise.all([api.project(id), api.tasks(id)]);
    const taskList = tasks.length === 0 ? '<div class="inline-empty">タスクはまだありません。</div>' : `<ul class="task-list">${tasks.map((task) => `<li><span class="status status-${task.status.toLowerCase()}">${task.status.replace('_', ' ')}</span><a href="/tasks/${task.id}" data-link>${escapeHtml(task.title)}</a><span>${formatDate(task.updatedAt)}</span></li>`).join('')}</ul>`;
    setView(layout(`<div class="page-actions"><a href="/projects" data-link>← 一覧</a><div><a class="button secondary" href="/projects/${id}/edit" data-link>編集</a><button class="danger" data-action="delete-project" data-id="${id}">削除</button></div></div>
      <section class="detail-card"><div class="card-meta">PROJECT #${id}</div><p>${escapeHtml(project.description)}</p><dl><dt>オーナー</dt><dd>#${project.ownerId}</dd><dt>作成日時</dt><dd>${formatDate(project.createdAt)}</dd></dl></section>
      <section><div class="section-heading"><h2>タスク</h2><a class="button" href="/projects/${id}/tasks/new" data-link>タスクを追加</a></div>${taskList}</section>`, project.name));
  } catch (error) { errorView(error); }
}

async function renderTask(id: number): Promise<void> {
  loading('タスク詳細');
  try {
    const task = await api.task(id);
    const statuses: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE', 'BLOCKED'];
    setView(layout(`<div class="page-actions"><a href="/projects/${task.projectId}" data-link>← プロジェクト</a><div><a class="button secondary" href="/tasks/${id}/edit" data-link>編集</a><button class="danger" data-action="delete-task" data-id="${id}" data-project-id="${task.projectId}">削除</button></div></div>
      <section class="detail-card"><span class="status status-${task.status.toLowerCase()}">${task.status.replace('_', ' ')}</span><p>${escapeHtml(task.description)}</p><label for="task-status">状態を変更</label><select id="task-status" data-action="task-status" data-id="${id}">${statuses.map((status) => `<option ${status === task.status ? 'selected' : ''}>${status}</option>`).join('')}</select></section>`, task.title));
  } catch (error) { errorView(error); }
}

function renderLogin(problem?: string): void {
  setView(`<main class="login-shell"><section class="login-panel"><div class="eyebrow">MANDALA SAMPLE</div><h1>おかえりなさい</h1><p>プロジェクトとタスクを一つの場所で管理します。</p>${problem ? `<div class="validation-summary" role="alert">${escapeHtml(problem)}</div>` : ''}<form data-form="login"><label for="username">ユーザー名</label><input id="username" name="username" autocomplete="username" value="admin" required /><label for="password">パスワード</label><input id="password" name="password" type="password" autocomplete="current-password" required /><button type="submit">ログイン</button></form><small>ローカル検証用アカウントはREADMEを参照してください。</small></section><aside><div>画面からDBまで、<br />根拠をつないで理解する。</div></aside></main>`);
}

async function render(route: Route): Promise<void> {
  switch (route.name) {
    case 'login': renderLogin(); break;
    case 'projects': await renderProjects(); break;
    case 'project-new': setView(projectForm()); break;
    case 'project-detail': await renderProject(route.projectId); break;
    case 'project-edit': loading('プロジェクトを編集'); try { setView(projectForm(await api.project(route.projectId))); } catch (error) { errorView(error); } break;
    case 'task-new': setView(taskForm(route.projectId)); break;
    case 'task-detail': await renderTask(route.taskId); break;
    case 'task-edit': loading('タスクを編集'); try { const task = await api.task(route.taskId); setView(taskForm(task.projectId, task)); } catch (error) { errorView(error); } break;
    case 'not-found': notFound(); break;
  }
}

function navigate(path: string): void {
  history.pushState({}, '', path);
  void render(matchRoute(location.pathname));
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('ja-JP', { dateStyle: 'medium', timeZone: 'Asia/Tokyo' }).format(new Date(value));
}

document.addEventListener('click', (event) => {
  const target = event.target as HTMLElement;
  const link = target.closest<HTMLAnchorElement>('a[data-link]');
  if (link) { event.preventDefault(); navigate(link.pathname); return; }
  const action = target.closest<HTMLElement>('[data-action]');
  if (!action) return;
  if (action.dataset.action === 'retry') void render(matchRoute(location.pathname));
  if (action.dataset.action === 'logout') void api.logout().finally(() => navigate('/login'));
  if (action.dataset.action === 'delete-project' && action.dataset.id && confirm('プロジェクトを削除しますか？')) void api.deleteProject(Number(action.dataset.id)).then(() => navigate('/projects')).catch(errorView);
  if (action.dataset.action === 'delete-task' && action.dataset.id && action.dataset.projectId && confirm('タスクを削除しますか？')) void api.deleteTask(Number(action.dataset.id)).then(() => navigate(`/projects/${action.dataset.projectId}`)).catch(errorView);
});

document.addEventListener('change', (event) => {
  const select = (event.target as HTMLElement).closest<HTMLSelectElement>('select[data-action="task-status"]');
  if (select?.dataset.id) void api.changeTaskStatus(Number(select.dataset.id), select.value as TaskStatus).then(() => renderTask(Number(select.dataset.id))).catch(errorView);
});

document.addEventListener('submit', (event) => {
  const form = event.target as HTMLFormElement;
  event.preventDefault();
  const data = new FormData(form);
  if (form.dataset.form === 'login') {
    void api.login(String(data.get('username')), String(data.get('password'))).then(() => navigate('/projects')).catch(() => renderLogin('ユーザー名またはパスワードが正しくありません。'));
  }
  if (form.dataset.form === 'project') {
    const input = { name: String(data.get('name')), description: String(data.get('description')) };
    const promise = form.dataset.id ? api.updateProject(Number(form.dataset.id), input) : api.createProject(input);
    void promise.then((project) => navigate(`/projects/${project.id}`)).catch((error) => error instanceof ApiError && error.status === 400 ? setView(projectForm(form.dataset.id ? ({ id: Number(form.dataset.id), ...input } as Project) : undefined, error.body as ValidationProblem)) : errorView(error));
  }
  if (form.dataset.form === 'task') {
    const input = { title: String(data.get('title')), description: String(data.get('description')) };
    const projectId = Number(form.dataset.projectId);
    const updateInput = { ...input, status: String(data.get('status') || 'TODO') as TaskStatus, assigneeId: data.get('assigneeId') ? Number(data.get('assigneeId')) : null };
    const promise = form.dataset.id ? api.updateTask(Number(form.dataset.id), updateInput) : api.createTask(projectId, input);
    void promise.then((task) => navigate(`/tasks/${task.id}`)).catch((error) => error instanceof ApiError && error.status === 400 ? setView(taskForm(projectId, form.dataset.id ? ({ id: Number(form.dataset.id), projectId, ...input } as Task) : undefined, error.body as ValidationProblem)) : errorView(error));
  }
});

window.addEventListener('popstate', () => void render(matchRoute(location.pathname)));
void render(matchRoute(location.pathname));
