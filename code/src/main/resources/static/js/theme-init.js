(function () {
    let theme = 'light';
    try { if (localStorage.getItem('primeskill-theme') === 'dark') theme = 'dark'; } catch (_) {}
    document.documentElement.dataset.theme = theme;
    const observeNavigationTransition = event => {
        if (!event.viewTransition) return;
        event.viewTransition.ready.catch(error => {
            // Leaving an opted-in public page can intentionally skip the animation.
            if (error?.name === 'InvalidStateError' && error.message.includes('ViewTransition opt-in disabled')) return;
            throw error;
        });
    };
    window.addEventListener('pageswap', observeNavigationTransition);
    window.addEventListener('pagereveal', observeNavigationTransition);
})();
