function(text, clear) {
  this.focus();
  if (clear) { this.value = ''; }
  this.value += text;
  this.dispatchEvent(new Event('input', {bubbles: true}));
  this.dispatchEvent(new Event('change', {bubbles: true}));
}
