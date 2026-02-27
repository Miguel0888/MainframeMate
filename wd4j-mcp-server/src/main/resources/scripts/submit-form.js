function() {
  this.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', code:'Enter', bubbles:true}));
  this.dispatchEvent(new KeyboardEvent('keypress', {key:'Enter', code:'Enter', bubbles:true}));
  this.dispatchEvent(new KeyboardEvent('keyup', {key:'Enter', code:'Enter', bubbles:true}));
  var f = this.closest('form');
  if (f) f.submit();
}
