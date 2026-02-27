(function() {
  var selectors = %SELECTORS_JSON%;
  for (var i = 0; i < selectors.length; i++) {
    try {
      var el = document.querySelector(selectors[i]);
      if (el && el.offsetParent !== null) {
        el.click();
        return 'clicked:' + selectors[i];
      }
    } catch(e) {}
  }
  var buttons = document.querySelectorAll('button, a[role=button], [class*=btn]');
  for (var j = 0; j < buttons.length && j < 50; j++) {
    var txt = (buttons[j].innerText || '').toLowerCase().trim();
    if ((txt.indexOf('accept') >= 0 || txt.indexOf('akzeptier') >= 0
        || txt.indexOf('agree') >= 0 || txt.indexOf('alle annehmen') >= 0
        || txt.indexOf('zustimmen') >= 0)
        && buttons[j].offsetParent !== null) {
      buttons[j].click();
      return 'clicked-text:' + txt;
    }
  }
  return 'none';
})();
