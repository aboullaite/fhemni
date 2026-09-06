(function () {
    let sessionPromise;

    function t(key, parameters = {}) {
        return window.FhemniI18n?.t(key, parameters) ?? key;
    }

    function session() {
        if (!sessionPromise) {
            sessionPromise = fetchJson('/api/auth/session', 10_000);
        }
        return sessionPromise;
    }

    async function withCsrf(options = {}) {
        const method = String(options.method || 'GET').toUpperCase();
        if (['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
            return options;
        }
        const current = await session();
        const headers = new Headers(options.headers || {});
        headers.set(current.csrfHeader, current.csrfToken);
        return { ...options, headers };
    }

    async function signOut() {
        const current = await session();
        const response = await fetch('/logout', {
            method: 'POST',
            headers: { [current.csrfHeader]: current.csrfToken }
        });
        if (!response.ok) {
            throw new Error(t('common.signOutFailed'));
        }
        window.location.assign('/');
    }

    function loginPage(returnTo = window.location.pathname) {
        const target = returnTo.startsWith('/') ? returnTo : '/';
        return `/login?continue=${encodeURIComponent(target)}`;
    }

    async function renderNavigation() {
        const container = document.querySelector('#authNav');
        if (!container) return;
        try {
            const current = await session();
            if (!current.authenticated) {
                container.innerHTML = `<a class="auth-link" href="${loginPage()}">${escapeHtml(t('common.signIn'))}</a>`;
                return;
            }
            const user = current.user;
            const avatar = safeUrl(user.avatarUrl)
                ? `<img src="${escapeHtml(user.avatarUrl)}" alt="">`
                : `<span>${escapeHtml(initials(user.displayName))}</span>`;
            container.innerHTML = `
                <div class="account-menu">
                    <div class="account-avatar">${avatar}</div>
                    <span class="account-name">${escapeHtml(user.displayName)}</span>
                    ${user.role === 'ADMIN' ? `<a class="auth-link compact" href="/admin">${escapeHtml(t('common.admin'))}</a>` : ''}
                    <button class="auth-link compact" type="button" data-sign-out>${escapeHtml(t('common.signOut'))}</button>
                </div>`;
            container.querySelector('[data-sign-out]')?.addEventListener('click', event => {
                event.currentTarget.disabled = true;
                signOut().catch(() => {
                    event.currentTarget.disabled = false;
                });
            });
        } catch (_) {
            container.innerHTML = `<span class="auth-unavailable">${escapeHtml(t('common.accountUnavailable'))}</span>`;
        }
    }

    async function fetchJson(url, timeoutMs) {
        const controller = new AbortController();
        const timeout = window.setTimeout(() => controller.abort(), timeoutMs);
        try {
            const response = await fetch(url, { signal: controller.signal });
            if (!response.ok) {
                throw new Error(t('common.requestFailed', { status: response.status }));
            }
            return await response.json();
        } finally {
            window.clearTimeout(timeout);
        }
    }

    function safeUrl(value) {
        try {
            const url = new URL(value);
            return ['http:', 'https:'].includes(url.protocol);
        } catch (_) {
            return false;
        }
    }

    function initials(value) {
        return String(value || '?').split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase();
    }

    function escapeHtml(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    }

    window.FhemniAuth = { session, withCsrf, signOut, loginPage };
    document.addEventListener('DOMContentLoaded', renderNavigation);
    document.addEventListener('fhemni:localechange', renderNavigation);
})();
