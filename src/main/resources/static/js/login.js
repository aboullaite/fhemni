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
    const confirmingMagicLink = parameters.get('confirm') === 'magic-link';
    document.querySelector('#loginTitle').textContent = confirmingMagicLink
        ? t('login.confirmTitle')
        : t(`login.intent.${intent}.title`);
    document.querySelector('#loginIntro').textContent = confirmingMagicLink
        ? t('login.confirmIntro')
        : t(`login.intent.${intent}.intro`);
    error.hidden = !parameters.has('error');

    try {
        const current = await window.FhemniAuth.session();
        if (current.authenticated) {
            providers.innerHTML = `
                <p>${escapeHtml(t('login.signedInAs', { name: current.user.displayName }))}</p>
                <a class="primary-button auth-provider" href="${escapeHtml(destination)}">${escapeHtml(t('login.continue'))}</a>`;
            return;
        }
        if (confirmingMagicLink) {
            const confirmation = await magicLinkConfirmation();
            if (!confirmation) {
                providers.innerHTML = `<p class="magic-link-status">${escapeHtml(t('login.confirmError'))}</p>`;
                return;
            }
            providers.innerHTML = `
                <p class="magic-link-status">
                    ${escapeHtml(t('login.confirmAccount'))}
                    <bdi class="font-bold">${escapeHtml(confirmation.maskedEmail)}</bdi>
                </p>
                <button id="confirmMagicLink" class="auth-provider auth-provider--email" type="button">
                    <svg class="auth-provider__icon" viewBox="0 0 24 24" aria-hidden="true">
                        <path d="M5 12.5 9.5 17 19 7.5"/>
                    </svg>
                    <span>${escapeHtml(t('login.confirmAction'))}</span>
                </button>
                <p class="magic-link-status" aria-live="polite" hidden></p>`;
            providers.querySelector('#confirmMagicLink')?.addEventListener('click', confirmMagicLink);
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
            <a class="auth-provider ${providerClass(provider.id)}" href="${escapeHtml(provider.loginUrl)}">
                ${providerIcon(provider.id)}
                <span>${escapeHtml(t('login.continueWith', { provider: provider.label }))}</span>
            </a>`).join('');
        const magicLink = current.magicLinkEnabled ? `
            ${current.providers.length ? `<div class="auth-divider"><span>${escapeHtml(t('login.or'))}</span></div>` : ''}
            <form id="magicLinkForm" class="magic-link-form">
                <label for="magicLinkEmail">${escapeHtml(t('login.emailLabel'))}</label>
                <div class="magic-link-fields">
                    <input id="magicLinkEmail" name="email" type="email" autocomplete="email"
                           maxlength="320" required placeholder="${escapeHtml(t('login.emailPlaceholder'))}">
                    <button class="auth-provider auth-provider--email" type="submit">
                        <svg class="auth-provider__icon" viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M3 6.75A1.75 1.75 0 0 1 4.75 5h14.5A1.75 1.75 0 0 1 21 6.75v10.5A1.75 1.75 0 0 1 19.25 19H4.75A1.75 1.75 0 0 1 3 17.25V6.75Z"/>
                            <path d="m4 7 8 6 8-6"/>
                        </svg>
                        <span>${escapeHtml(t('login.emailAction'))}</span>
                    </button>
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

async function magicLinkConfirmation() {
    try {
        const response = await fetch('/auth/magic-link/preview', {
            headers: { 'Accept': 'application/json' }
        });
        if (!response.ok) return null;
        return await response.json();
    } catch (_) {
        return null;
    }
}

async function confirmMagicLink(event) {
    const button = event.currentTarget;
    const status = button.parentElement.querySelector('.magic-link-status');
    button.disabled = true;
    status.hidden = true;
    try {
        const options = await window.FhemniAuth.withCsrf({ method: 'POST' });
        const response = await fetch('/auth/magic-link/confirm', options);
        if (!response.ok) throw new Error('confirmation failed');
        const result = await response.json();
        window.location.replace(safeLocalPath(result.returnTo) ? result.returnTo : '/');
    } catch (_) {
        status.textContent = t('login.confirmError');
        status.hidden = false;
        button.disabled = false;
    }
}

function providerClass(providerId) {
    return ['google', 'discord'].includes(providerId)
        ? `auth-provider--${providerId}`
        : 'auth-provider--generic';
}

function providerIcon(providerId) {
    if (providerId === 'google') {
        return `<span class="auth-provider__google-icon" aria-hidden="true">
            <svg class="auth-provider__icon" viewBox="0 0 48 48">
                <path fill="#FFC107" d="M6.3 14.7A20 20 0 0 0 4 24c0 3.4.8 6.5 2.3 9.3l6.6-5.1A12 12 0 0 1 12 24c0-1.5.3-2.9.9-4.2l-6.6-5.1Z"/>
                <path fill="#FF3D00" d="m6.3 14.7 6.6 4.8A12 12 0 0 1 24 12c3.1 0 5.8 1.2 8 3l5.6-5.6A20 20 0 0 0 6.3 14.7Z"/>
                <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2A12 12 0 0 1 12.7 28l-6.5 5.1A20 20 0 0 0 24 44Z"/>
                <path fill="#1976D2" d="M43.6 20H24v8h11.3a12 12 0 0 1-4.1 5.6l6.2 5.2C41 35.4 44 30.4 44 24c0-1.4-.1-2.7-.4-4Z"/>
            </svg>
        </span>`;
    }
    if (providerId === 'discord') {
        return `<svg class="auth-provider__icon" viewBox="0 0 24 24" aria-hidden="true">
            <path fill="currentColor" d="M20.3 4.4A19.8 19.8 0 0 0 15.4 3c-.2.4-.5 1-.7 1.5a18.3 18.3 0 0 0-5.4 0A15 15 0 0 0 8.6 3a19.7 19.7 0 0 0-4.9 1.4C.6 9 .2 13.5.6 18a19.9 19.9 0 0 0 6 3c.5-.7.9-1.5 1.3-2.3-.7-.3-1.3-.6-1.9-.9l.5-.4c3.7 1.7 7.7 1.7 11.3 0l.5.4c-.6.4-1.3.7-1.9.9.4.8.8 1.6 1.3 2.3a19.8 19.8 0 0 0 6-3c.5-5.2-.8-9.7-3.4-13.6ZM8.3 15.3c-1.1 0-2-1-2-2.3s.9-2.3 2-2.3 2 1 2 2.3-.9 2.3-2 2.3Zm7.4 0c-1.1 0-2-1-2-2.3s.9-2.3 2-2.3 2 1 2 2.3-.9 2.3-2 2.3Z"/>
        </svg>`;
    }
    return '';
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
        && value.length <= 1024
        && value.startsWith('/')
        && !value.startsWith('//')
        && !value.includes('\\')
        && !/[\u0000-\u001f\u007f]/.test(value);
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}
