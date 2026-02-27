function(mode, val) {
  var opts = this.options;
  for (var i = 0; i < opts.length; i++) {
    if ((mode === 'value' && opts[i].value === val)
      || (mode === 'label' && opts[i].text === val)
      || (mode === 'index' && i === parseInt(val))) {
      this.selectedIndex = i;
      break;
    }
  }
  this.dispatchEvent(new Event('change', {bubbles: true}));
}
