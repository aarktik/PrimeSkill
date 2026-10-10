'use strict';
async function sessionRequest(url, body) {
    const tokenResponse = await fetch('/api/v1/auth/csrf', {credentials: 'same-origin'});
    if (!tokenResponse.ok) throw new Error('Unable to load security information. Refresh the page and try again.');
    const csrf = await tokenResponse.json();
    return fetch(url, {method: 'POST', credentials: 'same-origin',
        headers: {'Content-Type': 'application/json', [csrf.headerName]: csrf.token},
        body: body === undefined ? undefined : JSON.stringify(body)});
}
const auth = document.querySelector('[data-auth]');
if (auth) auth.addEventListener('submit', async event => {
    event.preventDefault();
    const button = auth.querySelector('button[type=submit]');
    if (button.disabled) return;
    const original = button.innerHTML;
    const error = auth.querySelector('[data-form-error]');
    button.disabled = true; button.textContent = 'Saving…'; error.hidden = true;
    try {
        const fields = new FormData(auth);
        const body = {email:fields.get('email'), password:fields.get('password')};
        if (auth.dataset.auth === 'register') body.displayName = fields.get('displayName');
        const response = await sessionRequest(auth.action, body);
        if (!response.ok) {
            const messages = {400: 'Check your details and try again.', 401: 'The email or password is incorrect.',
                403: 'Your session has expired. Refresh the page and try again.', 409: 'This email already has an account. Please sign in.'};
            throw new Error(messages[response.status] || 'Unable to complete the request. Please try again.');
        }
        const target = auth.dataset.returnTo || '/dashboard/tools';
        window.location.assign(auth.dataset.auth === 'register' ? '/login?next=' + encodeURIComponent(target) : target);
    } catch (failure) {
        auth.querySelector('[name=password]').value = '';
        error.textContent = failure instanceof TypeError ? 'Unable to connect. Please try again.' : failure.message;
        error.hidden = false; error.focus();
    } finally { button.disabled = false; button.innerHTML = original; }
});
if (auth) {
    auth.querySelector('button[type=submit]').disabled = false;
    auth.querySelector('[data-auth-unavailable]')?.setAttribute('hidden', '');
}
for (const form of document.querySelectorAll('[data-logout]')) form.addEventListener('submit', async event => {
    event.preventDefault();
    const button = form.querySelector('button'); button.disabled = true;
    try {
        const response = await sessionRequest(form.action);
        if (!response.ok && response.status !== 401) throw new Error();
        window.location.assign('/login');
    } catch { form.querySelector('[data-logout-error]').textContent = 'Unable to sign out. Please try again.'; }
    finally { button.disabled = false; }
});
const menu = document.querySelector('.ps-menu-button');
if (menu) {
    const nav = document.getElementById(menu.getAttribute('aria-controls'));
    const closeMenu = restoreFocus => {
        menu.setAttribute('aria-expanded', 'false'); menu.setAttribute('aria-label', 'Open navigation');
        nav.classList.remove('is-open'); if (restoreFocus) menu.focus();
    };
    menu.addEventListener('click', () => {
        const open = menu.getAttribute('aria-expanded') !== 'true';
        menu.setAttribute('aria-expanded', String(open)); menu.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
        nav.classList.toggle('is-open', open);
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && menu.getAttribute('aria-expanded') === 'true') {
            closeMenu(true);
        }
    });
    document.addEventListener('click', event => { if (!event.target.closest('.ps-nav')) closeMenu(false); });
    window.matchMedia('(max-width: 1100px)').addEventListener('change', () => closeMenu(false));
    menu.closest('.ps-nav').classList.add('ps-nav--enhanced');
}
// Native POST forms keep server validation and CSRF. Lock only after confirmation succeeds.
for (const form of document.querySelectorAll('form[method=post]:not([data-auth]):not([data-logout])')) {
    form.addEventListener('submit', event => {
        if (event.defaultPrevented) return;
        if (form.dataset.submitting) { event.preventDefault(); return; }
        form.dataset.submitting = 'true';
        for (const button of form.querySelectorAll('button[type=submit]')) { button.disabled = true; }
        form.setAttribute('aria-busy', 'true');
    });
}
window.addEventListener('pageshow', () => {
    for (const form of document.querySelectorAll('form[data-submitting]')) {
        delete form.dataset.submitting; form.removeAttribute('aria-busy');
        for (const button of form.querySelectorAll('button[type=submit]')) button.disabled = false;
    }
});
const invalidField = document.querySelector('[aria-invalid=true]');
const errorSummary = document.querySelector('[data-error-summary]');
if (errorSummary) errorSummary.focus(); else if (invalidField) invalidField.focus();
