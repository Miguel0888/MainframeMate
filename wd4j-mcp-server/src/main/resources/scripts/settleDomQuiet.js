(function () {
    return new Promise(function (resolve) {
        var timer;
        var obs = new MutationObserver(function () {
            clearTimeout(timer);
            timer = setTimeout(function () {
                obs.disconnect();
                resolve('quiet');
            }, 500);
        });
        obs.observe(document.body || document.documentElement, {
            childList: true,
            subtree: true,
            attributes: true
        });
        // Hard timeout so we never hang forever
        timer = setTimeout(function () {
            obs.disconnect();
            resolve('timeout');
        }, 5000);
    });
})()
