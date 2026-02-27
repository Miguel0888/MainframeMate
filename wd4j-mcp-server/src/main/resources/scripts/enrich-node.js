function() {
  var tag = this.tagName.toLowerCase();
  var al = this.getAttribute('aria-label') || '';
  var ph = this.getAttribute('placeholder') || '';
  var tt = this.getAttribute('title') || '';
  var tp = this.getAttribute('type') || '';
  var nm = this.getAttribute('name') || '';
  var hr = '';
  try {
    var rawHr = this.getAttribute('href') || '';
    if (tag === 'a' && this.href) { hr = this.href; }
    else if (rawHr && rawHr.length > 0) {
      try { hr = new URL(rawHr, window.location.href).href; } catch(ue) { hr = rawHr; }
    }
  } catch(e) {}
  var vl = (this.value || '').substring(0, 20);
  var tx = (this.innerText || this.textContent || '').trim().substring(0, 50).replace(/\n/g, ' ');
  var d = tp ? tag + '[' + tp + ']' : tag;
  if (nm) d += '[name=' + nm + ']';
  if (al) d += ' "' + al + '"';
  else if (ph) d += ' "' + ph + '"';
  else if (tt) d += ' "' + tt + '"';
  else if (tx.length > 0) d += ' "' + tx.substring(0, 40) + '"';
  if (hr && hr.indexOf('javascript:') < 0) d += ' ->' + (hr.length > 200 ? hr.substring(0, 200) : hr);
  if (vl && (tag === 'input' || tag === 'textarea')) d += ' val="' + vl + '"';
  return d;
}
