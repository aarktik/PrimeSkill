'use strict';
async function sessionRequest(url, body) {
    const tokenResponse = await fetch('/api/v1/auth/csrf', {credentials: 'same-origin'});
    if (!tokenResponse.ok) throw new Error('โหลดข้อมูลความปลอดภัยไม่ได้ โปรดรีเฟรชหน้าแล้วลองอีกครั้ง');
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
    const original = button.textContent;
    const error = auth.querySelector('[data-form-error]');
    button.disabled = true; button.textContent = 'กำลังบันทึก…'; error.hidden = true;
    try {
        const response = await sessionRequest(auth.action, Object.fromEntries(new FormData(auth)));
        if (!response.ok) {
            const messages = {400: 'ตรวจสอบข้อมูลให้ครบและถูกต้อง', 401: 'อีเมลหรือรหัสผ่านไม่ถูกต้อง',
                403: 'เซสชันหมดอายุ โปรดรีเฟรชหน้าแล้วลองอีกครั้ง', 409: 'อีเมลนี้มีบัญชีแล้ว กรุณาเข้าสู่ระบบ'};
            throw new Error(messages[response.status] || 'ดำเนินการไม่สำเร็จ โปรดลองอีกครั้ง');
        }
        window.location.assign(auth.dataset.auth === 'register' ? '/login' : '/dashboard/tools');
    } catch (failure) {
        auth.querySelector('[name=password]').value = '';
        error.textContent = failure instanceof TypeError ? 'เชื่อมต่อไม่ได้ โปรดลองอีกครั้ง' : failure.message;
        error.hidden = false; error.focus();
    } finally { button.disabled = false; button.textContent = original; }
});
for (const form of document.querySelectorAll('[data-logout]')) form.addEventListener('submit', async event => {
    event.preventDefault();
    const button = form.querySelector('button'); button.disabled = true;
    try {
        const response = await sessionRequest(form.action);
        if (!response.ok && response.status !== 401) throw new Error();
        window.location.assign('/login');
    } catch { form.querySelector('[data-logout-error]').textContent = 'ออกจากระบบไม่สำเร็จ โปรดลองอีกครั้ง'; }
    finally { button.disabled = false; }
});
const menu = document.querySelector('.ps-menu-button');
if (menu) {
    const nav = document.getElementById(menu.getAttribute('aria-controls'));
    menu.addEventListener('click', () => {
        const open = menu.getAttribute('aria-expanded') !== 'true';
        menu.setAttribute('aria-expanded', String(open)); nav.classList.toggle('is-open', open);
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && menu.getAttribute('aria-expanded') === 'true') {
            menu.setAttribute('aria-expanded', 'false'); nav.classList.remove('is-open'); menu.focus();
        }
    });
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
if (invalidField) invalidField.focus();
