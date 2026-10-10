'use strict';
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
const syncTheme = () => {
    const dark = document.documentElement.dataset.theme === 'dark';
    document.querySelectorAll('[data-theme-toggle]').forEach(button => {
        button.hidden = false; button.setAttribute('aria-pressed', String(dark));
        button.setAttribute('aria-label', dark ? 'Switch to light theme' : 'Switch to dark theme');
    });
};
syncTheme();
document.querySelectorAll('[data-theme-toggle]').forEach(button => button.addEventListener('click', () => {
    const theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = theme;
    try { localStorage.setItem('primeskill-theme', theme); } catch (_) {}
    syncTheme();
}));
document.querySelectorAll('[data-password-toggle]').forEach(button => {
    button.hidden = false;
    button.addEventListener('click', () => {
        const input = document.getElementById(button.dataset.passwordToggle);
        const showing = input.type === 'password'; input.type = showing ? 'text' : 'password';
        button.textContent = showing ? 'Hide' : 'Show'; button.setAttribute('aria-pressed', String(showing));
    });
});
// Tag checkboxes enhance the unchanged comma-separated GET input.
const tagInput = document.querySelector('[data-tags-input]');
const tagPicker = document.querySelector('[data-tag-picker]');
if (tagInput && tagPicker) {
    const selected = new Set(tagInput.value.split(',').map(x => x.trim().toLowerCase()).filter(Boolean));
    const boxes = [...tagPicker.querySelectorAll('input[type=checkbox]')];
    boxes.forEach(box => { box.checked = selected.has(box.value); });
    tagPicker.hidden = false; tagInput.closest('[data-tags-fallback]').hidden = true;
    boxes.forEach(box => box.addEventListener('change', () => {
        if (box.checked) selected.add(box.value); else selected.delete(box.value);
        tagInput.value = [...selected].join(',');
    }));
}
const filters = document.querySelector('[data-filters]');
const filterTrigger = document.querySelector('[data-filter-open]');
if (filters && filterTrigger) {
    filters.classList.add('ps-filter-enhanced'); filterTrigger.hidden = false;
    const mobile = window.matchMedia('(max-width: 767px)');
    let background = [], previousOverflow = '';
    const focusables = () => [...filters.querySelectorAll('a[href],button,input,select')]
        .filter(x => !x.disabled && x.getClientRects().length);
    const close = (restoreFocus = true) => {
        filters.classList.remove('ps-filter-open'); filters.removeAttribute('role'); filters.removeAttribute('aria-modal');
        background.forEach(({node,inert}) => { node.inert = inert; }); background = [];
        filterTrigger.setAttribute('aria-expanded', 'false'); document.body.style.overflow = previousOverflow;
        if (restoreFocus) filterTrigger.focus();
    };
    filterTrigger.addEventListener('click', () => {
        if (!mobile.matches || filters.classList.contains('ps-filter-open')) return;
        filters.classList.add('ps-filter-open'); filters.setAttribute('role', 'dialog'); filters.setAttribute('aria-modal', 'true');
        previousOverflow = document.body.style.overflow;
        // Isolate siblings at every ancestor, keeping the original GET form and its inputs together.
        for (let node = filters; node && node !== document.body; node = node.parentElement) {
            for (const sibling of node.parentElement.children) {
                if (sibling === node || ['SCRIPT','STYLE'].includes(sibling.tagName)) continue;
                background.push({node:sibling,inert:sibling.inert}); sibling.inert = true;
            }
        }
        filterTrigger.setAttribute('aria-expanded', 'true'); document.body.style.overflow = 'hidden';
        focusables()[0]?.focus();
    });
    filters.querySelector('[data-filter-close]')?.addEventListener('click', () => close());
    document.addEventListener('focusin', event => {
        if (filters.classList.contains('ps-filter-open') && !filters.contains(event.target)) focusables()[0]?.focus();
    });
    document.addEventListener('keydown', event => {
        if (!filters.classList.contains('ps-filter-open')) return;
        if (event.key === 'Escape') close();
        if (event.key === 'Tab') {
            const elements = focusables();
            const first = elements[0], last = elements[elements.length - 1];
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        }
    });
    mobile.addEventListener('change', () => { if (!mobile.matches && filters.classList.contains('ps-filter-open')) close(false); });
    window.addEventListener('pageshow', () => { if (filters.classList.contains('ps-filter-open')) close(false); });
}
// Capture before the existing duplicate-submit handler can lock the form.
const dialog = document.getElementById('ps-confirm');
let confirmation = null;
if (dialog && typeof dialog.showModal === 'function') {
    document.querySelectorAll('form[data-confirm]').forEach(form => form.addEventListener('submit', event => {
        if (form.dataset.confirmed === 'true') { delete form.dataset.confirmed; return; }
        event.preventDefault();
        confirmation = {form, submitter:event.submitter, focus:document.activeElement};
        dialog.querySelector('#ps-confirm-title').textContent = form.dataset.confirmTitle || 'Confirm action';
        dialog.querySelector('#ps-confirm-copy').textContent = form.dataset.confirm;
        const accept = dialog.querySelector('[data-confirm-accept]');
        accept.textContent = form.dataset.confirmAction || 'Confirm';
        accept.className = form.dataset.confirmDanger === 'true' ? 'ps-btn ps-btn--danger' : 'ps-btn ps-btn--primary';
        dialog.showModal(); dialog.querySelector('[data-confirm-cancel]').focus();
    }, true));
    dialog.querySelector('[data-confirm-cancel]').addEventListener('click', () => dialog.close());
    dialog.addEventListener('close', () => { const focus = confirmation?.focus; confirmation = null; if (focus?.isConnected) focus.focus(); });
    dialog.querySelector('[data-confirm-accept]').addEventListener('click', () => {
        const current = confirmation; if (!current) return;
        current.form.dataset.confirmed = 'true'; dialog.close();
        current.form.requestSubmit(current.submitter || undefined);
    });
} else {
    document.querySelectorAll('form[data-confirm]').forEach(form => form.addEventListener('submit', event => {
        if (!window.confirm(form.dataset.confirm)) event.preventDefault();
    }, true));
}
let revealObserver;
if (!reducedMotion.matches && 'IntersectionObserver' in window) {
    revealObserver = new IntersectionObserver(entries => entries.forEach(entry => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('ps-revealing'); revealObserver.unobserve(entry.target);
    }), {threshold:.08});
    document.querySelectorAll('[data-reveal]').forEach((element,index) => {
        if (index < 6) { element.style.setProperty('--ps-delay', (index % 3) * 35 + 'ms'); revealObserver.observe(element); }
    });
}
reducedMotion.addEventListener('change', event => { if (event.matches) revealObserver?.disconnect(); });

document.querySelectorAll('[data-submit-on-change]').forEach(element => element.addEventListener('change', () => element.form.requestSubmit()));

document.querySelectorAll('[data-avatar-image]').forEach(img => {
    const fallback = () => { img.hidden = true; img.closest('.ps-avatar').querySelector('[data-avatar-fallback]').hidden = false; };
    img.addEventListener('error', fallback);
    if (img.complete && !img.naturalWidth) fallback();
});
