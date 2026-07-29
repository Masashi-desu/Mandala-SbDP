(() => {
  const root = document.documentElement;
  const storage = {
    get(key, fallback) {
      try { return localStorage.getItem(key) || fallback; } catch { return fallback; }
    },
    set(key, value) {
      try { localStorage.setItem(key, value); } catch { /* Storage can be disabled. */ }
    }
  };
  const english = {
    'controls.language': 'Language',
    'controls.theme': 'Theme',
    'theme.system': 'System',
    'theme.light': 'Light',
    'theme.dark': 'Dark',
    'search.open': 'Open search',
    'search.close': 'Close',
    'search.label': 'Search the Documentation Graph',
    'search.placeholder': 'Endpoint, Table, Stable ID…',
    'home.description': 'Bidirectionally connects screens, execution paths, Java, SQL, and PostgreSQL with supporting Evidence.',
    'home.e2e': 'E2E flows',
    'metrics.e2e': 'E2E flows',
    'metrics.screens': 'Screens',
    'metrics.endpoints': 'Endpoints',
    'metrics.symbols': 'Java symbols',
    'metrics.sql': 'SQL',
    'metrics.tables': 'Tables',
    'metrics.warnings': 'Warnings',
    'metrics.stale': 'Stale',
    'metrics.conflicts': 'Conflicts',
    'diff.majorChanges': 'Major changes since the previous analysis',
    'diff.noChanges': 'There are no semantic changes.',
    'diff.openReport': 'Open the diff report',
    'diff.added': 'Added',
    'diff.removed': 'Removed',
    'diff.modified': 'Modified',
    'diff.newItem': 'New item',
    'diff.deleted': 'Deleted',
    'diff.impact': 'Reverse-index impact',
    'empty.e2e': 'No E2E flows have been discovered.',
    'empty.category': 'There are no Nodes in this category.',
    'empty.attributes': 'There are no structured attributes.',
    'empty.evidence': 'Evidence is not available.',
    'empty.runtime': 'No Runtime Trace was observed for this scenario.',
    'empty.tables': 'There are no related Tables.',
    'empty.erTables': 'There are no Tables available for this ER diagram.',
    'empty.columns': 'There are no Columns.',
    'empty.relatedE2e': 'There are no related E2E flows.',
    'empty.relationships': 'There are no relationships.',
    'empty.screenTransitions': 'No observed screen-to-screen transitions are available.',
    'empty.actionTransitions': 'No action-level state transitions have been observed yet.',
    'empty.report': 'There are no matching items.',
    'collection.summary': 'nodes · reproducible list ordered by Stable ID',
    'node.warnings': 'Warnings',
    'node.conflicts': 'Conflicts requiring review',
    'node.specification': 'Specification',
    'node.forward': 'Follow from this item',
    'node.reverse': 'Items that use this item',
    'flow.runtime': 'Observed execution path',
    'flow.crudEr': 'CRUD and partial ER',
    'table.definition': 'Table definition',
    'table.columns': 'Columns',
    'table.tableComment': 'Table comment',
    'table.schema': 'Schema',
    'table.tableName': 'Table name',
    'table.owner': 'Owner',
    'table.rls': 'Row-level security',
    'table.enabled': 'Enabled',
    'table.disabled': 'Disabled',
    'table.column': 'Column',
    'table.dataType': 'Data type',
    'table.nullable': 'Nullable',
    'table.default': 'Default',
    'table.keysIndexes': 'Keys / indexes',
    'table.comment': 'Comment',
    'table.constraints': 'Constraints',
    'table.indexes': 'Indexes',
    'table.databaseObjects': 'Database objects',
    'table.referencedBy': 'Referenced by',
    'table.triggers': 'Triggers',
    'table.policies': 'Policies',
    'table.functions': 'Functions',
    'table.applicationUsage': 'Application usage',
    'table.relatedSql': 'Related SQL',
    'table.relatedDaos': 'Related DAOs',
    'table.relatedServices': 'Related Application Services',
    'table.relatedE2e': 'Related E2E flows',
    'screenshots.title': 'Screen captures',
    'transitions.nav': 'Screen transitions',
    'transitions.title': 'Observed screen transition diagram',
    'transitions.description': 'An overview that places E2E-observed screens as screenshot-backed Nodes and connects NAVIGATES_TO relationships with lines. Open a Screen for individual transitions and internal states.',
    'transitions.overview': 'Screen connection map',
    'transitions.overviewDescription': 'Select a Screen to inspect its one-to-one transitions, states, actions, conditional outcomes, and related HTTP calls.',
    'transitions.noScreenshot': 'No screenshot observed',
    'transitions.stateCount': '{0} states',
    'transitions.screenDetailTitle': 'Transitions for this Screen',
    'transitions.screenDetailDescription': 'One-to-one screen transitions observed by E2E scenarios where this Screen is the source or destination.',
    'transitions.screenActionTitle': 'States, actions, and conditional outcomes for this Screen',
    'transitions.actionDescription': 'Shows each action’s source state, target state, sequence, role, feature flags, outcome, and related HTTP calls.',
    'transitions.from': 'From',
    'transitions.to': 'To',
    'transitions.relatedHttp': 'Related HTTP',
    'transitions.branchCount': '{0} outcomes',
    'crud.title': 'CRUD matrix',
    'crud.description': 'Navigate bidirectionally from each cell to E2E, Endpoint, Service, DAO, SQL, Table, Column, and Trace pages. Classification uses SQL and observations rather than HTTP Method.',
    'er.title': 'ER diagram',
    'er.search': 'Search Tables',
    'er.diagram': 'Entity relationship diagram',
    'er.columns': 'Columns',
    'er.column': 'Column',
    'er.keys': 'Key',
    'er.dataType': 'Data type',
    'er.noKey': 'No key',
    'er.relationships': 'Relationships',
    'er.notation': 'Notation',
    'er.notationAria': 'ER relationship notation',
    'er.identifying': 'Identifying',
    'er.nonIdentifying': 'Non-identifying',
    'er.idefEndpoints': 'child (default 0..*) / optional parent',
    'er.idefCardinality': 'zero or one / one or more',
    'er.many': 'zero or more',
    'er.optionalOne': 'zero or one',
    'er.exactlyOne': 'exactly one',
    'er.keyColumns': 'Relationship keys',
    'er.noRelationshipColumns': 'No relationship keys',
    'er.openTableColumns': 'Open the Table page for all Columns',
    'report.evidenceDescription': 'Lists the Evidence, source, and Confidence for each item.',
    'report.staleDescription': 'Explanations that require confirmation after their source implementation changed.',
    'report.conflictDescription': 'Source conflicts that require human or Agent review.',
    'report.diffTitle': 'Changes since the previous analysis',
    'report.diffEmpty': 'Semantic diff excludes timestamps and JSON ordering. There are no semantic changes.',
    'report.diffDescription': 'Semantic diff excludes timestamps and JSON ordering. node +{0} / -{1} / ~{2}, edge +{3} / -{4} / ~{5}, impacted candidates {6}.',
    'report.item': 'Item',
    'report.type': 'Type',
    'report.stateEvidence': 'State / Evidence',
    'report.sourceId': 'Source / Stable ID'
  };
  const originalText = new Map();
  const originalAttributes = new Map();

  document.querySelectorAll('[data-i18n],[data-i18n-template]').forEach((element) => {
    originalText.set(element, element.textContent);
  });
  for (const attribute of ['aria-label', 'placeholder']) {
    document.querySelectorAll(`[data-i18n-${attribute}]`).forEach((element) => {
      originalAttributes.set(`${attribute}:${originalAttributes.size}`, { element, attribute, value: element.getAttribute(attribute) || '' });
    });
  }

  function translateTemplate(template, element) {
    const values = (element.dataset.i18nValues || '').split(',');
    return values.reduce((text, value, position) => text.replaceAll(`{${position}}`, value), template);
  }

  function applyLanguage(locale) {
    const language = locale === 'en' ? 'en' : 'ja';
    root.lang = language;
    root.dataset.locale = language;
    document.querySelectorAll('[data-i18n]').forEach((element) => {
      const translated = english[element.dataset.i18n];
      element.textContent = language === 'en' && translated ? translated : originalText.get(element);
    });
    document.querySelectorAll('[data-i18n-template]').forEach((element) => {
      const translated = english[element.dataset.i18nTemplate];
      element.textContent = language === 'en' && translated
        ? translateTemplate(translated, element)
        : originalText.get(element);
    });
    originalAttributes.forEach(({ element, attribute, value }) => {
      const key = element.dataset[`i18n${attribute === 'aria-label' ? 'AriaLabel' : 'Placeholder'}`];
      element.setAttribute(attribute, language === 'en' && english[key] ? english[key] : value);
    });
    const selector = document.querySelector('[data-language]');
    if (selector) selector.value = language;
    storage.set('mandala.language', language);
  }

  const themeSelect = document.querySelector('[data-theme-select]');
  const theme = storage.get('mandala.theme', 'system');
  root.dataset.theme = ['system', 'light', 'dark'].includes(theme) ? theme : 'system';
  if (themeSelect) themeSelect.value = root.dataset.theme;
  themeSelect?.addEventListener('change', () => {
    root.dataset.theme = themeSelect.value;
    storage.set('mandala.theme', themeSelect.value);
  });

  const languageSelect = document.querySelector('[data-language]');
  applyLanguage(storage.get('mandala.language', 'ja'));
  languageSelect?.addEventListener('change', () => applyLanguage(languageSelect.value));

  const open = document.querySelector('[data-search-open]');
  const dialog = document.querySelector('[data-search]');
  const input = document.querySelector('#mandala-search');
  const results = document.querySelector('[data-search-results]');
  let index = [];
  const stylesheet = document.querySelector('link[href$="assets/mandala.css"]');
  const prefix = stylesheet ? stylesheet.getAttribute('href').replace('assets/mandala.css', '') : '';
  open?.addEventListener('click', async () => {
    if (!index.length) index = await fetch(prefix + 'search-index.json').then((response) => response.json());
    dialog.showModal();
    input.focus();
  });
  input?.addEventListener('input', () => {
    const query = input.value.toLowerCase().trim();
    results.innerHTML = query
      ? index.filter((entry) => `${entry.title} ${entry.id} ${entry.type} ${entry.description}`.toLowerCase().includes(query))
        .slice(0, 30)
        .map((entry) => `<a class="search-result" href="${prefix}${escapeHtml(entry.url)}"><strong>${escapeHtml(entry.title)}</strong><small>${escapeHtml(entry.type)} · ${escapeHtml(entry.id)}</small></a>`)
        .join('')
      : '';
  });
  const svgNamespace = 'http://www.w3.org/2000/svg';
  const erDiagrams = Array.from(document.querySelectorAll('[data-er-diagram]'));
  const screenMaps = Array.from(document.querySelectorAll('[data-screen-map]'));
  let erAnimationFrame = 0;
  let screenMapAnimationFrame = 0;
  function svgElement(name, attributes, text) {
    const element = document.createElementNS(svgNamespace, name);
    Object.entries(attributes).forEach(([key, value]) => element.setAttribute(key, String(value)));
    if (text !== undefined) element.textContent = text;
    return element;
  }
  function drawScreenMap(map) {
    const svg = map.querySelector('[data-screen-connectors]');
    if (!svg) return;
    const mapBounds = map.getBoundingClientRect();
    const width = Math.max(1, map.clientWidth);
    const height = Math.max(1, map.clientHeight);
    svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
    svg.querySelectorAll('.screen-map-connector').forEach((connector) => connector.remove());
    const nodes = new Map(Array.from(map.querySelectorAll('[data-screen-node]'))
      .map((node) => [node.dataset.screenNode, node]));
    map.querySelectorAll('[data-screen-edge]').forEach((edge, edgeIndex) => {
      const from = nodes.get(edge.dataset.from);
      const to = nodes.get(edge.dataset.to);
      if (!from || !to || from === to) return;
      const fromBounds = from.getBoundingClientRect();
      const toBounds = to.getBoundingClientRect();
      const fromCenterX = fromBounds.left - mapBounds.left + fromBounds.width / 2;
      const fromCenterY = fromBounds.top - mapBounds.top + fromBounds.height / 2;
      const toCenterX = toBounds.left - mapBounds.left + toBounds.width / 2;
      const toCenterY = toBounds.top - mapBounds.top + toBounds.height / 2;
      const deltaX = toCenterX - fromCenterX;
      const deltaY = toCenterY - fromCenterY;
      let fromX;
      let fromY;
      let toX;
      let toY;
      let path;
      const useMobileGutter = width <= 620 && Math.abs(deltaX) < fromBounds.width / 2;
      if (useMobileGutter) {
        const direction = edgeIndex % 2 === 0 ? -1 : 1;
        fromX = fromCenterX + direction * fromBounds.width / 2;
        fromY = fromCenterY;
        toX = toCenterX + direction * toBounds.width / 2;
        toY = toCenterY;
        const routeX = direction < 0 ? 8 : width - 8;
        path = `M ${fromX.toFixed(1)} ${fromY.toFixed(1)} H ${routeX.toFixed(1)} V ${toY.toFixed(1)} H ${toX.toFixed(1)}`;
      } else if (Math.abs(deltaX) >= Math.abs(deltaY)) {
        const direction = deltaX >= 0 ? 1 : -1;
        fromX = fromCenterX + direction * fromBounds.width / 2;
        fromY = fromCenterY;
        toX = toCenterX - direction * toBounds.width / 2;
        toY = toCenterY;
        const control = Math.max(36, Math.abs(toX - fromX) * .45);
        path = `M ${fromX.toFixed(1)} ${fromY.toFixed(1)} C ${(fromX + direction * control).toFixed(1)} ${fromY.toFixed(1)}, ${(toX - direction * control).toFixed(1)} ${toY.toFixed(1)}, ${toX.toFixed(1)} ${toY.toFixed(1)}`;
      } else {
        const direction = deltaY >= 0 ? 1 : -1;
        fromX = fromCenterX;
        fromY = fromCenterY + direction * fromBounds.height / 2;
        toX = toCenterX;
        toY = toCenterY - direction * toBounds.height / 2;
        const control = Math.max(36, Math.abs(toY - fromY) * .45);
        path = `M ${fromX.toFixed(1)} ${fromY.toFixed(1)} C ${fromX.toFixed(1)} ${(fromY + direction * control).toFixed(1)}, ${toX.toFixed(1)} ${(toY - direction * control).toFixed(1)}, ${toX.toFixed(1)} ${toY.toFixed(1)}`;
      }
      const group = svgElement('g', { class: 'screen-map-connector' });
      group.append(
        svgElement('path', { class: 'screen-map-connector-halo', d: path }),
        svgElement('path', { class: 'screen-map-connector-line', d: path })
      );
      svg.append(group);
    });
  }
  function queueScreenMapDraw() {
    if (screenMapAnimationFrame) cancelAnimationFrame(screenMapAnimationFrame);
    screenMapAnimationFrame = requestAnimationFrame(() => {
      screenMapAnimationFrame = 0;
      screenMaps.forEach(drawScreenMap);
    });
  }
  if (screenMaps.length) {
    queueScreenMapDraw();
    window.addEventListener('resize', queueScreenMapDraw, { passive: true });
    if (typeof ResizeObserver !== 'undefined') {
      const observer = new ResizeObserver(queueScreenMapDraw);
      screenMaps.forEach((map) => observer.observe(map));
    }
    document.fonts?.ready.then(queueScreenMapDraw);
    screenMaps.forEach((map) => map.querySelectorAll('img')
      .forEach((image) => image.addEventListener('load', queueScreenMapDraw, { once: true })));
  }
  function appendIdef1xMarkers(group, relation, fromX, fromY, fromDirection, toX, toY, toDirection) {
    group.append(svgElement('circle', {
      class: 'er-idef-child',
      cx: fromX.toFixed(1),
      cy: fromY.toFixed(1),
      r: 4
    }));
    const cardinality = relation.dataset.erFromCardinality;
    const cardinalityCode = cardinality === '0..*' ? ''
      : cardinality === '0..1' ? 'Z'
        : cardinality === '1..*' ? 'P'
          : cardinality;
    if (cardinalityCode) {
      group.append(svgElement('text', {
        class: 'er-idef-cardinality',
        x: (fromX + fromDirection * 9).toFixed(1),
        y: (fromY - 7).toFixed(1),
        'text-anchor': fromDirection > 0 ? 'start' : 'end'
      }, cardinalityCode));
    }
    if (relation.dataset.erToCardinality === '0..1') {
      const centerX = toX + toDirection * 4;
      group.append(svgElement('polygon', {
        class: 'er-idef-optional-parent',
        points: `${centerX.toFixed(1)},${(toY - 5).toFixed(1)} ${(centerX + toDirection * 5).toFixed(1)},${toY.toFixed(1)} ${centerX.toFixed(1)},${(toY + 5).toFixed(1)} ${(centerX - toDirection * 5).toFixed(1)},${toY.toFixed(1)}`
      }));
    }
  }
  function appendIeMarker(group, cardinality, x, y, direction) {
    const many = cardinality.endsWith('*');
    const required = cardinality.startsWith('1');
    if (many) {
      const junctionX = x + direction * 10;
      group.append(
        svgElement('line', {
          class: 'er-ie-marker',
          x1: x.toFixed(1), y1: (y - 6).toFixed(1),
          x2: junctionX.toFixed(1), y2: y.toFixed(1)
        }),
        svgElement('line', {
          class: 'er-ie-marker',
          x1: x.toFixed(1), y1: y.toFixed(1),
          x2: junctionX.toFixed(1), y2: y.toFixed(1)
        }),
        svgElement('line', {
          class: 'er-ie-marker',
          x1: x.toFixed(1), y1: (y + 6).toFixed(1),
          x2: junctionX.toFixed(1), y2: y.toFixed(1)
        })
      );
    } else {
      const maximumX = x + direction * 4;
      group.append(svgElement('line', {
        class: 'er-ie-marker',
        x1: maximumX.toFixed(1), y1: (y - 6).toFixed(1),
        x2: maximumX.toFixed(1), y2: (y + 6).toFixed(1)
      }));
    }
    const minimumX = x + direction * 18;
    if (required) {
      group.append(svgElement('line', {
        class: 'er-ie-marker',
        x1: minimumX.toFixed(1), y1: (y - 6).toFixed(1),
        x2: minimumX.toFixed(1), y2: (y + 6).toFixed(1)
      }));
    } else {
      group.append(svgElement('circle', {
        class: 'er-ie-zero',
        cx: minimumX.toFixed(1),
        cy: y.toFixed(1),
        r: 4
      }));
    }
  }
  function drawRelationshipDiagram(diagram) {
    const canvas = diagram.querySelector('[data-er-canvas]');
    const svg = diagram.querySelector('[data-er-connectors]');
    if (!canvas || !svg) return;
    const canvasBounds = canvas.getBoundingClientRect();
    const width = Math.max(1, canvas.clientWidth);
    const height = Math.max(1, canvas.clientHeight);
    const notation = diagram.dataset.erNotation === 'ie' ? 'ie' : 'idef1x';
    svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
    svg.replaceChildren();

    const tables = new Map(Array.from(diagram.querySelectorAll('[data-er-table]'))
      .map((table) => [table.dataset.erTable, table]));
    const columns = new Map(Array.from(diagram.querySelectorAll('[data-er-column]'))
      .map((column) => [column.dataset.erColumn, column]));
    const relations = Array.from(diagram.querySelectorAll('[data-er-relation]'));
    const endpointRelations = new Map();
    relations.forEach((relation) => {
      for (const side of ['From', 'To']) {
        const table = relation.dataset[`er${side}Table`];
        const column = relation.dataset[`er${side}Column`] || '@table';
        const key = `${table} ${column}`;
        if (!endpointRelations.has(key)) endpointRelations.set(key, []);
        endpointRelations.get(key).push(relation);
      }
    });
    function endpointOffset(relation, side) {
      const table = relation.dataset[`er${side}Table`];
      const column = relation.dataset[`er${side}Column`] || '@table';
      const related = endpointRelations.get(`${table} ${column}`) || [relation];
      const spacing = Math.min(13, 30 / Math.max(1, related.length - 1));
      return (related.indexOf(relation) - (related.length - 1) / 2) * spacing;
    }

    relations.forEach((relation) => {
      const fromTable = tables.get(relation.dataset.erFromTable);
      const toTable = tables.get(relation.dataset.erToTable);
      if (!fromTable || !toTable || fromTable.classList.contains('is-filtered')
          || toTable.classList.contains('is-filtered')) return;
      const fromEndpoint = columns.get(relation.dataset.erFromColumn)
        || fromTable.querySelector('[data-er-table-anchor]');
      const toEndpoint = columns.get(relation.dataset.erToColumn)
        || toTable.querySelector('[data-er-table-anchor]');
      if (!fromEndpoint || !toEndpoint) return;

      const fromCardBounds = fromTable.getBoundingClientRect();
      const toCardBounds = toTable.getBoundingClientRect();
      const fromEndpointBounds = fromEndpoint.getBoundingClientRect();
      const toEndpointBounds = toEndpoint.getBoundingClientRect();
      if (!fromCardBounds.width || !toCardBounds.width) return;

      const fromCenter = fromCardBounds.left + fromCardBounds.width / 2;
      const toCenter = toCardBounds.left + toCardBounds.width / 2;
      const fromY = fromEndpointBounds.top + fromEndpointBounds.height / 2 - canvasBounds.top
        + endpointOffset(relation, 'From');
      const toY = toEndpointBounds.top + toEndpointBounds.height / 2 - canvasBounds.top
        + endpointOffset(relation, 'To');
      const separatedHorizontally = fromCardBounds.right + 8 < toCardBounds.left
        || toCardBounds.right + 8 < fromCardBounds.left;
      let fromX;
      let toX;
      let path;
      let fromDirection;
      let toDirection;

      if (separatedHorizontally) {
        const direction = fromCenter < toCenter ? 1 : -1;
        fromDirection = direction;
        toDirection = -direction;
        fromX = (direction > 0 ? fromCardBounds.right : fromCardBounds.left) - canvasBounds.left;
        toX = (direction > 0 ? toCardBounds.left : toCardBounds.right) - canvasBounds.left;
        const control = Math.max(28, Math.abs(toX - fromX) * .45);
        path = `M ${fromX.toFixed(1)} ${fromY.toFixed(1)} C ${(fromX + direction * control).toFixed(1)} ${fromY.toFixed(1)}, ${(toX - direction * control).toFixed(1)} ${toY.toFixed(1)}, ${toX.toFixed(1)} ${toY.toFixed(1)}`;
      } else {
        const rightEdge = Math.max(fromCardBounds.right, toCardBounds.right) - canvasBounds.left;
        const leftEdge = Math.min(fromCardBounds.left, toCardBounds.left) - canvasBounds.left;
        const useRight = rightEdge + 24 <= width;
        fromDirection = useRight ? 1 : -1;
        toDirection = fromDirection;
        const routeX = useRight ? rightEdge + 20 : Math.max(2, leftEdge - 20);
        fromX = (useRight ? fromCardBounds.right : fromCardBounds.left) - canvasBounds.left;
        toX = (useRight ? toCardBounds.right : toCardBounds.left) - canvasBounds.left;
        path = `M ${fromX.toFixed(1)} ${fromY.toFixed(1)} H ${routeX.toFixed(1)} V ${toY.toFixed(1)} H ${toX.toFixed(1)}`;
      }

      const group = svgElement('g', { class: 'er-connector' });
      const relationshipClass = relation.dataset.erIdentifying === 'true'
        ? 'is-identifying'
        : 'is-non-identifying';
      group.append(
        svgElement('path', { class: 'er-connector-halo', d: path }),
        svgElement('path', { class: `er-connector-line ${relationshipClass}`, d: path })
      );
      if (notation === 'idef1x') {
        appendIdef1xMarkers(
          group, relation, fromX, fromY, fromDirection, toX, toY, toDirection);
      } else {
        appendIeMarker(group, relation.dataset.erFromCardinality, fromX, fromY, fromDirection);
        appendIeMarker(group, relation.dataset.erToCardinality, toX, toY, toDirection);
      }
      svg.append(group);
    });
  }
  function queueRelationshipDraw() {
    if (erAnimationFrame) cancelAnimationFrame(erAnimationFrame);
    erAnimationFrame = requestAnimationFrame(() => {
      erAnimationFrame = 0;
      erDiagrams.forEach(drawRelationshipDiagram);
    });
  }
  if (erDiagrams.length) {
    erDiagrams.forEach((diagram) => {
      const notationSelect = diagram.querySelector('[data-er-notation-select]');
      if (!notationSelect) return;
      diagram.dataset.erNotation = notationSelect.value === 'ie' ? 'ie' : 'idef1x';
      notationSelect.addEventListener('change', () => {
        diagram.dataset.erNotation = notationSelect.value === 'ie' ? 'ie' : 'idef1x';
        drawRelationshipDiagram(diagram);
      });
    });
    queueRelationshipDraw();
    window.addEventListener('resize', queueRelationshipDraw, { passive: true });
    if (typeof ResizeObserver !== 'undefined') {
      const observer = new ResizeObserver(queueRelationshipDraw);
      erDiagrams.forEach((diagram) => observer.observe(diagram));
    }
    document.fonts?.ready.then(queueRelationshipDraw);
  }
  const filter = document.querySelector('[data-table-filter]');
  filter?.addEventListener('input', () => {
    const query = filter.value.toLowerCase();
    document.querySelectorAll('[data-table]').forEach((item) => {
      item.classList.toggle('is-filtered', !item.dataset.table.toLowerCase().includes(query));
    });
    queueRelationshipDraw();
  });
  function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, (character) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[character]);
  }
})();
