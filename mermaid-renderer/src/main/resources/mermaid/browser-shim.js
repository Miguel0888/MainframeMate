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
    // Average character width ~8px at 16px font, add padding
    return text.length * 8 + 16;
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
    // Fallback: innerHTML raw (strip HTML tags to get text only)
    if (!text && el._innerHTMLRaw) {
        text = el._innerHTMLRaw.replace(/<[^>]*>/g, '');
    }
    return text;
}

// ── Dimension computation for getBBox / getBoundingClientRect ────────────────
function _computeElementDims(el) {
    var tag = (el.tagName || '').toLowerCase();
    var attrs = el._attrs || {};

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

    // 5) line — x1,y1,x2,y2
    if (tag === 'line') {
        var x1 = num('x1'), y1 = num('y1'), x2 = num('x2'), y2 = num('y2');
        if (!isNaN(x1) && !isNaN(y1) && !isNaN(x2) && !isNaN(y2)) {
            return { x: Math.min(x1, x2), y: Math.min(y1, y2),
                     w: Math.abs(x2 - x1) || 1, h: Math.abs(y2 - y1) || 1 };
        }
    }

    // 6) text — estimate from content
    if (tag === 'text' || tag === 'tspan') {
        var tw = _estimateTextWidth(el);
        if (tw > 0) {
            var tx = num('x'), ty = num('y');
            return { x: isNaN(tx) ? 0 : tx - tw / 2, y: isNaN(ty) ? -12 : ty - 12, w: tw, h: 24 };
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
            return { x: isNaN(fx) ? 0 : fx, y: isNaN(fy) ? 0 : fy, w: foText, h: 24 };
        }
    }

    // 7b) HTML elements (div, span, p, label, etc.) — used inside foreignObject
    if (tag === 'div' || tag === 'span' || tag === 'p' || tag === 'label' || tag === 'b' || tag === 'i') {
        var htw = _estimateTextWidth(el);
        if (htw > 0) {
            return { x: 0, y: 0, w: htw, h: 24 };
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

    // Fallback: text-based estimate
    var textLen = _estimateTextWidth(el);
    var w = Math.max(textLen, 20);
    var h = textLen > 0 ? 24 : 20;
    return { x: 0, y: 0, w: w, h: h };
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

    el.appendChild = function(child) {
        if (child.parentNode && child.parentNode.removeChild) {
            child.parentNode.removeChild(child);
        }
        el.childNodes.push(child);
        if (child.nodeType === 1) el.children.push(child);
        child.parentNode = el;
        el.firstChild = el.childNodes[0];
        el.lastChild = el.childNodes[el.childNodes.length - 1];
        return child;
    };
    el.removeChild = function(child) {
        var idx = el.childNodes.indexOf(child);
        if (idx >= 0) el.childNodes.splice(idx, 1);
        var cidx = el.children.indexOf(child);
        if (cidx >= 0) el.children.splice(cidx, 1);
        child.parentNode = null;
        el.firstChild = el.childNodes[0] || null;
        el.lastChild = el.childNodes[el.childNodes.length - 1] || null;
        return child;
    };
    el.insertBefore = function(newNode, refNode) {
        if (!refNode) return el.appendChild(newNode);
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
        el.firstChild = el.childNodes[0];
        el.lastChild = el.childNodes[el.childNodes.length - 1];
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
        clone.innerHTML = el.innerHTML;
        clone.textContent = el.textContent;
        clone.className = el.className;
        clone.id = el.id;
        var keys = Object.keys(el._attrs);
        for (var i = 0; i < keys.length; i++) {
            clone._attrs[keys[i]] = el._attrs[keys[i]];
        }
        if (deep) {
            for (var j = 0; j < el.childNodes.length; j++) {
                if (el.childNodes[j].cloneNode) {
                    clone.appendChild(el.childNodes[j].cloneNode(true));
                }
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
            return {
                measureText: function(text) {
                    // Approximate text measurement: ~7px per character at default font
                    var w = (text ? text.length : 0) * 7;
                    return {
                        width: w,
                        actualBoundingBoxAscent: 10,
                        actualBoundingBoxDescent: 3,
                        fontBoundingBoxAscent: 12,
                        fontBoundingBoxDescent: 4
                    };
                },
                font: '16px sans-serif',
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
    var defaults = {
        display: 'block', visibility: 'visible', opacity: '1',
        fontSize: '16px', fontFamily: 'sans-serif', fontWeight: '400', fontStyle: 'normal',
        lineHeight: '1.2', color: 'rgb(0, 0, 0)', backgroundColor: 'rgba(0, 0, 0, 0)',
        fill: 'rgb(0, 0, 0)', stroke: 'none', strokeWidth: '1',
        width: '100px', height: '20px', margin: '0px', padding: '0px',
        border: '0px none rgb(0, 0, 0)', borderWidth: '0px',
        position: 'static', overflow: 'visible',
        whiteSpace: 'normal', textAlign: 'start', textDecoration: 'none',
        transform: 'none', transition: 'none',
        boxSizing: 'content-box'
    };
    return {
        getPropertyValue: function(prop) {
            // Check element's own style first
            if (el && el.style && el.style._props && el.style._props[prop]) {
                return el.style._props[prop];
            }
            // Convert camelCase prop to kebab-case for lookup
            var kebab = prop.replace(/([A-Z])/g, '-$1').toLowerCase();
            return defaults[prop] || defaults[kebab] || '';
        },
        display: defaults.display,
        visibility: defaults.visibility,
        opacity: defaults.opacity,
        fontSize: defaults.fontSize,
        fontFamily: defaults.fontFamily,
        color: defaults.color,
        backgroundColor: defaults.backgroundColor
    };
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

window.requestAnimationFrame = function(fn) { fn(Date.now()); return 0; };
window.cancelAnimationFrame = function(id) {};
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

