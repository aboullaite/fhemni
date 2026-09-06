document.addEventListener('DOMContentLoaded', renderLogin);
document.addEventListener('fhemni:localechange', renderLogin);

function t(key, parameters = {}) {
    return window.FhemniI18n?.t(key, parameters) ?? key;
}

async function renderLogin() {
    const providers = document.querySelector('#loginProviders');
    const error = document.querySelector('#loginError');
    error.hidden = !new URLSearchParams(window.location.search).has('error');

    try {
        const current = await window.FhemniAuth.session();
        if (current.authenticated) {
            providers.innerHTML = `
                <p>${escapeHtml(t('login.signedInAs', { name: current.user.displayName }))}</p>
                <a class="primary-button auth-provider" href="/">${escapeHtml(t('login.continue'))}</a>`;
            return;
        }
        if (!current.providers.length) {
            providers.innerHTML = `
                <div class="login-not-configured">
                    ${escapeHtml(t('login.notConfigured'))}
                </div>`;
            return;
        }
        providers.innerHTML = current.providers.map(provider => `
            <a class="auth-provider" href="${escapeHtml(provider.loginUrl)}">
                ${escapeHtml(t('login.continueWith', { provider: provider.label }))}
            </a>`).join('');
    } catch (_) {
        providers.innerHTML = `<div class="login-not-configured">${escapeHtml(t('login.unavailable'))}</div>`;
    }
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}
