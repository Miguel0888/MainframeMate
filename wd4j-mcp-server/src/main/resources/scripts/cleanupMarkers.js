(function () {
    var els = document.querySelectorAll('[data-mm-menu-id]');
    for (var i = 0; i < els.length; i++) {
        els[i].removeAttribute('data-mm-menu-id');
    }
})()
