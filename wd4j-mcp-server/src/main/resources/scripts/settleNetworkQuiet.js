(function () {
    return new Promise(function (resolve) {
        var timer;
        function reset() {
            clearTimeout(timer);
            timer = setTimeout(function () { resolve('quiet'); }, 500);
        }
        try {
            var obs = new PerformanceObserver(function (list) {
                if (list.getEntries().length > 0) reset();
            });
            obs.observe({ type: 'resource', buffered: false });
            reset();
            // Hard timeout so we never hang forever
            setTimeout(function () {
                try { obs.disconnect(); } catch (e) {}
                resolve('timeout');
            }, 8000);
        } catch (e) {
            resolve('unsupported');
        }
    });
})()
