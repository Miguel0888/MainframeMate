(function (maxItems, excerptLen) {
    var _start = Date.now();
    function _expired() { return (Date.now() - _start) > 3000; }

    var r = 'TITLE|' + document.title + '\n' + 'URL|' + window.location.href + '\n';

    // ── Phase 1: Find and tag interactive elements ──────────────────
    var sel = 'a[href],button,input,select,textarea,'
            + '[role=button],[role=link],[role=tab],'
            + '[role=menuitem],[role=checkbox],[role=radio]';

    var nodes = document.querySelectorAll(sel);
    var idx = 0;
    var scanned = 0;
    var maxScan = 500; // cap to avoid freezing on huge DOMs

    for (var i = 0; i < nodes.length && idx < maxItems && scanned < maxScan; i++) {
        if (_expired()) break;
        scanned++;

        var n = nodes[i];

        // Fast visibility check via offsetParent (no getComputedStyle – that triggers layout)
        try {
            if (!n.offsetParent && n.tagName !== 'BODY'
                && (!n.style || n.style.position !== 'fixed')) continue;
        } catch (e) { continue; }

        var tag = n.tagName.toLowerCase();
        var al  = n.getAttribute('aria-label') || '';
        var ph  = n.getAttribute('placeholder') || '';
        var tt  = n.getAttribute('title') || '';
        var tp  = n.getAttribute('type') || '';
        var nm  = n.getAttribute('name') || '';

        // Resolve href
        var hr = '';
        try {
            if (tag === 'a' && n.href) {
                hr = n.href;
            } else {
                var raw = n.getAttribute('href') || '';
                if (raw) {
                    try { hr = new URL(raw, location.href).href; } catch (e) { hr = raw; }
                }
            }
        } catch (e) {}

        // Text content (textContent only – innerText triggers layout)
        var tx = '';
        try { tx = (n.textContent || '').trim().substring(0, 60).replace(/\n/g, ' '); } catch (e) {}

        // Label: aria-label > placeholder > title > text > name
        var label = al || ph || tt || tx || nm || '';
        if (label.length > 80) label = label.substring(0, 80) + '…';

        // Action hint
        var hint = '';
        if (n.getAttribute('target') === '_blank') hint = 'new window';
        else if (hr && (hr.endsWith('.pdf') || hr.endsWith('.zip') || hr.endsWith('.exe'))) hint = 'download';
        else if (tp === 'password') hint = 'password field';

        // Tag element for Phase 2 CSS locate
        n.setAttribute('data-mm-menu-id', '' + idx);
        r += 'EL|' + idx + '|' + tag + '|' + label + '|' + hr + '|' + hint + '\n';
        idx++;
    }

    // ── Phase 2: Extract page excerpt ───────────────────────────────
    var excEl = document.querySelector('article')
             || document.querySelector('[role=main]')
             || document.querySelector('main')
             || document.querySelector('#content');

    var exc = '';

    if (excEl && !_expired()) {
        // Direct textContent on a semantic container (no cloneNode!)
        try {
            exc = (excEl.textContent || '').replace(/\s+/g, ' ').trim().substring(0, excerptLen);
        } catch (e) {}
    } else if (!_expired()) {
        // Fallback: TreeWalker over body, skipping script/style/nav
        try {
            var tw = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
                acceptNode: function (nd) {
                    var p = nd.parentElement;
                    if (!p) return NodeFilter.FILTER_SKIP;
                    var pn = p.tagName;
                    if (pn === 'SCRIPT' || pn === 'STYLE' || pn === 'NOSCRIPT' || pn === 'NAV')
                        return NodeFilter.FILTER_REJECT;
                    return NodeFilter.FILTER_ACCEPT;
                }
            });
            var buf = [];
            var len = 0;
            while (tw.nextNode() && len < excerptLen && !_expired()) {
                var v = tw.currentNode.nodeValue.trim();
                if (v.length > 2) { buf.push(v); len += v.length; }
            }
            exc = buf.join(' ').substring(0, excerptLen);
        } catch (e) {}
    }

    r += 'EXCERPT|' + exc;
    return r;
})(__MAX_ITEMS__, __EXCERPT_LEN__)
