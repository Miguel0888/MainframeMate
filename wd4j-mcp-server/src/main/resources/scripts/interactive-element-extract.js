(function() {
  var results = [];
  var selector = 'input:not([type=hidden]):not([type=checkbox]):not([type=radio]),' +
                 'textarea, button, [type=submit], select';
  var els = document.querySelectorAll(selector);
  for (var i = 0; i < els.length && results.length < 20; i++) {
    var el = els[i];
    if (el.offsetParent === null && el.style.display !== 'contents') continue;
    var tag = el.tagName.toLowerCase();
    var type = (el.type || '').toLowerCase();
    var name = el.name || '';
    var placeholder = el.placeholder || '';
    var ariaLabel = el.getAttribute('aria-label') || '';
    var label = '';
    if (el.id) {
      var lbl = document.querySelector('label[for="' + el.id + '"]');
      if (lbl) label = lbl.innerText || '';
    }
    if (!label && el.closest('label')) label = el.closest('label').innerText || '';
    var text = (tag === 'button' || type === 'submit') ? (el.innerText || el.value || '') : '';
    var elType = tag;
    if (tag === 'input') elType = type || 'text';
    results.push({
      idx: i,
      tag: tag,
      elType: elType,
      name: name.substring(0, 50),
      placeholder: placeholder.substring(0, 80),
      ariaLabel: ariaLabel.substring(0, 80),
      label: (label || '').trim().substring(0, 80),
      text: (text || '').trim().substring(0, 80),
      value: (el.value || '').substring(0, 30)
    });
  }
  return JSON.stringify(results);
})();
