/*
 * browser-shim.js — Minimal browser-like environment for running Mermaid inside GraalJS.
 *
 * This shim provides enough of the window/document/navigator API surface
 * so that mermaid.min.js (IIFE build, v11.x) can load and initialize.
 * It is NOT a general-purpose browser polyfill.
 */

// ── Event target mixin ──────────────────────────────────────────────────────
function EventTargetMixin(obj) {
    obj._listeners = {};
    obj.addEventListener = function(type, fn) {
        if (!obj._listeners[type]) obj._listeners[type] = [];
        obj._listeners[type].push(fn);
    };
    obj.removeEventListener = function(type, fn) {
        if (!obj._listeners[type]) return;
        var idx = obj._listeners[type].indexOf(fn);
        if (idx >= 0) obj._listeners[type].splice(idx, 1);
    };
    obj.dispatchEvent = function(evt) {
        var type = evt.type || evt;
        var fns = obj._listeners[type] || [];
        for (var i = 0; i < fns.length; i++) {
            try { fns[i](evt); } catch(e) { /* ignore */ }
        }
    };
    return obj;
}

// ── Minimal selector engine (supports #id, .class, tagName, and simple combos) ─
function _matchesSelector(el, sel) {
    if (!el || el.nodeType !== 1) return false;
    sel = sel.trim();
    // Handle comma-separated selectors (OR)
    if (sel.indexOf(',') >= 0) {
        var parts = sel.split(',');
        for (var i = 0; i < parts.length; i++) {
            if (_matchesSelector(el, parts[i])) return true;
        }
        return false;
    }
    // Trim descendant parts - only match the last segment for .matches()
    var segments = sel.split(/\s+/);
    var last = segments[segments.length - 1];
    // Simple selectors
    if (last === '*') return true;
    // :first-child pseudo-selector — used by D3's insert(tag, ":first-child")
    // to insert shape elements BEFORE labels in node groups
    if (last === ':first-child') {
        if (!el.parentNode || !el.parentNode.childNodes) return false;
        for (var fc = 0; fc < el.parentNode.childNodes.length; fc++) {
            if (el.parentNode.childNodes[fc].nodeType === 1) return el.parentNode.childNodes[fc] === el;
        }
        return false;
    }
    // :last-child pseudo-selector
    if (last === ':last-child') {
        if (!el.parentNode || !el.parentNode.childNodes) return false;
        for (var lc = el.parentNode.childNodes.length - 1; lc >= 0; lc--) {
            if (el.parentNode.childNodes[lc].nodeType === 1) return el.parentNode.childNodes[lc] === el;
        }
        return false;
    }
    if (last.charAt(0) === '#') return el.id === last.substring(1);
    if (last.charAt(0) === '.') return (' ' + (el.className || '') + ' ').indexOf(' ' + last.substring(1) + ' ') >= 0;
    // [attr="value"] selector
    var attrMatch = last.match(/^\[([a-zA-Z_-]+)(?:([~|^$*]?)=["']([^"']*)["'])?\]$/);
    if (attrMatch) {
        var attrName = attrMatch[1];
        var attrOp = attrMatch[2] || '';
        var attrVal = attrMatch[3];
        var actual = attrName === 'id' ? el.id : (attrName === 'class' ? el.className : (el._attrs ? el._attrs[attrName] : el.getAttribute ? el.getAttribute(attrName) : null));
        if (attrVal === undefined) return actual !== undefined && actual !== null; // [attr] = has attribute
        if (attrOp === '') return actual === attrVal; // [attr=val]
        if (attrOp === '~') return actual && (' ' + actual + ' ').indexOf(' ' + attrVal + ' ') >= 0; // [attr~=val]
        if (attrOp === '|') return actual && (actual === attrVal || actual.indexOf(attrVal + '-') === 0); // [attr|=val]
        if (attrOp === '^') return actual && actual.indexOf(attrVal) === 0; // [attr^=val]
        if (attrOp === '$') return actual && actual.indexOf(attrVal, actual.length - attrVal.length) >= 0; // [attr$=val]
        if (attrOp === '*') return actual && actual.indexOf(attrVal) >= 0; // [attr*=val]
        return false;
    }
    // Tag name (no special chars)
    if (last.indexOf('#') < 0 && last.indexOf('.') < 0 && last.indexOf('[') < 0) {
        return (el.tagName || '').toLowerCase() === last.toLowerCase();
    }
    // tag#id
    var m = last.match(/^([a-zA-Z][a-zA-Z0-9-]*)#([^\.\[]+)/);
    if (m) return (el.tagName || '').toLowerCase() === m[1].toLowerCase() && el.id === m[2];
    // tag.class
    m = last.match(/^([a-zA-Z][a-zA-Z0-9-]*)\.([^\.\[#]+)/);
    if (m) return (el.tagName || '').toLowerCase() === m[1].toLowerCase() && (' ' + (el.className || '') + ' ').indexOf(' ' + m[2] + ' ') >= 0;
    // tag[attr="value"]
    m = last.match(/^([a-zA-Z][a-zA-Z0-9-]*)\[/);
    if (m) {
        var tagPart = m[1];
        var attrPart = last.substring(tagPart.length);
        return (el.tagName || '').toLowerCase() === tagPart.toLowerCase() && _matchesSelector(el, attrPart);
    }
    return false;
}

function _querySelectorAll(root, sel) {
    var results = [];
    function walk(node) {
        if (!node) return;
        var children = node.childNodes || [];
        for (var i = 0; i < children.length; i++) {
            var child = children[i];
            if (child.nodeType === 1 && _matchesSelector(child, sel)) {
                results.push(child);
            }
            walk(child);
        }
    }
    walk(root);
    return results;
}

function _querySelector(root, sel) {
    var all = _querySelectorAll(root, sel);
    return all.length > 0 ? all[0] : null;
}

// ── CSSStyleDeclaration stub ────────────────────────────────────────────────
function createStyleObject() {
    var style = {};
    style._props = {};
    style.setProperty = function(name, value, priority) {
        style._props[name] = value;
        // Also set camelCase version for direct property access
        var camel = name.replace(/-([a-z])/g, function(m, c) { return c.toUpperCase(); });
        style[camel] = value;
    };
    style.getPropertyValue = function(name) {
        return style._props[name] || '';
    };
    style.removeProperty = function(name) {
        var old = style._props[name] || '';
        delete style._props[name];
        var camel = name.replace(/-([a-z])/g, function(m, c) { return c.toUpperCase(); });
        delete style[camel];
        return old;
    };
    style.getPropertyPriority = function(name) { return ''; };
    style.cssText = '';
    style.length = 0;
    style.item = function(index) { return ''; };
    return style;
}

// ── SVG namespace constants ──────────────────────────────────────────────────
var SVG_NS = 'http://www.w3.org/2000/svg';
var XHTML_NS = 'http://www.w3.org/1999/xhtml';
var XLINK_NS = 'http://www.w3.org/1999/xlink';

// ── XML attribute escaping ──────────────────────────────────────────────────
function _escapeXmlAttr(val) {
    if (val == null) return '';
    return String(val).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
function _escapeXmlText(val) {
    if (val == null) return '';
    return String(val).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// ── Element serialization helper ────────────────────────────────────────────
function _serializeNode(node, parentNs) {
    if (!node) return '';
    if (node.nodeType === 3) return _escapeXmlText(node.textContent || '');
    if (node.nodeType !== 1) return '';

    var tag = (node.tagName || 'div').toLowerCase();
    var ns = node.namespaceURI || XHTML_NS;
    var attrs = '';

    // Emit xmlns only when namespace differs from parent
    if (ns && ns !== parentNs) {
        attrs += ' xmlns="' + ns + '"';
    }

    if (node._attrs) {
        var keys = Object.keys(node._attrs);
        for (var i = 0; i < keys.length; i++) {
            var key = keys[i];
            var val = node._attrs[key];
            // Don't double-emit xmlns if we already added it above
            if (key === 'xmlns' && val === ns && ns !== parentNs) continue;
            // Skip alignment-baseline — Batik rejects "central" value
            if (key === 'alignment-baseline') continue;
            // Skip function values (D3/Mermaid may store callbacks in attrs)
            if (typeof val === 'function') continue;
            attrs += ' ' + key + '="' + _escapeXmlAttr(val) + '"';
        }
    }

    // Serialize style properties inline
    if (node.style && node.style._props) {
        var styleKeys = Object.keys(node.style._props);
        if (styleKeys.length > 0) {
            var styleStr = '';
            for (var s = 0; s < styleKeys.length; s++) {
                var sval = node.style._props[styleKeys[s]];
                // Skip function values (polyfill methods, callbacks)
                if (typeof sval === 'function') continue;
                if (styleStr) styleStr += '; ';
                styleStr += styleKeys[s] + ': ' + sval;
            }
            if (styleStr && (!node._attrs || !node._attrs['style'])) {
                attrs += ' style="' + _escapeXmlAttr(styleStr) + '"';
            }
        }
    }

    // Serialize children
    var inner = '';
    if (node.childNodes && node.childNodes.length > 0) {
        for (var j = 0; j < node.childNodes.length; j++) {
            inner += _serializeNode(node.childNodes[j], ns);
        }
    } else if (node._innerHTMLRaw) {
        inner = node._innerHTMLRaw;
    }

    // For <style> elements, wrap content in CDATA so CSS selectors with >
    // don't break XML parsing in downstream Java (Batik, JAXP)
    if (tag === 'style' && inner && (inner.indexOf('>') >= 0 || inner.indexOf('<') >= 0 || inner.indexOf('&') >= 0)) {
        if (inner.indexOf('<![CDATA[') < 0) {
            inner = '<![CDATA[' + inner + ']]>';
        }
    }

    // Self-closing void SVG elements
    var voidSvgTags = { 'path': 1, 'circle': 1, 'ellipse': 1, 'line': 1, 'polyline': 1,
                        'polygon': 1, 'rect': 1, 'use': 1, 'image': 1, 'br': 1, 'hr': 1,
                        'img': 1, 'input': 1, 'meta': 1, 'link': 1 };
    if (!inner && voidSvgTags[tag]) {
        return '<' + tag + attrs + '/>';
    }

    return '<' + tag + attrs + '>' + inner + '</' + tag + '>';
}

// ── Text width estimation (for layout without a real rendering engine) ──────
function _estimateTextWidth(el) {
    var text = _collectAllText(el);
    if (!text) return 0;

    // Sanity check: if the "text" is actually CSS or huge markup content, cap it
    if (text.length > 200 || text.indexOf('{') >= 0 || text.indexOf('}') >= 0) {
        // This is likely CSS content from a <style> block that leaked into text measurement
        // Extract only what looks like a label (alphanumeric + common punctuation)
        var stripped = text.replace(/[^a-zA-Z0-9äöüÄÖÜß\s.,!?:;\-()]/g, '').trim();
        if (stripped.length === 0) return 0;
        if (stripped.length > 50) stripped = stripped.substring(0, 50);
        text = stripped;
    }

    // Use Java bridge for accurate pixel-level text measurement if available
    if (typeof javaBridge !== 'undefined' && javaBridge.measureTextWidth) {
        var fontFamily = _resolveFontFamily(el) || '"trebuchet ms", verdana, arial, sans-serif';
        var fontSize = _resolveFontSize(el) || 16;
        try {
            return javaBridge.measureTextWidth(text, fontFamily, fontSize);
        } catch (e) {
            // Fall through to estimation
        }
    }

    // Fallback: rough estimation (~8px per character at 16px font)
    return text.length * 8 + 16;
}

/**
 * Resolve the effective font-family for an element by checking:
 * 1. inline style font-family
 * 2. class-based defaults (Mermaid uses specific classes)
 * 3. inherited from ancestors
 * 4. global default
 */
function _resolveFontFamily(el) {
    var current = el;
    while (current && current.nodeType === 1) {
        // Check style object
        if (current.style && current.style.fontFamily) {
            return current.style.fontFamily;
        }
        // Check style attribute string
        var styleAttr = current._attrs && current._attrs['style'];
        if (styleAttr) {
            var match = styleAttr.match(/font-family\s*:\s*([^;]+)/);
            if (match) return match[1].trim();
        }
        // Check font-family attribute (SVG text elements)
        var ff = current._attrs && current._attrs['font-family'];
        if (ff) return ff;
        current = current.parentNode;
    }
    return null;
}

/**
 * Resolve the effective font-size for an element.
 */
function _resolveFontSize(el) {
    var current = el;
    while (current && current.nodeType === 1) {
        // Check style object
        if (current.style && current.style.fontSize) {
            return parseFloat(current.style.fontSize) || 16;
        }
        // Check style attribute string
        var styleAttr = current._attrs && current._attrs['style'];
        if (styleAttr) {
            var match = styleAttr.match(/font-size\s*:\s*([\d.]+)/);
            if (match) return parseFloat(match[1]) || 16;
        }
        // Check font-size attribute (SVG text elements)
        var fs = current._attrs && current._attrs['font-size'];
        if (fs) return parseFloat(fs) || 16;
        current = current.parentNode;
    }
    return null;
}

/**
 * Recursively collect all text content from an element and its descendants.
 * This handles: direct text nodes, <tspan>, <span>, <div>, foreignObject, innerHTML.
 */
function _collectAllText(el) {
    if (!el) return '';
    // Text node
    if (el.nodeType === 3) return el.textContent || '';
    // Element node — collect from children recursively
    var text = '';
    if (el.childNodes && el.childNodes.length > 0) {
        for (var i = 0; i < el.childNodes.length; i++) {
            text += _collectAllText(el.childNodes[i]);
        }
    }
    // Fallback: _textContent property (set via textContent setter)
    if (!text && el._textContent) text = el._textContent;
    // Fallback: innerHTML raw — only use if it looks like a simple label, not full markup/CSS.
    // Mermaid sets innerHTML to complex structures like <style>...</style><g>...</g> which
    // after tag-stripping becomes a huge CSS string, producing wildly inflated measurements.
    if (!text && el._innerHTMLRaw) {
        var raw = el._innerHTMLRaw;
        // Skip if it contains <style>, <svg>, or lots of CSS-like content
        if (raw.indexOf('<style') < 0 && raw.indexOf('<svg') < 0 && raw.length < 500) {
            text = raw.replace(/<[^>]*>/g, '').trim();
        }
    }
    return text;
}

// ── Dimension computation for getBBox / getBoundingClientRect ────────────────
function _computeElementDims(el) {
    var tag = (el.tagName || '').toLowerCase();
    var attrs = el._attrs || {};

    // display:none elements have zero dimensions (critical for Cytoscape headless detection)
    var styleAttr = attrs['style'] || '';
    if (el.style && el.style._props && el.style._props['display'] === 'none') {
        return { x: 0, y: 0, w: 0, h: 0 };
    }
    if (styleAttr.indexOf('display') >= 0 && styleAttr.match(/display\s*:\s*none/i)) {
        return { x: 0, y: 0, w: 0, h: 0 };
    }

    // Helper to read numeric attribute
    function num(name) {
        var v = attrs[name];
        if (v === undefined || v === null || v === '') return NaN;
        return parseFloat(v);
    }

    // 1) rect — explicit width/height
    if (tag === 'rect') {
        var rw = num('width'), rh = num('height'), rx = num('x'), ry = num('y');
        if (!isNaN(rw) && !isNaN(rh)) {
            return { x: isNaN(rx) ? 0 : rx, y: isNaN(ry) ? 0 : ry, w: rw, h: rh };
        }
    }

    // 2) circle — use r
    if (tag === 'circle') {
        var r = num('r'), cx = num('cx'), cy = num('cy');
        if (!isNaN(r)) {
            cx = isNaN(cx) ? 0 : cx; cy = isNaN(cy) ? 0 : cy;
            return { x: cx - r, y: cy - r, w: 2 * r, h: 2 * r };
        }
    }

    // 3) ellipse — use rx, ry
    if (tag === 'ellipse') {
        var erx = num('rx'), ery = num('ry'), ecx = num('cx'), ecy = num('cy');
        if (!isNaN(erx) && !isNaN(ery)) {
            ecx = isNaN(ecx) ? 0 : ecx; ecy = isNaN(ecy) ? 0 : ecy;
            return { x: ecx - erx, y: ecy - ery, w: 2 * erx, h: 2 * ery };
        }
    }

    // 4) polygon — parse points attribute
    if (tag === 'polygon' || tag === 'polyline') {
        var pts = attrs['points'];
        if (pts) {
            var coords = pts.trim().split(/[\s,]+/);
            var minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
            for (var i = 0; i + 1 < coords.length; i += 2) {
                var px = parseFloat(coords[i]), py = parseFloat(coords[i + 1]);
                if (!isNaN(px) && !isNaN(py)) {
                    if (px < minX) minX = px; if (px > maxX) maxX = px;
                    if (py < minY) minY = py; if (py > maxY) maxY = py;
                }
            }
            if (minX < Infinity) {
                // Also account for transform on this element
                var tw = maxX - minX, th = maxY - minY;
                var tOff = _parseTranslate(el);
                return { x: minX + tOff[0], y: minY + tOff[1], w: tw, h: th };
            }
        }
    }

    // 4b) path — parse d attribute for bounding box
    if (tag === 'path') {
        var pd = attrs['d'];
        if (pd) {
            var pathBox = _parsePathBBox(pd);
            if (pathBox) {
                var ptOff = _parseTranslate(el);
                return { x: pathBox.minX + ptOff[0], y: pathBox.minY + ptOff[1],
                         w: pathBox.maxX - pathBox.minX, h: pathBox.maxY - pathBox.minY };
            }
        }
    }

    // 5) line — x1,y1,x2,y2
    if (tag === 'line') {
        var x1 = num('x1'), y1 = num('y1'), x2 = num('x2'), y2 = num('y2');
        if (!isNaN(x1) && !isNaN(y1) && !isNaN(x2) && !isNaN(y2)) {
            return { x: Math.min(x1, x2), y: Math.min(y1, y2),
                     w: Math.abs(x2 - x1) || 1, h: Math.abs(y2 - y1) || 1 };
        }
    }

    // 6) text — estimate from content; height based on actual font-size
    //    Real browsers return getBBox height ≈ fontSize (ascent + descent).
    //    Using a hardcoded 24 inflated layout spacing (especially sequence diagram
    //    message-text ↔ arrow-line gap).
    if (tag === 'text' || tag === 'tspan') {
        var tw = _estimateTextWidth(el);
        if (tw > 0) {
            var tx = num('x'), ty = num('y');
            var fs6 = _resolveFontSize(el) || 16;
            var ascent6 = Math.round(fs6 * 0.8);   // baseline → top of glyphs
            var th6     = Math.round(fs6);           // ascent + descent ≈ fontSize
            return { x: isNaN(tx) ? 0 : tx - tw / 2,
                     y: isNaN(ty) ? -ascent6 : ty - ascent6,
                     w: tw, h: th6 };
        }
    }

    // 7) foreignObject — explicit width/height, or estimate from text content
    if (tag === 'foreignobject') {
        var fw = num('width'), fh = num('height'), fx = num('x'), fy = num('y');
        if (!isNaN(fw) && !isNaN(fh) && fw > 0 && fh > 0) {
            return { x: isNaN(fx) ? 0 : fx, y: isNaN(fy) ? 0 : fy, w: fw, h: fh };
        }
        // No dimensions set yet — estimate from text content (Mermaid measures before setting w/h)
        var foText = _estimateTextWidth(el);
        if (foText > 0) {
            var fs7 = _resolveFontSize(el) || 16;
            return { x: isNaN(fx) ? 0 : fx, y: isNaN(fy) ? 0 : fy, w: foText, h: Math.round(fs7) };
        }
    }

    // 7b) HTML elements (div, span, p, label, etc.) — used inside foreignObject
    if (tag === 'div' || tag === 'span' || tag === 'p' || tag === 'label' || tag === 'b' || tag === 'i') {
        var htw = _estimateTextWidth(el);
        if (htw > 0) {
            var fs7b = _resolveFontSize(el) || 16;
            return { x: 0, y: 0, w: htw, h: Math.round(fs7b) };
        }
    }

    // 8) g / svg / other containers — aggregate child bboxes with proper min/max
    if (el.childNodes && el.childNodes.length > 0) {
        var cMinX = Infinity, cMinY = Infinity, cMaxX = -Infinity, cMaxY = -Infinity;
        var found = false;
        for (var i = 0; i < el.childNodes.length; i++) {
            var child = el.childNodes[i];
            if (child.getBBox) {
                var cb = child.getBBox();
                if (cb.w === undefined) { cb.w = cb.width; cb.h = cb.height; }
                if (cb.w > 0 || cb.h > 0) {
                    // Account for child's transform
                    var cOff = _parseTranslate(child);
                    var cx1 = cb.x + cOff[0], cy1 = cb.y + cOff[1];
                    var cx2 = cx1 + cb.w, cy2 = cy1 + cb.h;
                    if (cx1 < cMinX) cMinX = cx1;
                    if (cy1 < cMinY) cMinY = cy1;
                    if (cx2 > cMaxX) cMaxX = cx2;
                    if (cy2 > cMaxY) cMaxY = cy2;
                    found = true;
                }
            }
        }
        if (found && cMaxX > cMinX && cMaxY > cMinY) {
            return { x: cMinX, y: cMinY, w: cMaxX - cMinX, h: cMaxY - cMinY };
        }
    }

    // Fallback: text-based estimate or reasonable container default
    var textLen = _estimateTextWidth(el);
    if (textLen > 0) {
        return { x: 0, y: 0, w: textLen, h: 24 };
    }
    // Container elements (div, body, html, canvas) get large defaults
    // so libraries like Cytoscape.js can compute proper layout bounds
    if (tag === 'div' || tag === 'body' || tag === 'html' || tag === 'canvas' || tag === 'section') {
        return { x: 0, y: 0, w: 800, h: 600 };
    }
    return { x: 0, y: 0, w: 20, h: 20 };
}

/**
 * Parse an SVG path "d" attribute and compute its bounding box.
 * Handles absolute and relative M, L, H, V, C, S, Q, T, A, Z commands.
 * For bezier curves, control points are included (conservative bbox).
 * Returns {minX, minY, maxX, maxY} or null if parsing fails.
 */
function _parsePathBBox(d) {
    if (!d) return null;
    var minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    var found = false;
    var curX = 0, curY = 0;
    var startX = 0, startY = 0;

    function update(x, y) {
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
        found = true;
    }

    // Tokenize: split by command letters, keeping the command
    var tokens = d.match(/[MmLlHhVvCcSsQqTtAaZz][^MmLlHhVvCcSsQqTtAaZz]*/g);
    if (!tokens) return null;

    for (var i = 0; i < tokens.length; i++) {
        var token = tokens[i].trim();
        var cmd = token.charAt(0);
        var args = token.substring(1).trim();
        var nums = args.match(/-?[\d]+(?:\.[\d]*)?(?:[eE][+-]?[\d]+)?/g);
        var values = [];
        if (nums) {
            for (var j = 0; j < nums.length; j++) {
                values.push(parseFloat(nums[j]));
            }
        }

        var isRel = cmd === cmd.toLowerCase();
        var CMD = cmd.toUpperCase();

        switch (CMD) {
            case 'M':
                for (var k = 0; k + 1 < values.length; k += 2) {
                    var mx = isRel ? curX + values[k] : values[k];
                    var my = isRel ? curY + values[k + 1] : values[k + 1];
                    update(mx, my);
                    curX = mx; curY = my;
                    if (k === 0) { startX = mx; startY = my; }
                    // After first pair, implicit L commands
                }
                break;
            case 'L':
            case 'T':
                for (var k = 0; k + 1 < values.length; k += 2) {
                    var lx = isRel ? curX + values[k] : values[k];
                    var ly = isRel ? curY + values[k + 1] : values[k + 1];
                    update(lx, ly);
                    curX = lx; curY = ly;
                }
                break;
            case 'H':
                for (var k = 0; k < values.length; k++) {
                    var hx = isRel ? curX + values[k] : values[k];
                    update(hx, curY);
                    curX = hx;
                }
                break;
            case 'V':
                for (var k = 0; k < values.length; k++) {
                    var vy = isRel ? curY + values[k] : values[k];
                    update(curX, vy);
                    curY = vy;
                }
                break;
            case 'C':
                for (var k = 0; k + 5 < values.length; k += 6) {
                    for (var p = 0; p < 3; p++) {
                        var cx = isRel ? curX + values[k + p * 2] : values[k + p * 2];
                        var cy = isRel ? curY + values[k + p * 2 + 1] : values[k + p * 2 + 1];
                        update(cx, cy);
                    }
                    curX = isRel ? curX + values[k + 4] : values[k + 4];
                    curY = isRel ? curY + values[k + 5] : values[k + 5];
                }
                break;
            case 'S':
                for (var k = 0; k + 3 < values.length; k += 4) {
                    for (var p = 0; p < 2; p++) {
                        var sx = isRel ? curX + values[k + p * 2] : values[k + p * 2];
                        var sy = isRel ? curY + values[k + p * 2 + 1] : values[k + p * 2 + 1];
                        update(sx, sy);
                    }
                    curX = isRel ? curX + values[k + 2] : values[k + 2];
                    curY = isRel ? curY + values[k + 3] : values[k + 3];
                }
                break;
            case 'Q':
                for (var k = 0; k + 3 < values.length; k += 4) {
                    for (var p = 0; p < 2; p++) {
                        var qx = isRel ? curX + values[k + p * 2] : values[k + p * 2];
                        var qy = isRel ? curY + values[k + p * 2 + 1] : values[k + p * 2 + 1];
                        update(qx, qy);
                    }
                    curX = isRel ? curX + values[k + 2] : values[k + 2];
                    curY = isRel ? curY + values[k + 3] : values[k + 3];
                }
                break;
            case 'A':
                for (var k = 0; k + 6 < values.length; k += 7) {
                    var ax = isRel ? curX + values[k + 5] : values[k + 5];
                    var ay = isRel ? curY + values[k + 6] : values[k + 6];
                    update(ax, ay);
                    // Conservative: include arc radius extent
                    var arx = values[k], ary = values[k + 1];
                    update(ax - arx, ay - ary);
                    update(ax + arx, ay + ary);
                    curX = ax; curY = ay;
                }
                break;
            case 'Z':
                curX = startX; curY = startY;
                break;
        }
    }

    if (!found || minX >= Infinity) return null;
    return { minX: minX, minY: minY, maxX: maxX, maxY: maxY };
}

// Parse translate(x, y) from an element's transform attribute
function _parseTranslate(el) {
    var t = el._attrs && el._attrs['transform'];
    if (!t && el.getAttribute) t = el.getAttribute('transform');
    if (!t) return [0, 0];
    var m = t.match(/translate\(\s*(-?[\d.]+)\s*[,\s]\s*(-?[\d.]+)\s*\)/);
    if (m) return [parseFloat(m[1]), parseFloat(m[2])];
    return [0, 0];
}

// ── DOM Element factory ─────────────────────────────────────────────────────
function createDomElement(tagName, namespaceURI) {
    var el = EventTargetMixin({});
    el.nodeType = 1;
    el.tagName = tagName ? tagName.toUpperCase() : 'DIV';
    el.nodeName = el.tagName;
    el.namespaceURI = namespaceURI || XHTML_NS;
    el.ownerDocument = null; // will be set to document after document is created
    el.className = '';
    el.id = '';
    el._innerHTMLRaw = '';
    el.style = createStyleObject();
    el.childNodes = [];
    el.children = [];
    el.parentNode = null;
    el.firstChild = null;
    el.lastChild = null;
    el.nextSibling = null;
    el.previousSibling = null;
    el.attributes = [];

    // classList API — required by Mermaid's sequence diagram renderer
    // for loop/alt/note/activation box rendering
    el.classList = {
        _el: el,
        add: function() {
            var classes = (el.className || '').split(/\s+/).filter(function(c) { return c; });
            for (var i = 0; i < arguments.length; i++) {
                var cls = arguments[i];
                if (cls && classes.indexOf(cls) < 0) classes.push(cls);
            }
            el.className = classes.join(' ');
            if (el._attrs) el._attrs['class'] = el.className;
        },
        remove: function() {
            var classes = (el.className || '').split(/\s+/).filter(function(c) { return c; });
            for (var i = 0; i < arguments.length; i++) {
                var cls = arguments[i];
                var idx = classes.indexOf(cls);
                if (idx >= 0) classes.splice(idx, 1);
            }
            el.className = classes.join(' ');
            if (el._attrs) el._attrs['class'] = el.className;
        },
        contains: function(cls) {
            return (' ' + (el.className || '') + ' ').indexOf(' ' + cls + ' ') >= 0;
        },
        toggle: function(cls, force) {
            if (force !== undefined) {
                if (force) { el.classList.add(cls); return true; }
                else { el.classList.remove(cls); return false; }
            }
            if (el.classList.contains(cls)) { el.classList.remove(cls); return false; }
            el.classList.add(cls); return true;
        },
        replace: function(oldCls, newCls) {
            if (!el.classList.contains(oldCls)) return false;
            el.classList.remove(oldCls);
            el.classList.add(newCls);
            return true;
        },
        item: function(index) {
            var classes = (el.className || '').split(/\s+/).filter(function(c) { return c; });
            return index < classes.length ? classes[index] : null;
        },
        toString: function() { return el.className || ''; }
    };
    Object.defineProperty(el.classList, 'length', {
        get: function() {
            return (el.className || '').split(/\s+/).filter(function(c) { return c; }).length;
        },
        configurable: true
    });
    el._attrs = {};

    // Make innerHTML a dynamic getter/setter so DOM-built children are serialized
    Object.defineProperty(el, 'innerHTML', {
        get: function() {
            if (el.childNodes && el.childNodes.length > 0) {
                var result = '';
                for (var i = 0; i < el.childNodes.length; i++) {
                    result += _serializeNode(el.childNodes[i], el.namespaceURI);
                }
                return result;
            }
            return el._innerHTMLRaw || '';
        },
        set: function(val) {
            el._innerHTMLRaw = val;
            el.childNodes = [];
            el.children = [];
            el.firstChild = null;
            el.lastChild = null;
        },
        configurable: true,
        enumerable: true
    });

    // outerHTML getter
    Object.defineProperty(el, 'outerHTML', {
        get: function() {
            return _serializeNode(el, (el.parentNode ? el.parentNode.namespaceURI : null) || XHTML_NS);
        },
        configurable: true,
        enumerable: true
    });

    // textContent: getter returns text from all descendants, setter clears children
    Object.defineProperty(el, 'textContent', {
        get: function() {
            if (el.childNodes && el.childNodes.length > 0) {
                var result = '';
                for (var i = 0; i < el.childNodes.length; i++) {
                    var child = el.childNodes[i];
                    if (child.nodeType === 3) result += child.textContent || '';
                    else if (child.textContent) result += child.textContent;
                }
                return result;
            }
            return el._textContent || '';
        },
        set: function(val) {
            el._textContent = val;
            el.childNodes = [];
            el.children = [];
            // Create a text node child so serialization picks up the content
            if (val != null && val !== '') {
                var textNode = { nodeType: 3, textContent: String(val), ownerDocument: el.ownerDocument };
                el.childNodes.push(textNode);
                el.firstChild = textNode;
                el.lastChild = textNode;
            } else {
                el.firstChild = null;
                el.lastChild = null;
            }
        },
        configurable: true,
        enumerable: true
    });

    el.setAttribute = function(key, value) {
        el._attrs[key] = String(value);
        if (key === 'class') el.className = String(value);
        if (key === 'id') el.id = String(value);
        // Sync data-* attributes to dataset
        if (key.indexOf('data-') === 0) {
            var camel = key.substring(5).replace(/-([a-z])/g, function(m, c) { return c.toUpperCase(); });
            el.dataset[camel] = String(value);
        }
    };
    el.getAttribute = function(key) {
        if (key === 'class') return el.className || null;
        return el._attrs[key] !== undefined ? el._attrs[key] : null;
    };
    el.removeAttribute = function(key) {
        delete el._attrs[key];
    };
    el.hasAttribute = function(key) {
        return el._attrs[key] !== undefined;
    };
    el.getAttributeNS = function(ns, key) { return el.getAttribute(key); };
    el.setAttributeNS = function(ns, key, value) { el.setAttribute(key, value); };
    el.removeAttributeNS = function(ns, key) { el.removeAttribute(key); };

    // Helper: maintain previousSibling / nextSibling for all childNodes
    function _updateSiblings(parent) {
        var cn = parent.childNodes;
        for (var si = 0; si < cn.length; si++) {
            cn[si].previousSibling = si > 0 ? cn[si - 1] : null;
            cn[si].nextSibling = si < cn.length - 1 ? cn[si + 1] : null;
        }
        parent.firstChild = cn.length > 0 ? cn[0] : null;
        parent.lastChild = cn.length > 0 ? cn[cn.length - 1] : null;
    }

    el.appendChild = function(child) {
        if (child.parentNode && child.parentNode.removeChild) {
            child.parentNode.removeChild(child);
        }
        el.childNodes.push(child);
        if (child.nodeType === 1) el.children.push(child);
        child.parentNode = el;
        _updateSiblings(el);
        return child;
    };
    el.removeChild = function(child) {
        var idx = el.childNodes.indexOf(child);
        if (idx >= 0) el.childNodes.splice(idx, 1);
        var cidx = el.children.indexOf(child);
        if (cidx >= 0) el.children.splice(cidx, 1);
        child.parentNode = null;
        child.previousSibling = null;
        child.nextSibling = null;
        _updateSiblings(el);
        return child;
    };
    el.insertBefore = function(newNode, refNode) {
        if (!refNode) return el.appendChild(newNode);
        if (newNode.parentNode && newNode.parentNode.removeChild) {
            newNode.parentNode.removeChild(newNode);
        }
        var idx = el.childNodes.indexOf(refNode);
        if (idx >= 0) {
            el.childNodes.splice(idx, 0, newNode);
            if (newNode.nodeType === 1) {
                var cidx = el.children.indexOf(refNode);
                if (cidx >= 0) el.children.splice(cidx, 0, newNode);
                else el.children.push(newNode);
            }
        } else {
            el.childNodes.push(newNode);
            if (newNode.nodeType === 1) el.children.push(newNode);
        }
        newNode.parentNode = el;
        _updateSiblings(el);
        return newNode;
    };
    el.replaceChild = function(newChild, oldChild) {
        var idx = el.childNodes.indexOf(oldChild);
        if (idx >= 0) el.childNodes[idx] = newChild;
        newChild.parentNode = el;
        oldChild.parentNode = null;
        return oldChild;
    };
    el.contains = function(other) {
        if (other === el) return true;
        for (var i = 0; i < el.childNodes.length; i++) {
            if (el.childNodes[i] === other) return true;
            if (el.childNodes[i].contains && el.childNodes[i].contains(other)) return true;
        }
        return false;
    };

    // insertAdjacentElement — used by Mermaid for sequence diagram elements
    el.insertAdjacentElement = function(position, newElement) {
        switch ((position || '').toLowerCase()) {
            case 'beforebegin':
                if (el.parentNode) el.parentNode.insertBefore(newElement, el);
                break;
            case 'afterbegin':
                el.insertBefore(newElement, el.firstChild);
                break;
            case 'beforeend':
                el.appendChild(newElement);
                break;
            case 'afterend':
                if (el.parentNode) {
                    if (el.nextSibling) el.parentNode.insertBefore(newElement, el.nextSibling);
                    else el.parentNode.appendChild(newElement);
                }
                break;
        }
        return newElement;
    };

    // insertAdjacentHTML — stub that creates a text node
    el.insertAdjacentHTML = function(position, html) {
        // Simple stub: create element from HTML text (limited support)
        var temp = createDomElement('span');
        temp._innerHTMLRaw = html;
        el.insertAdjacentElement(position, temp);
    };

    // dataset — simple object that maps to data-* attributes
    // Cannot use Proxy (may not be available), so provide basic get/set helpers
    el.dataset = {};
    el._syncDataset = function() {
        // Sync data-* attrs to dataset object
        if (!el._attrs) return;
        var keys = Object.keys(el._attrs);
        for (var di = 0; di < keys.length; di++) {
            if (keys[di].indexOf('data-') === 0) {
                var camel = keys[di].substring(5).replace(/-([a-z])/g, function(m, c) { return c.toUpperCase(); });
                el.dataset[camel] = el._attrs[keys[di]];
            }
        }
    };

    // getRootNode — walk up to the topmost parent (needed by Mermaid mindmap)
    el.getRootNode = function(opts) {
        var current = el;
        while (current.parentNode) current = current.parentNode;
        return current;
    };

    // isConnected — check if element is in a document tree
    Object.defineProperty(el, 'isConnected', {
        get: function() {
            var root = el.getRootNode();
            return root === document || root === document.documentElement || (root.nodeType === 9);
        },
        configurable: true
    });

    el.cloneNode = function(deep) {
        var clone = createDomElement(tagName, namespaceURI);
        clone.className = el.className;
        clone.id = el.id;
        var keys = Object.keys(el._attrs);
        for (var i = 0; i < keys.length; i++) {
            clone._attrs[keys[i]] = el._attrs[keys[i]];
        }
        // Copy style properties
        if (el.style && el.style._props) {
            var skeys = Object.keys(el.style._props);
            for (var s = 0; s < skeys.length; s++) {
                clone.style._props[skeys[s]] = el.style._props[skeys[s]];
                var camel = skeys[s].replace(/-([a-z])/g, function(m, c) { return c.toUpperCase(); });
                clone.style[camel] = el.style._props[skeys[s]];
            }
        }
        if (deep) {
            // Deep clone: recursively clone child nodes
            if (el.childNodes && el.childNodes.length > 0) {
                for (var j = 0; j < el.childNodes.length; j++) {
                    var child = el.childNodes[j];
                    if (child.cloneNode) {
                        clone.appendChild(child.cloneNode(true));
                    } else if (child.nodeType === 3) {
                        // Text node
                        clone.appendChild({ nodeType: 3, textContent: child.textContent, ownerDocument: el.ownerDocument });
                    }
                }
            } else if (el._innerHTMLRaw) {
                // No child nodes but has raw HTML — copy it
                clone._innerHTMLRaw = el._innerHTMLRaw;
            }
        } else {
            // Shallow clone: only copy _innerHTMLRaw if no children
            if (!el.childNodes || el.childNodes.length === 0) {
                clone._innerHTMLRaw = el._innerHTMLRaw || '';
            }
        }
        return clone;
    };

    el.querySelector = function(sel) { return _querySelector(el, sel); };
    el.querySelectorAll = function(sel) { return _querySelectorAll(el, sel); };
    el.getElementsByTagName = function(name) { return _querySelectorAll(el, name); };
    el.getElementsByClassName = function(name) { return _querySelectorAll(el, '.' + name); };
    el.matches = function(sel) { return _matchesSelector(el, sel); };
    el.closest = function(sel) {
        var current = el;
        while (current) {
            if (current.nodeType === 1 && _matchesSelector(current, sel)) return current;
            current = current.parentNode;
        }
        return null;
    };
    el.getBoundingClientRect = function() {
        var dims = _computeElementDims(el);
        return { x: dims.x, y: dims.y, width: dims.w, height: dims.h,
                 top: dims.y, right: dims.x + dims.w, bottom: dims.y + dims.h, left: dims.x };
    };
    el.getComputedTextLength = function() {
        return _estimateTextWidth(el);
    };
    el.getBBox = function() {
        var dims = _computeElementDims(el);
        return { x: dims.x, y: dims.y, width: dims.w, height: dims.h };
    };
    el.getTotalLength = function() { return 0; };
    el.getPointAtLength = function(len) { return { x: 0, y: 0 }; };

    // SVG-specific
    el.createSVGPoint = function() { return { x: 0, y: 0, matrixTransform: function() { return { x: 0, y: 0 }; } }; };
    el.getScreenCTM = function() { return { inverse: function() { return {}; } }; };

    el.focus = function() {};
    el.blur = function() {};
    el.click = function() {};
    el.hasChildNodes = function() { return el.childNodes && el.childNodes.length > 0; };
    el.normalize = function() {};
    el.remove = function() {
        if (el.parentNode && el.parentNode.removeChild) {
            el.parentNode.removeChild(el);
        }
    };

    // Layout dimension properties (used by Gantt charts and other diagram types)
    Object.defineProperty(el, 'offsetWidth', {
        get: function() { return _computeElementDims(el).w; },
        configurable: true
    });
    Object.defineProperty(el, 'offsetHeight', {
        get: function() { return _computeElementDims(el).h; },
        configurable: true
    });
    Object.defineProperty(el, 'clientWidth', {
        get: function() { return _computeElementDims(el).w; },
        configurable: true
    });
    Object.defineProperty(el, 'clientHeight', {
        get: function() { return _computeElementDims(el).h; },
        configurable: true
    });
    Object.defineProperty(el, 'scrollWidth', {
        get: function() { return _computeElementDims(el).w; },
        configurable: true
    });
    Object.defineProperty(el, 'scrollHeight', {
        get: function() { return _computeElementDims(el).h; },
        configurable: true
    });

    // Canvas 2D context stub (used by Mermaid mindmap for text measurement)
    el.getContext = function(type) {
        if (type === '2d') {
            var ctx = {
                _font: '16px sans-serif',
                measureText: function(text) {
                    // Use Java bridge for accurate measurement
                    var w, asc = 10, desc = 3;
                    if (typeof javaBridge !== 'undefined' && javaBridge.measureTextFull) {
                        try {
                            // Parse font string: "16px sans-serif" or "bold 14px arial"
                            var fontSize = 16;
                            var fontFamily = 'sans-serif';
                            var fontParts = (ctx._font || '').match(/([\d.]+)px\s+(.*)/);
                            if (fontParts) {
                                fontSize = parseFloat(fontParts[1]) || 16;
                                fontFamily = fontParts[2] || 'sans-serif';
                            }
                            var result = javaBridge.measureTextFull(text || '', fontFamily, fontSize);
                            var parts = ('' + result).split(',');
                            w = parseFloat(parts[0]) || 0;
                            asc = parseFloat(parts[1]) || 10;
                            desc = parseFloat(parts[2]) || 3;
                        } catch (e) {
                            w = (text ? text.length : 0) * 7;
                        }
                    } else {
                        w = (text ? text.length : 0) * 7;
                    }
                    return {
                        width: w,
                        actualBoundingBoxAscent: asc,
                        actualBoundingBoxDescent: desc,
                        fontBoundingBoxAscent: asc + 2,
                        fontBoundingBoxDescent: desc + 1
                    };
                },
                fillStyle: '#000',
                strokeStyle: '#000',
                lineWidth: 1,
                fillRect: function() {},
                clearRect: function() {},
                strokeRect: function() {},
                beginPath: function() {},
                closePath: function() {},
                moveTo: function() {},
                lineTo: function() {},
                arc: function() {},
                fill: function() {},
                stroke: function() {},
                fillText: function() {},
                strokeText: function() {},
                save: function() {},
                restore: function() {},
                translate: function() {},
                rotate: function() {},
                scale: function() {},
                setTransform: function() {},
                getTransform: function() { return { a:1,b:0,c:0,d:1,e:0,f:0 }; },
                createLinearGradient: function() { return { addColorStop: function() {} }; },
                createRadialGradient: function() { return { addColorStop: function() {} }; },
                drawImage: function() {},
                getImageData: function() { return { data: [] }; },
                putImageData: function() {}
            };
            Object.defineProperty(ctx, 'font', {
                get: function() { return ctx._font; },
                set: function(val) { ctx._font = val; },
                configurable: true, enumerable: true
            });
            return ctx;
        }
        return null;
    };

    return el;
}

// ── DOM constructor stubs (required by DOMPurify embedded in Mermaid) ───────
function Element() {}
Element.prototype = {
    nodeType: 1,
    setAttribute: function(k, v) {},
    getAttribute: function(k) { return null; },
    removeAttribute: function(k) {},
    hasAttribute: function(k) { return false; },
    setAttributeNS: function(ns, k, v) {},
    getAttributeNS: function(ns, k) { return null; },
    removeAttributeNS: function(ns, k) {},
    appendChild: function(c) { return c; },
    removeChild: function(c) { return c; },
    insertBefore: function(n, r) { return n; },
    replaceChild: function(n, o) { return o; },
    cloneNode: function(deep) { return createDomElement('div'); },
    contains: function(other) { return false; },
    hasChildNodes: function() { return false; },
    normalize: function() {},
    querySelector: function(sel) { return null; },
    querySelectorAll: function(sel) { return []; },
    getElementsByTagName: function(name) { return []; },
    getElementsByClassName: function(name) { return []; },
    matches: function(sel) { return false; },
    closest: function(sel) { return null; },
    addEventListener: function() {},
    removeEventListener: function() {},
    dispatchEvent: function() {},
    getBoundingClientRect: function() { return {x:0,y:0,width:0,height:0,top:0,right:0,bottom:0,left:0}; },
    childNodes: [],
    children: [],
    parentNode: null,
    firstChild: null,
    lastChild: null,
    nextSibling: null,
    previousSibling: null,
    textContent: '',
    innerHTML: '',
    outerHTML: '',
    style: {},
    className: '',
    id: '',
    tagName: 'DIV',
    nodeName: 'DIV',
    namespaceURI: 'http://www.w3.org/1999/xhtml'
};

function Node() {}
Node.prototype = Element.prototype;
Node.ELEMENT_NODE = 1;
Node.TEXT_NODE = 3;
Node.COMMENT_NODE = 8;
Node.DOCUMENT_NODE = 9;
Node.DOCUMENT_FRAGMENT_NODE = 11;

function DocumentFragment() {}
DocumentFragment.prototype = Element.prototype;

function HTMLTemplateElement() {}
HTMLTemplateElement.prototype = Element.prototype;
HTMLTemplateElement.prototype.content = { ownerDocument: null, childNodes: [], firstChild: null };

function HTMLFormElement() {}
HTMLFormElement.prototype = Element.prototype;

function HTMLElement() {}
HTMLElement.prototype = Element.prototype;

function SVGElement() {}
SVGElement.prototype = Element.prototype;

function Text() {}
Text.prototype = { nodeType: 3, textContent: '', cloneNode: function() { return { nodeType: 3, textContent: this.textContent }; } };

function Comment() {}
Comment.prototype = { nodeType: 8, textContent: '' };

function NodeFilter() {}
NodeFilter.SHOW_ALL = 0xFFFFFFFF;
NodeFilter.SHOW_ELEMENT = 1;
NodeFilter.SHOW_TEXT = 4;
NodeFilter.SHOW_COMMENT = 128;
NodeFilter.FILTER_ACCEPT = 1;
NodeFilter.FILTER_REJECT = 2;
NodeFilter.FILTER_SKIP = 3;

function NamedNodeMap() {}
NamedNodeMap.prototype = {
    length: 0,
    getNamedItem: function(name) { return null; },
    setNamedItem: function(item) {},
    removeNamedItem: function(name) {},
    item: function(index) { return null; }
};

// ── window ──────────────────────────────────────────────────────────────────
var window = EventTargetMixin({});
var self = window;
var globalThis = window;

window.window = window;
window.self = window;
window.top = window;
window.parent = window;

window.location = {
    href: 'about:blank',
    protocol: 'https:',
    host: 'localhost',
    hostname: 'localhost',
    pathname: '/',
    search: '',
    hash: '',
    origin: 'https://localhost'
};

window.navigator = { userAgent: 'GraalJS MermaidSpike/1.0', platform: 'Java', language: 'en' };
var navigator = window.navigator;

// DOM constructors on window (required by DOMPurify)
window.Element = Element;
window.Node = Node;
window.DocumentFragment = DocumentFragment;
window.HTMLTemplateElement = HTMLTemplateElement;
window.HTMLFormElement = HTMLFormElement;
window.HTMLElement = HTMLElement;
window.SVGElement = SVGElement;
window.Text = Text;
window.Comment = Comment;
window.NodeFilter = NodeFilter;
window.NamedNodeMap = NamedNodeMap;

window.screen = { width: 1920, height: 1080, availWidth: 1920, availHeight: 1080, colorDepth: 24 };

window.devicePixelRatio = 1;
window.innerWidth = 1920;
window.innerHeight = 1080;
window.outerWidth = 1920;
window.outerHeight = 1080;
window.pageXOffset = 0;
window.pageYOffset = 0;
window.scrollX = 0;
window.scrollY = 0;

window.getComputedStyle = function(el) {
    // Build defaults — use 'relative' position so Cytoscape accepts the container
    var defaults = {
        'display': 'block', 'visibility': 'visible', 'opacity': '1',
        'font-size': '16px', 'font-family': 'sans-serif', 'font-weight': '400', 'font-style': 'normal',
        'line-height': '1.2', 'color': 'rgb(0, 0, 0)', 'background-color': 'rgba(0, 0, 0, 0)',
        'fill': 'rgb(0, 0, 0)', 'stroke': 'none', 'stroke-width': '1',
        'width': '800px', 'height': '600px',
        'margin': '0px', 'margin-top': '0px', 'margin-right': '0px', 'margin-bottom': '0px', 'margin-left': '0px',
        'padding': '0px', 'padding-top': '0px', 'padding-right': '0px', 'padding-bottom': '0px', 'padding-left': '0px',
        'border': '0px none rgb(0, 0, 0)', 'border-width': '0px',
        'border-top-width': '0px', 'border-right-width': '0px', 'border-bottom-width': '0px', 'border-left-width': '0px',
        'position': 'relative', 'overflow': 'visible',
        'white-space': 'normal', 'text-align': 'start', 'text-decoration': 'none',
        'transform': 'none', 'transition': 'none',
        'box-sizing': 'content-box',
        'min-width': '0px', 'min-height': '0px',
        'max-width': 'none', 'max-height': 'none',
        'top': 'auto', 'right': 'auto', 'bottom': 'auto', 'left': 'auto',
        'float': 'none', 'clear': 'none',
        'vertical-align': 'baseline',
        'outline': 'none', 'cursor': 'auto',
        'pointer-events': 'auto',
        'user-select': 'auto',
        'clip': 'auto',
        'border-radius': '0px'
    };

    // Resolve a value: element's own style overrides defaults
    function resolve(prop) {
        // Check element's inline style first (kebab-case) via style object
        if (el && el.style && el.style._props && el.style._props[prop] !== undefined && el.style._props[prop] !== '') {
            return el.style._props[prop];
        }
        // Also check the raw style attribute string (set via setAttribute('style', ...))
        if (el && el._attrs && el._attrs['style']) {
            var styleStr = el._attrs['style'];
            var re = new RegExp('(?:^|;)\\s*' + prop.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&') + '\\s*:\\s*([^;]+)', 'i');
            var match = styleStr.match(re);
            if (match) return match[1].trim();
        }
        // Check element's attributes (width/height on SVG/canvas elements)
        if (el && el._attrs) {
            if (prop === 'width' && el._attrs['width']) return el._attrs['width'] + (String(el._attrs['width']).indexOf('px') < 0 ? 'px' : '');
            if (prop === 'height' && el._attrs['height']) return el._attrs['height'] + (String(el._attrs['height']).indexOf('px') < 0 ? 'px' : '');
        }
        return defaults[prop] || '';
    }

    // Build the result object with ALL default properties accessible directly
    // Both camelCase and kebab-case access must work (Cytoscape uses camelCase)
    var result = {
        getPropertyValue: function(prop) {
            // Convert camelCase to kebab-case
            var kebab = prop.replace(/([A-Z])/g, '-$1').toLowerCase();
            return resolve(kebab) || resolve(prop) || '';
        },
        setProperty: function() {},
        removeProperty: function() { return ''; },
        length: 0,
        item: function() { return ''; }
    };

    // Populate direct property access for all defaults (both kebab and camelCase)
    var keys = Object.keys(defaults);
    for (var i = 0; i < keys.length; i++) {
        var kebab = keys[i];
        var val = resolve(kebab);
        // kebab-case access
        result[kebab] = val;
        // camelCase access
        var camel = kebab.replace(/-([a-z])/g, function(m, c) { return c.toUpperCase(); });
        result[camel] = val;
    }

    return result;
};

window.matchMedia = function(query) {
    return {
        matches: false,
        media: query,
        addEventListener: function() {},
        removeEventListener: function() {},
        addListener: function() {},
        removeListener: function() {}
    };
};

window.getSelection = function() {
    return {
        removeAllRanges: function() {},
        addRange: function() {},
        getRangeAt: function() { return { startContainer: null, endContainer: null }; },
        rangeCount: 0
    };
};

// requestAnimationFrame: Do NOT invoke callback synchronously.
// Cytoscape starts an animation loop that calls requestAnimationFrame recursively.
// Executing callbacks synchronously would cause infinite recursion / stack overflow.
// Headless mode does not need animation — the layout runs synchronously in run().
var __rafId = 0;
var __rafQueue = [];
window.requestAnimationFrame = function(fn) {
    __rafId++;
    __rafQueue.push({id: __rafId, fn: fn});
    return __rafId;
};
window.cancelAnimationFrame = function(id) {
    __rafQueue = __rafQueue.filter(function(item) { return item.id !== id; });
};
// Flush up to N queued animation frames (used after layout completes)
window.__flushAnimationFrames = function(maxIterations) {
    var count = 0;
    while (__rafQueue.length > 0 && count < (maxIterations || 10)) {
        var item = __rafQueue.shift();
        try { item.fn(Date.now()); } catch(e) {}
        count++;
    }
};
window.setTimeout = function(fn, delay) { if (typeof fn === 'function') fn(); return 0; };
window.clearTimeout = function(id) {};
window.setInterval = function(fn, delay) { return 0; };
window.clearInterval = function(id) {};

// btoa / atob — real Base64 encoding/decoding (Mermaid 11 uses btoa as global)
var _b64chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
var btoa = function(str) {
    str = String(str);
    var out = '';
    for (var i = 0; i < str.length; i += 3) {
        var b1 = str.charCodeAt(i) & 0xFF;
        var b2 = i + 1 < str.length ? str.charCodeAt(i + 1) & 0xFF : 0;
        var b3 = i + 2 < str.length ? str.charCodeAt(i + 2) & 0xFF : 0;
        out += _b64chars.charAt(b1 >> 2);
        out += _b64chars.charAt(((b1 & 3) << 4) | (b2 >> 4));
        out += i + 1 < str.length ? _b64chars.charAt(((b2 & 15) << 2) | (b3 >> 6)) : '=';
        out += i + 2 < str.length ? _b64chars.charAt(b3 & 63) : '=';
    }
    return out;
};
var atob = function(str) {
    str = String(str).replace(/=+$/, '');
    var out = '';
    for (var i = 0; i < str.length; i += 4) {
        var b1 = _b64chars.indexOf(str.charAt(i));
        var b2 = _b64chars.indexOf(str.charAt(i + 1));
        var b3 = _b64chars.indexOf(str.charAt(i + 2));
        var b4 = _b64chars.indexOf(str.charAt(i + 3));
        out += String.fromCharCode((b1 << 2) | (b2 >> 4));
        if (b3 >= 0) out += String.fromCharCode(((b2 & 15) << 4) | (b3 >> 2));
        if (b4 >= 0) out += String.fromCharCode(((b3 & 3) << 6) | b4);
    }
    return out;
};
window.btoa = btoa;
window.atob = atob;

// Make timing functions available globally
var setTimeout = window.setTimeout;
var clearTimeout = window.clearTimeout;
var setInterval = window.setInterval;
var clearInterval = window.clearInterval;
var requestAnimationFrame = window.requestAnimationFrame;
var cancelAnimationFrame = window.cancelAnimationFrame;

// ── DOMParser ───────────────────────────────────────────────────────────────
function DOMParser() {}
DOMParser.prototype.parseFromString = function(str, type) {
    var doc = {
        documentElement: createDomElement('html'),
        querySelector: function(sel) { return null; },
        querySelectorAll: function(sel) { return []; },
        getElementsByTagName: function(name) { return []; },
        getElementById: function(id) { return null; },
        createElementNS: function(ns, name) { return createDomElement(name, ns); },
        createElement: function(name) { return createDomElement(name); },
        createTextNode: function(text) { return { nodeType: 3, textContent: text }; }
    };
    return doc;
};
window.DOMParser = DOMParser;

// ── XMLSerializer ───────────────────────────────────────────────────────────
function XMLSerializer() {}
XMLSerializer.prototype.serializeToString = function(node) {
    if (!node) return '';
    if (node.nodeType === 3) return _escapeXmlText(node.textContent || '');
    // For SVG root, use null parent namespace so xmlns is always emitted
    var parentNs = (node.tagName && node.tagName.toLowerCase() === 'svg') ? null : XHTML_NS;
    return _serializeNode(node, parentNs);
};
window.XMLSerializer = XMLSerializer;

// ── document ────────────────────────────────────────────────────────────────
var document = EventTargetMixin({});
document.nodeType = 9;
document.documentElement = createDomElement('html');
document.documentElement.namespaceURI = 'http://www.w3.org/1999/xhtml';
document.head = createDomElement('head');
document.body = createDomElement('body');
document.documentElement.appendChild(document.head);
document.documentElement.appendChild(document.body);

// Set ownerDocument on the root elements
document.documentElement.ownerDocument = document;
document.head.ownerDocument = document;
document.body.ownerDocument = document;

// Cytoscape.js accesses getComputedStyle via node.ownerDocument.defaultView
document.defaultView = window;


document.createElement = function(name) {
    var el = createDomElement(name);
    el.ownerDocument = document;
    return el;
};
document.createElementNS = function(ns, name) {
    var el = createDomElement(name, ns);
    el.ownerDocument = document;
    return el;
};
document.createTextNode = function(text) {
    return { nodeType: 3, textContent: text, ownerDocument: document, cloneNode: function() { return document.createTextNode(text); } };
};
document.createComment = function(data) { return { nodeType: 8, textContent: data, ownerDocument: document }; };
document.createDocumentFragment = function() {
    var frag = createDomElement('#fragment');
    frag.nodeType = 11;
    frag.ownerDocument = document;
    return frag;
};
document.createRange = function() {
    return {
        setStart: function() {},
        setEnd: function() {},
        commonAncestorContainer: document.body,
        createContextualFragment: function(html) {
            var frag = document.createDocumentFragment();
            var div = document.createElement('div');
            div.innerHTML = html;
            frag.appendChild(div);
            return frag;
        }
    };
};
document.createTreeWalker = function() {
    return { nextNode: function() { return null; } };
};

document.querySelector = function(sel) {
    if (sel === 'body') return document.body;
    if (sel === 'head') return document.head;
    if (sel === 'html') return document.documentElement;
    return _querySelector(document.documentElement, sel);
};
document.querySelectorAll = function(sel) { return _querySelectorAll(document.documentElement, sel); };
document.getElementById = function(id) { return _querySelector(document.documentElement, '#' + id); };
document.getElementsByTagName = function(name) {
    if (name === 'body') return [document.body];
    if (name === 'head') return [document.head];
    if (name === 'html') return [document.documentElement];
    return _querySelectorAll(document.documentElement, name);
};
document.getElementsByClassName = function(name) { return _querySelectorAll(document.documentElement, '.' + name); };

window.document = document;

// ── console ─────────────────────────────────────────────────────────────────
var console = {
    log:   function() { javaBridge.log(Array.prototype.slice.call(arguments).join(' ')); },
    warn:  function() { javaBridge.log('WARN: ' + Array.prototype.slice.call(arguments).join(' ')); },
    error: function() { javaBridge.log('ERROR: ' + Array.prototype.slice.call(arguments).join(' ')); },
    info:  function() { javaBridge.log('INFO: ' + Array.prototype.slice.call(arguments).join(' ')); },
    debug: function() {},
    trace: function() {},
    dir:   function() {},
    table: function() {},
    group: function() {},
    groupEnd: function() {},
    time:  function() {},
    timeEnd: function() {},
    assert: function(cond, msg) { if (!cond) javaBridge.log('ASSERT FAILED: ' + (msg || '')); }
};
window.console = console;

// ── misc browser APIs ───────────────────────────────────────────────────────
var URL = {
    createObjectURL: function(blob) { return 'blob:graaljs/' + Math.random(); },
    revokeObjectURL: function(url) {}
};
window.URL = URL;

var Blob = function(parts, options) { this.parts = parts; this.type = (options && options.type) || ''; };
window.Blob = Blob;

function XMLHttpRequest() { this._headers = {}; }
XMLHttpRequest.prototype.open = function() {};
XMLHttpRequest.prototype.send = function() {};
XMLHttpRequest.prototype.setRequestHeader = function(k, v) { this._headers[k] = v; };
XMLHttpRequest.prototype.getResponseHeader = function(k) { return null; };
XMLHttpRequest.prototype.getAllResponseHeaders = function() { return ''; };
window.XMLHttpRequest = XMLHttpRequest;

var fetch = function(url, opts) {
    return { then: function(fn) { return { catch: function(fn2) { return { finally: function(fn3) {} }; } }; } };
};
window.fetch = fetch;

// ── CustomEvent / Event ─────────────────────────────────────────────────────
function Event(type, opts) { this.type = type; this.bubbles = (opts && opts.bubbles) || false; }
function CustomEvent(type, opts) { this.type = type; this.detail = (opts && opts.detail) || null; this.bubbles = (opts && opts.bubbles) || false; }
window.Event = Event;
window.CustomEvent = CustomEvent;

// ── MutationObserver / ResizeObserver stubs ──────────────────────────────────
function MutationObserver(callback) { this._callback = callback; }
MutationObserver.prototype.observe = function() {};
MutationObserver.prototype.disconnect = function() {};
MutationObserver.prototype.takeRecords = function() { return []; };
window.MutationObserver = MutationObserver;

function ResizeObserver(callback) { this._callback = callback; }
ResizeObserver.prototype.observe = function() {};
ResizeObserver.prototype.disconnect = function() {};
ResizeObserver.prototype.unobserve = function() {};
window.ResizeObserver = ResizeObserver;

function IntersectionObserver(callback) { this._callback = callback; }
IntersectionObserver.prototype.observe = function() {};
IntersectionObserver.prototype.disconnect = function() {};
IntersectionObserver.prototype.unobserve = function() {};
window.IntersectionObserver = IntersectionObserver;

// ── performance ─────────────────────────────────────────────────────────────
window.performance = {
    now: function() { return Date.now(); },
    mark: function() {},
    measure: function() {},
    getEntriesByName: function() { return []; },
    getEntriesByType: function() { return []; }
};

// ── CSS / StyleSheet stubs ──────────────────────────────────────────────────
window.CSSStyleSheet = function() { this.cssRules = []; };
window.CSSStyleSheet.prototype.insertRule = function(rule, idx) { this.cssRules.splice(idx || 0, 0, rule); return idx || 0; };
window.CSSStyleSheet.prototype.deleteRule = function(idx) { this.cssRules.splice(idx, 1); };

// ── Standard JS built-ins on window (some libs access via window.X) ─────────
window.Error = Error;
window.TypeError = TypeError;
window.RangeError = RangeError;
window.SyntaxError = SyntaxError;
window.ReferenceError = ReferenceError;
window.EvalError = typeof EvalError !== 'undefined' ? EvalError : Error;
window.URIError = typeof URIError !== 'undefined' ? URIError : Error;
window.Object = Object;
window.Array = Array;
window.String = String;
window.Number = Number;
window.Boolean = Boolean;
window.Function = Function;
window.RegExp = RegExp;
window.Date = Date;
window.Math = Math;
window.JSON = JSON;
window.Map = typeof Map !== 'undefined' ? Map : function() {};
window.Set = typeof Set !== 'undefined' ? Set : function() {};
window.WeakMap = typeof WeakMap !== 'undefined' ? WeakMap : function() {};
window.WeakSet = typeof WeakSet !== 'undefined' ? WeakSet : function() {};
window.Promise = typeof Promise !== 'undefined' ? Promise : function(fn) { fn(function(){}, function(){}); };
window.Symbol = typeof Symbol !== 'undefined' ? Symbol : function(desc) { return desc; };
window.Proxy = typeof Proxy !== 'undefined' ? Proxy : undefined;
window.Reflect = typeof Reflect !== 'undefined' ? Reflect : undefined;
window.ArrayBuffer = typeof ArrayBuffer !== 'undefined' ? ArrayBuffer : function() {};
window.Uint8Array = typeof Uint8Array !== 'undefined' ? Uint8Array : function() {};
window.Int32Array = typeof Int32Array !== 'undefined' ? Int32Array : function() {};
window.Float64Array = typeof Float64Array !== 'undefined' ? Float64Array : function() {};
window.DataView = typeof DataView !== 'undefined' ? DataView : function() {};
// TextEncoder/TextDecoder — must also be declared as top-level vars because
// Mermaid code references them directly, not via window.
if (typeof TextEncoder === 'undefined') {
    var TextEncoder = function TextEncoder() {
        this.encoding = 'utf-8';
        this.encode = function(str) {
            if (typeof str !== 'string') str = String(str);
            var arr = [];
            for (var i = 0; i < str.length; i++) {
                var c = str.charCodeAt(i);
                if (c < 0x80) { arr.push(c); }
                else if (c < 0x800) { arr.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f)); }
                else { arr.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f)); }
            }
            return new Uint8Array(arr);
        };
    };
}
window.TextEncoder = TextEncoder;

if (typeof TextDecoder === 'undefined') {
    var TextDecoder = function TextDecoder() {
        this.encoding = 'utf-8';
        this.decode = function(buf) {
            if (!buf || !buf.length) return '';
            var s = '';
            for (var i = 0; i < buf.length; i++) s += String.fromCharCode(buf[i]);
            return s;
        };
    };
}
window.TextDecoder = TextDecoder;
window.parseInt = parseInt;
window.parseFloat = parseFloat;
window.isNaN = isNaN;
window.isFinite = isFinite;
window.encodeURIComponent = encodeURIComponent;
window.decodeURIComponent = decodeURIComponent;
window.encodeURI = encodeURI;
window.decodeURI = decodeURI;

// ── APIs required by Mermaid 11+ ─────────────────────────────────────────────

// Object.hasOwn — ES2022 polyfill (not available in GraalJS / Java 8)
if (!Object.hasOwn) {
    Object.hasOwn = function(obj, prop) {
        return Object.prototype.hasOwnProperty.call(obj, prop);
    };
}

// Array.prototype.at — ES2022 polyfill
if (!Array.prototype.at) {
    Array.prototype.at = function(index) {
        index = Math.trunc(index) || 0;
        if (index < 0) index += this.length;
        if (index < 0 || index >= this.length) return undefined;
        return this[index];
    };
}

// String.prototype.at — ES2022 polyfill
if (!String.prototype.at) {
    String.prototype.at = function(index) {
        index = Math.trunc(index) || 0;
        if (index < 0) index += this.length;
        if (index < 0 || index >= this.length) return undefined;
        return this[index];
    };
}

// String.prototype.replaceAll — ES2021 polyfill
if (!String.prototype.replaceAll) {
    String.prototype.replaceAll = function(search, replacement) {
        if (search instanceof RegExp) {
            if (!search.global) throw new TypeError('String.prototype.replaceAll called with a non-global RegExp');
            return this.replace(search, replacement);
        }
        return this.split(search).join(replacement);
    };
}

// structuredClone — deep-copy via JSON round-trip (sufficient for plain objects)
if (typeof structuredClone === 'undefined') {
    var structuredClone = function(obj) {
        if (obj === undefined || obj === null) return obj;
        return JSON.parse(JSON.stringify(obj));
    };
    window.structuredClone = structuredClone;
}

// queueMicrotask — execute immediately in our synchronous environment
if (typeof queueMicrotask === 'undefined') {
    var queueMicrotask = function(fn) { fn(); };
    window.queueMicrotask = queueMicrotask;
}

// crypto.randomUUID / crypto.getRandomValues
if (typeof crypto === 'undefined') {
    var crypto = {
        getRandomValues: function(arr) {
            for (var i = 0; i < arr.length; i++) {
                arr[i] = Math.floor(Math.random() * 256);
            }
            return arr;
        },
        randomUUID: function() {
            // v4 UUID
            var bytes = new Uint8Array(16);
            crypto.getRandomValues(bytes);
            bytes[6] = (bytes[6] & 0x0f) | 0x40;
            bytes[8] = (bytes[8] & 0x3f) | 0x80;
            var hex = [];
            for (var i = 0; i < 16; i++) {
                var h = bytes[i].toString(16);
                hex.push(h.length < 2 ? '0' + h : h);
            }
            return hex[0]+hex[1]+hex[2]+hex[3]+'-'+hex[4]+hex[5]+'-'+hex[6]+hex[7]+'-'+hex[8]+hex[9]+'-'+hex[10]+hex[11]+hex[12]+hex[13]+hex[14]+hex[15];
        }
    };
    window.crypto = crypto;
}

// AbortController / AbortSignal stubs
function AbortController() {
    this.signal = { aborted: false, addEventListener: function() {}, removeEventListener: function() {} };
}
AbortController.prototype.abort = function() { this.signal.aborted = true; };
window.AbortController = AbortController;

// WeakRef stub (used by some Mermaid internals)
if (typeof WeakRef === 'undefined') {
    var WeakRef = function(target) { this._target = target; };
    WeakRef.prototype.deref = function() { return this._target; };
    window.WeakRef = WeakRef;
}

// FinalizationRegistry stub
if (typeof FinalizationRegistry === 'undefined') {
    var FinalizationRegistry = function(callback) {};
    FinalizationRegistry.prototype.register = function() {};
    FinalizationRegistry.prototype.unregister = function() {};
    window.FinalizationRegistry = FinalizationRegistry;
}

// document.createNodeIterator (used by DOMPurify in Mermaid 11)
if (!document.createNodeIterator) {
    document.createNodeIterator = function(root, whatToShow, filter) {
        var nodes = [];
        var idx = -1;
        function collect(node) {
            if (!node) return;
            var dominated = false;
            if (whatToShow) {
                if ((whatToShow & 1) && node.nodeType === 1) dominated = true;
                if ((whatToShow & 4) && node.nodeType === 3) dominated = true;
                if ((whatToShow & 128) && node.nodeType === 8) dominated = true;
                if ((whatToShow & 64) && node.nodeType === 7) dominated = true;
                if ((whatToShow & 8) && node.nodeType === 4) dominated = true;
            } else {
                dominated = true;
            }
            if (dominated) nodes.push(node);
            var children = node.childNodes || [];
            for (var i = 0; i < children.length; i++) {
                collect(children[i]);
            }
        }
        collect(root);
        return {
            nextNode: function() {
                idx++;
                return idx < nodes.length ? nodes[idx] : null;
            },
            previousNode: function() {
                idx--;
                return idx >= 0 ? nodes[idx] : null;
            }
        };
    };
}

// document.implementation (used by DOMPurify)
if (!document.implementation) {
    document.implementation = {
        createHTMLDocument: function(title) {
            var doc = {
                nodeType: 9,
                documentElement: createDomElement('html'),
                body: createDomElement('body'),
                head: createDomElement('head'),
                createElement: function(name) { var e = createDomElement(name); e.ownerDocument = doc; return e; },
                createElementNS: function(ns, name) { var e = createDomElement(name, ns); e.ownerDocument = doc; return e; },
                createTextNode: function(text) { return { nodeType: 3, textContent: text, ownerDocument: doc }; },
                createComment: function(data) { return { nodeType: 8, textContent: data, ownerDocument: doc }; },
                createDocumentFragment: function() { var f = createDomElement('#fragment'); f.nodeType = 11; f.ownerDocument = doc; return f; },
                createNodeIterator: document.createNodeIterator,
                querySelector: function(sel) { return _querySelector(doc.documentElement, sel); },
                querySelectorAll: function(sel) { return _querySelectorAll(doc.documentElement, sel); },
                getElementById: function(id) { return _querySelector(doc.documentElement, '#' + id); },
                getElementsByTagName: function(name) { return _querySelectorAll(doc.documentElement, name); },
                importNode: function(node, deep) { return node.cloneNode ? node.cloneNode(deep) : node; }
            };
            doc.documentElement.appendChild(doc.head);
            doc.documentElement.appendChild(doc.body);
            doc.documentElement.ownerDocument = doc;
            doc.body.ownerDocument = doc;
            doc.head.ownerDocument = doc;
            if (title) { var t = doc.createElement('title'); t.textContent = title; doc.head.appendChild(t); }
            return doc;
        }
    };
}

// document.importNode
if (!document.importNode) {
    document.importNode = function(node, deep) {
        return node.cloneNode ? node.cloneNode(deep) : node;
    };
}

// Signal that the shim loaded successfully
'browser-shim.js loaded';

