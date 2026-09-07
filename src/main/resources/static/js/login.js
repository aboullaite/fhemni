document.addEventListener('DOMContentLoaded', renderLogin);
document.addEventListener('fhemni:localechange', renderLogin);

function t(key, parameters = {}) {
    return window.FhemniI18n?.t(key, parameters) ?? key;
}

async function renderLogin() {
    const providers = document.querySelector('#loginProviders');
    const error = document.querySelector('#loginError');
    const parameters = new URLSearchParams(window.location.search);
    const intent = ['chat', 'suggest', 'vote'].includes(parameters.get('intent'))
        ? parameters.get('intent')
        : 'default';
    const requestedDestination = parameters.get('continue');
    const destination = safeLocalPath(requestedDestination) ? requestedDestination : '/';
    document.querySelector('#loginTitle').textContent = t(`login.intent.${intent}.title`);
    document.querySelector('#loginIntro').textContent = t(`login.intent.${intent}.intro`);
    error.hidden = !parameters.has('error');

    try {
        const current = await window.FhemniAuth.session();
        if (current.authenticated) {
            providers.innerHTML = `
                <p>${escapeHtml(t('login.signedInAs', { name: current.user.displayName }))}</p>
                <a class="primary-button auth-provider" href="${escapeHtml(destination)}">${escapeHtml(t('login.continue'))}</a>`;
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

function safeLocalPath(value) {
    return typeof value === 'string'
        && value.startsWith('/')
        && !value.startsWith('//')
        && !value.includes('\\')
        && !value.includes('\r')
        && !value.includes('\n');
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}
