(() => {
  const root = document.documentElement;
  const storage = {
    get(key, fallback) {
      try { return localStorage.getItem(key) || fallback; } catch { return fallback; }
    },
    set(key, value) {
      try { localStorage.setItem(key, value); } catch { /* Storage may be disabled. */ }
    }
  };

  const themeSelect = document.querySelector('[data-theme-select]');
  const storedTheme = storage.get('mandala.theme', 'system');
  const theme = ['system', 'light', 'dark'].includes(storedTheme) ? storedTheme : 'system';
  root.dataset.theme = theme;
  if (themeSelect) themeSelect.value = theme;
  themeSelect?.addEventListener('change', () => {
    if (!['system', 'light', 'dark'].includes(themeSelect.value)) return;
    root.dataset.theme = themeSelect.value;
    storage.set('mandala.theme', themeSelect.value);
  });

  const languageSelect = document.querySelector('[data-language]');
  const storedLanguage = storage.get('mandala.language', root.dataset.locale || 'ja');
  const preferredLanguage = ['ja', 'en'].includes(storedLanguage) ? storedLanguage : (root.dataset.locale || 'ja');
  const preferredOption = languageSelect?.querySelector(`option[data-locale="${preferredLanguage}"]`);
  if (preferredOption && preferredLanguage !== root.dataset.locale) {
    window.location.replace(preferredOption.value);
    return;
  }
  languageSelect?.addEventListener('change', () => {
    const selected = languageSelect.selectedOptions[0];
    if (!selected?.value || !['ja', 'en'].includes(selected.dataset.locale)) return;
    storage.set('mandala.language', selected.dataset.locale);
    window.location.assign(selected.value);
  });

  const nav = document.querySelector('[data-nav]');
  document.querySelector('[data-menu]')?.addEventListener('click', () => nav?.classList.toggle('open'));

  const toc = document.querySelector('[data-toc]');
  document.querySelectorAll('main h2,main h3').forEach((heading) => {
    const link = document.createElement('a');
    link.href = `#${heading.id}`;
    link.textContent = heading.textContent;
    link.className = heading.tagName === 'H3' ? 'depth-3' : '';
    toc?.append(link);
  });

  const dialog = document.querySelector('[data-search]');
  const input = document.querySelector('#search');
  const results = document.querySelector('[data-results]');
  let index = [];
  let searchIndexUrl;
  const suggestedUrls = [
    'overview.html',
    'installation.html',
    'concepts.html',
    'refresh.html',
    'generated-docs.html',
    'security.html'
  ];

  function matchingEntries(query) {
    const terms = query.toLowerCase().trim().split(/\s+/).filter(Boolean);
    if (!terms.length) {
      const suggested = suggestedUrls.map((url) => index.find((entry) => entry.url === url)).filter(Boolean);
      return [...suggested, ...index.filter((entry) => !suggested.includes(entry))].slice(0, 6);
    }
    return index
      .map((entry, position) => {
        const title = entry.title.toLowerCase();
        const description = entry.description.toLowerCase();
        const text = entry.text.toLowerCase();
        if (!terms.every((term) => `${title} ${description} ${text}`.includes(term))) return undefined;
        const score = terms.reduce((total, term) => (
          total + (title.startsWith(term) ? 0 : title.includes(term) ? 1 : description.includes(term) ? 2 : 3)
        ), 0);
        return { entry, position, score };
      })
      .filter(Boolean)
      .sort((a, b) => a.score - b.score || a.position - b.position)
      .slice(0, 20)
      .map(({ entry }) => entry);
  }

  function renderResults(query) {
    const normalizedQuery = query.trim();
    const entries = matchingEntries(normalizedQuery);
    const label = normalizedQuery ? results.dataset.resultsLabel : results.dataset.suggestionsLabel;
    const links = entries.map((entry) => {
      const href = new URL(entry.url, searchIndexUrl).href;
      return `<a href="${escapeHtml(href)}"><strong>${escapeHtml(entry.title)}</strong><small>${escapeHtml(entry.description)}</small></a>`;
    }).join('');
    results.innerHTML = `<div class="search-results-label">${escapeHtml(label)}</div>${
      links || `<p class="search-empty">${escapeHtml(results.dataset.emptyLabel)}</p>`
    }`;
  }

  document.querySelector('[data-search-open]')?.addEventListener('click', async () => {
    if (!index.length) {
      searchIndexUrl = new URL(root.dataset.searchIndex, window.location.href);
      index = await fetch(searchIndexUrl).then((response) => response.json());
    }
    renderResults(input.value);
    dialog.showModal();
    input.focus();
  });
  input?.addEventListener('input', () => {
    renderResults(input.value);
  });

  function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, (character) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[character]);
  }
})();
