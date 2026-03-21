/*
 * browser-shim.js — Minimal browser-like environment for running Mermaid inside GraalJS.
 *
 * This shim provides enough of the window/document/navigator API surface
 * so that mermaid.min.js (UMD build, v9.x) can load and initialize.
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
            // Don't double-emit xmlns if we already added it above
            if (key === 'xmlns' && node._attrs[key] === ns && ns !== parentNs) continue;
            attrs += ' ' + key + '="' + _escapeXmlAttr(node._attrs[keys[i]]) + '"';
        }
    }

    // Serialize style properties inline
    if (node.style && node.style._props) {
        var styleKeys = Object.keys(node.style._props);
        if (styleKeys.length > 0) {
            var styleStr = '';
            for (var s = 0; s < styleKeys.length; s++) {
                if (styleStr) styleStr += '; ';
                styleStr += styleKeys[s] + ': ' + node.style._props[styleKeys[s]];
            }
            if (!node._attrs || !node._attrs['style']) {
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
    // Get text content from this element and immediate text-node children
    var text = '';
    if (el.childNodes && el.childNodes.length > 0) {
        for (var i = 0; i < el.childNodes.length; i++) {
            var child = el.childNodes[i];
            if (child.nodeType === 3) text += child.textContent || '';
        }
    }
    if (!text && el._textContent) text = el._textContent;
    if (!text) return 0;
    // Average character width ~8px at 16px font, add padding
    return text.length * 8 + 16;
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
        // Estimate dimensions based on text content and children
        var textLen = _estimateTextWidth(el);
        var w = Math.max(textLen, 20);
        var h = textLen > 0 ? 24 : 20;
        // For SVG containers, aggregate children
        if (el.childNodes && el.childNodes.length > 0 && textLen === 0) {
            var maxW = 0, maxH = 0;
            for (var i = 0; i < el.childNodes.length; i++) {
                var child = el.childNodes[i];
                if (child.getBoundingClientRect) {
                    var cr = child.getBoundingClientRect();
                    if (cr.width > maxW) maxW = cr.width;
                    maxH += cr.height;
                }
            }
            if (maxW > 0) w = maxW;
            if (maxH > 0) h = maxH;
        }
        return { x: 0, y: 0, width: w, height: h, top: 0, right: w, bottom: h, left: 0 };
    };
    el.getComputedTextLength = function() {
        return _estimateTextWidth(el);
    };
    el.getBBox = function() {
        var textLen = _estimateTextWidth(el);
        var w = Math.max(textLen, 20);
        var h = textLen > 0 ? 24 : 20;
        // For group elements, aggregate child bboxes
        if (el.childNodes && el.childNodes.length > 0 && textLen === 0) {
            var maxW = 0, totalH = 0;
            for (var i = 0; i < el.childNodes.length; i++) {
                var child = el.childNodes[i];
                if (child.getBBox) {
                    var cb = child.getBBox();
                    if (cb.width > maxW) maxW = cb.width;
                    totalH += cb.height;
                }
            }
            if (maxW > 0) w = maxW;
            if (totalH > 0) h = totalH;
        }
        return { x: 0, y: 0, width: w, height: h };
    };
    el.getTotalLength = function() { return 0; };
    el.getPointAtLength = function(len) { return { x: 0, y: 0 }; };

    // SVG-specific
    el.createSVGPoint = function() { return { x: 0, y: 0, matrixTransform: function() { return { x: 0, y: 0 }; } }; };
    el.getScreenCTM = function() { return { inverse: function() { return {}; } }; };

    el.focus = function() {};
    el.blur = function() {};
    el.click = function() {};

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
    return {
        getPropertyValue: function(prop) { return ''; },
        display: 'block',
        visibility: 'visible',
        opacity: '1',
        fontSize: '16px',
        fontFamily: 'sans-serif'
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
window.atob = function(str) { return ''; };
window.btoa = function(str) { return ''; };

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
window.TextEncoder = typeof TextEncoder !== 'undefined' ? TextEncoder : function() { this.encode = function(s) { return []; }; };
window.TextDecoder = typeof TextDecoder !== 'undefined' ? TextDecoder : function() { this.decode = function(b) { return ''; }; };
window.parseInt = parseInt;
window.parseFloat = parseFloat;
window.isNaN = isNaN;
window.isFinite = isFinite;
window.encodeURIComponent = encodeURIComponent;
window.decodeURIComponent = decodeURIComponent;
window.encodeURI = encodeURI;
window.decodeURI = decodeURI;

// Signal that the shim loaded successfully
'browser-shim.js loaded';

