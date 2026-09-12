document.addEventListener('DOMContentLoaded', renderLogin);
document.addEventListener('fhemni:localechange', renderLogin);

function t(key, parameters = {}) {
    return window.FhemniI18n?.t(key, parameters) ?? key;
}

async function renderLogin() {
    const providers = document.querySelector('#loginProviders');
    const error = document.querySelector('#loginError');
    const parameters = new URLSearchParams(window.location.search);
    const intent = ['chat', 'programme', 'suggest', 'vote'].includes(parameters.get('intent'))
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
        if (!current.providers.length && !current.magicLinkEnabled) {
            providers.innerHTML = `
                <div class="login-not-configured">
                    ${escapeHtml(t('login.notConfigured'))}
                </div>`;
            return;
        }
        const oauthProviders = current.providers.map(provider => `
            <a class="auth-provider" href="${escapeHtml(provider.loginUrl)}">
                ${escapeHtml(t('login.continueWith', { provider: provider.label }))}
            </a>`).join('');
        const magicLink = current.magicLinkEnabled ? `
            ${current.providers.length ? `<div class="auth-divider"><span>${escapeHtml(t('login.or'))}</span></div>` : ''}
            <form id="magicLinkForm" class="magic-link-form">
                <label for="magicLinkEmail">${escapeHtml(t('login.emailLabel'))}</label>
                <div class="magic-link-fields">
                    <input id="magicLinkEmail" name="email" type="email" autocomplete="email"
                           maxlength="320" required placeholder="${escapeHtml(t('login.emailPlaceholder'))}">
                    <button class="auth-provider" type="submit">${escapeHtml(t('login.emailAction'))}</button>
                </div>
                <p class="magic-link-status" aria-live="polite" hidden></p>
            </form>` : '';
        providers.innerHTML = oauthProviders + magicLink;
        providers.querySelector('#magicLinkForm')?.addEventListener('submit', event => {
            requestMagicLink(event, destination);
        });
    } catch (_) {
        providers.innerHTML = `<div class="login-not-configured">${escapeHtml(t('login.unavailable'))}</div>`;
    }
}

async function requestMagicLink(event, destination) {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector('button');
    const status = form.querySelector('.magic-link-status');
    button.disabled = true;
    status.hidden = true;
    try {
        const options = await window.FhemniAuth.withCsrf({
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: form.email.value, returnTo: destination })
        });
        const response = await fetch('/api/auth/magic-link', options);
        if (!response.ok) throw new Error('request failed');
        form.querySelector('.magic-link-fields').hidden = true;
        form.querySelector('label').hidden = true;
        status.textContent = t('login.emailSent');
        status.hidden = false;
    } catch (_) {
        status.textContent = t('login.emailError');
        status.hidden = false;
        button.disabled = false;
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
