(function () {
    let theme = 'light';
    try { if (localStorage.getItem('primeskill-theme') === 'dark') theme = 'dark'; } catch (_) {}
    document.documentElement.dataset.theme = theme;
    const observeNavigationTransition = event => {
        if (!event.viewTransition) return;
        event.viewTransition.ready.catch(error => {
            // Navigation may skip the animation when opt-in changes or the page
            // has already been revealed. Neither cancellation changes page data.
            if (error?.name === 'InvalidStateError' &&
                (error.message.includes('ViewTransition opt-in disabled') ||
                 error.message.includes('Page already revealed'))) return;
            throw error;
        });
    };
    window.addEventListener('pageswap', observeNavigationTransition);
    window.addEventListener('pagereveal', observeNavigationTransition);
})();
