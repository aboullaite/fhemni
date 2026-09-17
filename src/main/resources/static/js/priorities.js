(function () {
    const STORAGE_PREFIX = 'fhemni.civic-priorities';
    const COPY = {
        ar: {
            pageTitle: 'بوصلة الأولويات — فهّمني',
            description: '18 سؤال باش تكتاشف الأولويات والمفاضلات العمومية اللي كتعني ليك فالمغرب.',
            loading: 'كنوجدو الأسئلة…', eyebrow: 'بوصلة الأولويات',
            orbitTags: ['الأسعار', 'الصحة', 'الماء', 'السكن', 'الحماية الاجتماعية', 'الحكامة', 'الشغل', 'التعليم', 'المساواة'],
            duration: '18 سؤال · حوالي 5 دقايق', privacy: 'الأجوبة كتبقى فهاد الجهاز وما كنخزنوهاش فالسيرفر',
            start: 'بدا دابا', resume: 'كمل من فين وقفتي', question: 'السؤال {current} من {total}', exit: 'نخرج ونكمل من بعد',
            important: 'هاد الموضوع مهم عندي', context: 'علاش الاختيار ماشي ساهل؟', source: 'المصدر',
            previous: 'السؤال اللي فات', skip: 'دوز هاد السؤال',
            answers: ['ماشي رأيي نهائيا', 'بعيد على رأيي', 'بين وبين', 'قريب لرأيي', 'هاد الشي هو رأيي'],
            resultsTitle: 'الأولويات ديالك',
            completion: 'جاوبتي على {answered} من {total} سؤال', themesTitle: 'الأولويات اللي عندك فيها موقف قوي',
            strength: 'قوة الأولوية {score}%', importantCount: '{count} معلمين كمهمين',
            methodology: 'كيفاش تحسبات النتيجة؟', restart: 'نعاود من الأول', explore: 'قارن البرامج كاملة',
            programmeKicker: 'الخطوة الثانية', programmeTitle: 'شوف شنو كتقول البرامج الرسمية',
            programmeIntro: 'اختار المواضيع والأحزاب اللي بغيتي تقارن. فهّمني كيجمع ليك الوعود المرتبطة من البرامج المنشورة بلا ترتيب ولا نقطة توافق.',
            importantMark: 'مهم عندك', errorTitle: 'ما قدرناش نكملو دابا', error: 'عاود جرب. التقدم ديالك باقي محفوظ فهاد الجهاز.',
            retry: 'نعاود نجرب', needAnswer: 'جاوب على سؤال واحد على الأقل باش نوجدو البروفايل ديالك.',
            positionMatrix: 'شوف مواقف الأحزاب',
            compassTitle: 'البوصلة الحزبية ديالك',
            compassIntro: 'هاد النسب مبنية على المقارنة بين أجوبتك والمواقف الموثقة ديال كل حزب من البرنامج الرسمي ديالو.',
            compassLoading: 'كنحسبو التوافق مع الأحزاب…',
            compassEmpty: 'ما كاينش بزاف مواقف منشورة دابا باش نقارنو.',
            compassCoverage: '{covered} من {total} سؤال مغطيين',
            compassMore: 'شوف باقي الأحزاب',
            compassAgree: 'متوافقين', compassDisagree: 'مختلفين', compassMixed: 'مختلط',
            compassDetails: 'التفاصيل', compassReviewed: '{count} موقف تم تحليلهم من البرامج الرسمية',
            compassDisclaimer: 'التوافق مبني على البرامج المنشورة. الغياب ما كيعنيش معارضة.',
            compassShare: 'شارك النتيجة ديالك', compassShareHint: 'البوصلة ولا أقرب 3',
            shareTitle: 'شارك النتيجة ديالك', shareIntro: 'شارك الصورة مباشرة، ولا صايب رابط عمومي بمعاينة. الرابط كيطلع غير صورة النتيجة وما كيطلعش الأجوبة ديالك.',
            shareCompass: 'بوصلة الأولويات', shareParties: 'أقرب 3 أحزاب',
            shareCompassTitle: 'بوصلة الأولويات ديالي', sharePartiesTitle: 'الأحزاب الأقرب لأولوياتي',
            sharePreparing: 'كنوجدو الصورة…', sharePublishing: 'كنوجدو الرابط العمومي…', shareNative: 'شارك الصورة', shareLink: 'شارك الرابط', shareDownload: 'حمّل الصورة', shareChooseNetwork: 'اختار فين بغيتي تشارك', shareCopyLink: 'نسخ الرابط', shareLinkCopied: 'تنسخ الرابط.', shareLinkCopyError: 'ما قدرناش ننسخو الرابط.', shareSocialTextCopied: 'نسخنا لك النص. لصقو فالمنشور ديالك.',
            shareError: 'ما قدرناش نوجدو الصورة. عاود جرّب.', shareClose: 'سد',
            shareCardNote: 'مقارنة مبنية على المواقف الموثقة فالبرامج الرسمية المنشورة.',
            shareCardCta: 'دخل حتى نتا وجرّبها',
            storyQuestion: 'وانت، شنو كيهمّك أكثر؟', storySubtitle: 'جاوب على 18 سؤال ودير بوصلة الأولويات ديالك.', storyCta: 'جرّبها فـ fhemni.ma',
            sharePreviewCompass: 'معاينة بوصلة الأولويات', sharePreviewParties: 'معاينة أقرب ثلاثة أحزاب',
            shareCompassText: 'يالله جربت بوصلة الأولويات ديالي فـ فهّمني، وطلع ليا بلي أهم المواضيع عندي هي: {items}.\n\nوانت، شنو الأولويات ديالك؟ جرّبها: https://fhemni.ma/priorities',
            sharePartiesText: 'يالله اكتشفت فـ فهّمني الأحزاب الأقرب لأولوياتي، حسب البرامج الرسمية المنشورة:\n{items}\n\nوانت، شكون الأقرب لأولوياتك؟ جرّبها: https://fhemni.ma/priorities'
        },
        fr: {
            pageTitle: 'Boussole des priorités — Fhemni',
            description: '18 questions pour explorer les priorités publiques et les arbitrages qui comptent pour vous au Maroc.',
            loading: 'Préparation des questions…', eyebrow: 'Boussole des priorités',
            orbitTags: ['Prix', 'Santé', 'Eau', 'Logement', 'Protection sociale', 'Gouvernance', 'Emploi', 'Éducation', 'Égalité'],
            duration: '18 questions · environ 5 minutes', privacy: 'Vos réponses restent sur cet appareil et ne sont pas stockées sur le serveur',
            start: 'Commencer', resume: 'Reprendre le questionnaire', question: 'Question {current} sur {total}', exit: 'Quitter et reprendre plus tard',
            important: 'Ce sujet est important pour moi', context: 'Quel est l’arbitrage ?', source: 'Source',
            previous: 'Question précédente', skip: 'Passer cette question',
            answers: ['Pas du tout d’accord', 'Pas d’accord', 'Neutre', 'D’accord', 'Tout à fait d’accord'],
            resultsTitle: 'Vos priorités',
            completion: 'Vous avez répondu à {answered} questions sur {total}', themesTitle: 'Vos priorités les plus affirmées',
            strength: 'Intensité {score} %', importantCount: '{count} marquée(s) importante(s)',
            methodology: 'Comment ce résultat est-il calculé ?', restart: 'Recommencer', explore: 'Comparer tous les programmes',
            programmeKicker: 'Deuxième étape', programmeTitle: 'Consultez les programmes officiels',
            programmeIntro: 'Choisissez les sujets et les partis à comparer. Fhemni rassemble les promesses pertinentes des programmes publiés, sans classement ni score de compatibilité.',
            importantMark: 'Important pour vous', errorTitle: 'Impossible de terminer pour le moment', error: 'Réessayez. Votre progression reste enregistrée sur cet appareil.',
            retry: 'Réessayer', needAnswer: 'Répondez à au moins une question pour créer votre profil.',
            positionMatrix: 'Voir les positions des partis',
            compassTitle: 'Votre boussole partisane',
            compassIntro: 'Ces pourcentages comparent vos réponses aux positions documentées de chaque parti dans son programme officiel.',
            compassLoading: 'Calcul de la compatibilité avec les partis…',
            compassEmpty: 'Pas assez de positions publiées pour comparer.',
            compassCoverage: '{covered} questions sur {total} couvertes',
            compassMore: 'Voir les autres partis',
            compassAgree: 'D\'accord', compassDisagree: 'En désaccord', compassMixed: 'Mixte',
            compassDetails: 'Détails', compassReviewed: '{count} positions analysées à partir des programmes officiels',
            compassDisclaimer: 'La compatibilité est basée sur les programmes publiés. L\'absence ne signifie pas opposition.',
            compassShare: 'Partager mon résultat', compassShareHint: 'Boussole ou top 3',
            shareTitle: 'Partager votre résultat', shareIntro: 'Partagez directement l’image ou créez un lien public avec aperçu. Seule l’image du résultat est publiée, jamais vos réponses individuelles.',
            shareCompass: 'Boussole des priorités', shareParties: '3 partis les plus proches',
            shareCompassTitle: 'Ma boussole des priorités', sharePartiesTitle: 'Les partis les plus proches de mes priorités',
            sharePreparing: 'Préparation de l’image…', sharePublishing: 'Création du lien public…', shareNative: 'Partager l’image', shareLink: 'Partager le lien', shareDownload: 'Télécharger l’image', shareChooseNetwork: 'Choisissez où partager', shareCopyLink: 'Copier le lien', shareLinkCopied: 'Lien copié.', shareLinkCopyError: 'Impossible de copier le lien.', shareSocialTextCopied: 'Texte copié. Collez-le dans votre publication.',
            shareError: 'Impossible de préparer l’image. Réessayez.', shareClose: 'Fermer',
            shareCardNote: 'Comparaison fondée sur les positions documentées dans les programmes officiels publiés.',
            shareCardCta: 'À vous de jouer',
            storyQuestion: 'Et vous, quelles sont vos priorités ?', storySubtitle: 'Répondez à 18 questions et créez votre boussole des priorités.', storyCta: 'Essayez-la sur fhemni.ma',
            sharePreviewCompass: 'Aperçu de la boussole des priorités', sharePreviewParties: 'Aperçu des trois partis les plus proches',
            shareCompassText: 'Je viens de tester la boussole des priorités de Fhemni. Mes sujets les plus importants sont : {items}.\n\nEt vous, quelles sont vos priorités ? Essayez-la : https://fhemni.ma/priorities',
            sharePartiesText: 'Je viens de découvrir sur Fhemni les partis les plus proches de mes priorités, selon les programmes officiels publiés :\n{items}\n\nEt vous, quels partis sont les plus proches de vos priorités ? Essayez-la : https://fhemni.ma/priorities'
        },
        en: {
            pageTitle: 'Priority compass — Fhemni',
            description: '18 questions to explore the public priorities and trade-offs that matter to you in Morocco.',
            loading: 'Preparing the questions…', eyebrow: 'Priority compass',
            orbitTags: ['Prices', 'Health', 'Water', 'Housing', 'Social protection', 'Governance', 'Jobs', 'Education', 'Equality'],
            duration: '18 questions · about 5 minutes', privacy: 'Your answers stay on this device and are not stored on the server',
            start: 'Start', resume: 'Continue where you left off', question: 'Question {current} of {total}', exit: 'Leave and continue later',
            important: 'This issue matters a lot to me', context: 'What is the trade-off?', source: 'Source',
            previous: 'Previous question', skip: 'Skip this question',
            answers: ['Strongly disagree', 'Disagree', 'Neither', 'Agree', 'Strongly agree'],
            resultsTitle: 'Your priorities',
            completion: 'You answered {answered} of {total} questions', themesTitle: 'Your clearest priorities',
            strength: 'Priority strength {score}%', importantCount: '{count} marked important',
            methodology: 'How was this calculated?', restart: 'Start again', explore: 'Compare all programmes',
            programmeKicker: 'Next step', programmeTitle: 'See what the official programmes say',
            programmeIntro: 'Choose the topics and parties you want to compare. Fhemni gathers the relevant promises from published programmes, without ranking parties or producing a compatibility score.',
            importantMark: 'Important to you', errorTitle: 'We could not finish just now', error: 'Try again. Your progress is still saved on this device.',
            retry: 'Try again', needAnswer: 'Answer at least one question to build your profile.',
            positionMatrix: 'See party positions',
            compassTitle: 'Your party compass',
            compassIntro: 'These percentages compare your answers to each party\'s documented positions from their official programme.',
            compassLoading: 'Computing party compatibility…',
            compassEmpty: 'Not enough published positions to compare yet.',
            compassCoverage: '{covered} of {total} questions covered',
            compassMore: 'See other parties',
            compassAgree: 'Agree', compassDisagree: 'Disagree', compassMixed: 'Mixed',
            compassDetails: 'Details', compassReviewed: '{count} positions analysed from official programmes',
            compassDisclaimer: 'Compatibility is based on published programmes. Absence does not mean opposition.',
            compassShare: 'Share my result', compassShareHint: 'Compass or top 3',
            shareTitle: 'Share your result', shareIntro: 'Share the image directly or create a public preview link. Only the result image is published—never your individual answers.',
            shareCompass: 'Priority compass', shareParties: 'Closest 3 parties',
            shareCompassTitle: 'My priority compass', sharePartiesTitle: 'Parties closest to my priorities',
            sharePreparing: 'Preparing image…', sharePublishing: 'Creating public link…', shareNative: 'Share image', shareLink: 'Share link', shareDownload: 'Download image', shareChooseNetwork: 'Choose where to share', shareCopyLink: 'Copy link', shareLinkCopied: 'Link copied.', shareLinkCopyError: 'Could not copy the link.', shareSocialTextCopied: 'Text copied. Paste it into your post.',
            shareError: 'We could not prepare the image. Try again.', shareClose: 'Close',
            shareCardNote: 'Comparison based on documented positions in published official programmes.',
            shareCardCta: 'Try it yourself',
            storyQuestion: 'What matters most to you?', storySubtitle: 'Answer 18 questions and create your priority compass.', storyCta: 'Try it at fhemni.ma',
            sharePreviewCompass: 'Priority compass preview', sharePreviewParties: 'Closest three parties preview',
            shareCompassText: 'I just tried Fhemni’s priority compass. The issues that matter most to me are: {items}.\n\nWhat are your priorities? Try it: https://fhemni.ma/priorities',
            sharePartiesText: 'I just discovered the parties closest to my priorities on Fhemni, according to published official programmes:\n{items}\n\nWhich parties are closest to your priorities? Try it: https://fhemni.ma/priorities'
        }
    };

    let questionnaire;
    let state;
    let currentIndex = 0;
    let currentLocale = 'ar';

    document.addEventListener('DOMContentLoaded', initialize);
    document.addEventListener('fhemni:localechange', event => {
        const dialog = document.querySelector('#priorityShareDialog');
        if (dialog?.open) dialog.close();
        shareAssetGeneration++;
        clearShareAssets();
        currentLocale = event.detail.locale;
        loadQuestionnaire();
    });

    async function initialize() {
        currentLocale = window.FhemniI18n?.locale() || 'ar';
        bindActions();
        await loadQuestionnaire();
    }

    function bindActions() {
        document.querySelector('#priorityStart').addEventListener('click', start);
        document.querySelector('#priorityPrevious').addEventListener('click', previous);
        document.querySelector('#prioritySkip').addEventListener('click', skip);
        document.querySelector('#priorityExit').addEventListener('click', renderIntro);
        document.querySelector('#priorityRestart').addEventListener('click', restart);
        document.querySelector('#priorityRetry').addEventListener('click', () => questionnaire ? finish() : loadQuestionnaire());
        document.querySelector('#priorityShareClose').addEventListener('click', closeShareDialog);
        document.querySelector('#priorityShareDialog').addEventListener('click', event => {
            if (event.target === event.currentTarget) closeShareDialog();
        });
        document.querySelector('#priorityShareDialog').addEventListener('close', event => {
            closeSocialShareMenu();
            event.currentTarget.returnFocus?.focus();
            event.currentTarget.returnFocus = null;
        });
        document.querySelectorAll('[data-priority-share-kind]').forEach(button => {
            button.addEventListener('click', () => selectShareKind(button.dataset.priorityShareKind));
        });
        document.querySelector('#priorityShareNative').addEventListener('click', shareSelectedAsset);
        document.querySelector('#priorityShareLink').addEventListener('click', shareSelectedPublicLink);
        document.querySelectorAll('[data-priority-social]').forEach(button => {
            button.addEventListener('click', () => shareToSocialNetwork(button.dataset.prioritySocial));
        });
        document.addEventListener('click', event => {
            if (!event.target.closest('#priorityShareLinkMenu')) closeSocialShareMenu();
        });
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape') closeSocialShareMenu();
        });
        document.querySelector('#priorityShareDownload').addEventListener('click', event => {
            if (event.currentTarget.classList.contains('disabled')) event.preventDefault();
            else track('priority_result_shared', { kind: selectedShareKind, method: 'download' });
        });
    }

    async function loadQuestionnaire() {
        showOnly('priorityLoading');
        applyCopy();
        try {
            const response = await fetch(`/api/catalog/questionnaires/current?lang=${encodeURIComponent(currentLocale)}`);
            if (!response.ok) throw new Error(`Questionnaire request failed: ${response.status}`);
            questionnaire = await response.json();
            document.documentElement.lang = questionnaire.language;
            document.documentElement.dir = questionnaire.direction;
            state = restoreState(questionnaire.version);
            currentIndex = Math.min(state.current || 0, questionnaire.questions.length - 1);
            renderIntro();
        } catch (_) {
            showError(copy().error);
        }
    }

    function applyCopy() {
        const value = copy();
        document.title = value.pageTitle;
        document.querySelector('#priorityMetaDescription').content = value.description;
        document.querySelector('#priorityLoading p').textContent = value.loading;
        document.querySelector('#priorityEyebrow').textContent = value.eyebrow;
        document.querySelectorAll('[data-priority-orbit-tag]').forEach(tag => {
            tag.textContent = value.orbitTags[Number(tag.dataset.priorityOrbitTag)];
        });
        document.querySelector('#priorityDuration').textContent = value.duration;
        document.querySelector('#priorityPrivacy').textContent = value.privacy;
        document.querySelector('#priorityExit').textContent = value.exit;
        document.querySelector('#priorityImportantLabel').textContent = value.important;
        document.querySelector('#priorityContextLabel').textContent = value.context;
        document.querySelector('#priorityPrevious').textContent = value.previous;
        document.querySelector('#prioritySkip').textContent = value.skip;
        document.querySelector('#priorityResultsTitle').textContent = value.resultsTitle;
        document.querySelector('#priorityThemesTitle').textContent = value.themesTitle;
        document.querySelector('#priorityRestart').textContent = value.restart;
        document.querySelector('#priorityExplore').textContent = value.explore;
        document.querySelector('#priorityProgrammeKicker').textContent = value.programmeKicker;
        document.querySelector('#priorityProgrammeTitle').textContent = value.programmeTitle;
        document.querySelector('#priorityProgrammeIntro').textContent = value.programmeIntro;
        document.querySelector('#priorityCompassMoreLabel').textContent = value.compassMore;
        document.querySelector('#priorityCompassShareLabel').textContent = value.compassShare;
        document.querySelector('#priorityCompassShareHint').textContent = value.compassShareHint;
        document.querySelector('#priorityErrorTitle').textContent = value.errorTitle;
        document.querySelector('#priorityRetry').textContent = value.retry;
        document.querySelector('#priorityShareDialogTitle').textContent = value.shareTitle;
        document.querySelector('#priorityShareDialogIntro').textContent = value.shareIntro;
        document.querySelector('#priorityShareChoices').setAttribute('aria-label', value.shareIntro);
        document.querySelector('[data-priority-share-label="compass"]').textContent = value.shareCompass;
        document.querySelector('[data-priority-share-label="parties"]').textContent = value.shareParties;
        document.querySelector('#priorityShareClose').textContent = '×';
        document.querySelector('#priorityShareClose').title = value.shareClose;
        document.querySelector('#priorityShareClose').setAttribute('aria-label', value.shareClose);
        document.querySelector('[data-priority-share-action="native"]').textContent = value.shareNative;
        document.querySelector('[data-priority-share-action="link"]').textContent = value.shareLink;
        document.querySelector('[data-priority-share-action="download"]').textContent = value.shareDownload;
        const copyLink = document.querySelector('[data-priority-social="copy"]');
        copyLink.title = value.shareCopyLink;
        copyLink.setAttribute('aria-label', value.shareCopyLink);
    }

    function renderIntro() {
        applyCopy();
        document.querySelector('#priorityTitle').textContent = questionnaire.title;
        document.querySelector('#priorityIntroText').textContent = questionnaire.intro;
        const progressed = Object.keys(state.answers).length + state.skipped.length > 0;
        document.querySelector('#priorityStart').textContent = progressed ? copy().resume : copy().start;
        showOnly('priorityIntro');
    }

    function start() {
        track('priority_questionnaire_start', { resumed: Object.keys(state.answers).length > 0 });
        renderQuestion();
    }

    function renderQuestion() {
        const question = questionnaire.questions[currentIndex];
        const saved = state.answers[question.key];
        document.querySelector('#priorityTheme').textContent = question.themeLabel;
        document.querySelector('#priorityCounter').textContent = format(copy().question, {
            current: currentIndex + 1,
            total: questionnaire.questions.length
        });
        setWidthClass(document.querySelector('#priorityProgressBar'), (currentIndex + 1) * 100 / questionnaire.questions.length);
        document.querySelector('#priorityPrompt').textContent = question.prompt;
        document.querySelector('#priorityContextText').textContent = question.context;
        document.querySelector('#priorityImportant').checked = Boolean(saved?.important);
        document.querySelector('#priorityPrevious').disabled = currentIndex === 0;
        renderAnswers(saved?.value);
        renderSources(question.sources);
        showOnly('priorityQuestionStage');
        document.querySelector('#priorityPrompt').focus({ preventScroll: true });
        state.current = currentIndex;
        persistState();
    }

    function renderAnswers(selectedValue) {
        const container = document.querySelector('#priorityAnswerChoices');
        container.replaceChildren();
        copy().answers.forEach((label, index) => {
            const value = index - 2;
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'priority-answer-button';
            button.dataset.value = value;
            button.setAttribute('aria-pressed', String(value === selectedValue));
            button.innerHTML = `<span>${escapeHtml(label)}</span><i aria-hidden="true"></i>`;
            button.addEventListener('click', () => answer(value));
            container.append(button);
        });
    }

    function renderSources(sources) {
        const container = document.querySelector('#prioritySources');
        container.replaceChildren();
        sources.forEach(source => {
            const link = document.createElement('a');
            link.href = source.url;
            link.target = '_blank';
            link.rel = 'noopener noreferrer';
            link.textContent = `${copy().source}: ${source.label}`;
            container.append(link);
        });
    }

    function answer(value) {
        const question = questionnaire.questions[currentIndex];
        state.answers[question.key] = {
            value,
            important: document.querySelector('#priorityImportant').checked
        };
        state.skipped = state.skipped.filter(key => key !== question.key);
        persistState();
        track('priority_question_answered', {
            question_number: currentIndex + 1,
            important: state.answers[question.key].important
        });
        window.setTimeout(next, 120);
    }

    function skip() {
        const question = questionnaire.questions[currentIndex];
        delete state.answers[question.key];
        if (!state.skipped.includes(question.key)) state.skipped.push(question.key);
        persistState();
        track('priority_question_skipped', { question_number: currentIndex + 1 });
        next();
    }

    function next() {
        if (currentIndex < questionnaire.questions.length - 1) {
            currentIndex++;
            renderQuestion();
            return;
        }
        finish();
    }

    function previous() {
        if (currentIndex === 0) return;
        currentIndex--;
        renderQuestion();
    }

    async function finish() {
        const answers = Object.entries(state.answers).map(([questionKey, answer]) => ({ questionKey, ...answer }));
        if (!answers.length) {
            showError(copy().needAnswer);
            return;
        }
        showOnly('priorityLoading');
        try {
            const options = await window.FhemniAuth.withCsrf({
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ language: currentLocale, answers })
            });
            const response = await fetch('/api/catalog/questionnaires/current/profile', options);
            if (!response.ok) throw new Error(`Profile request failed: ${response.status}`);
            renderResults(await response.json());
            track('priority_questionnaire_complete', {
                answered_count: answers.length,
                skipped_count: questionnaire.questions.length - answers.length
            });
        } catch (_) {
            showError(copy().error);
        }
    }

    function renderResults(profile) {
        document.querySelector('#priorityCompletion').textContent = format(copy().completion, {
            answered: profile.answeredCount,
            total: profile.questionCount
        });
        currentPriorities = profile.priorities;
        renderThemeResults(profile.priorities.slice(0, 4));
        showOnly('priorityResults');
        window.scrollTo({ top: 0, behavior: 'smooth' });
        loadCompass();
    }

    function renderThemeResults(priorities) {
        const container = document.querySelector('#priorityThemeResults');
        container.replaceChildren();
        priorities.forEach((priority, index) => {
            const article = document.createElement('article');
            article.className = 'priority-theme-result';
            article.innerHTML = `
                <div class="priority-result-rank">${index + 1}</div>
                <div><h3>${escapeHtml(priority.label)}</h3>
                <div class="priority-result-bar"><span class="${widthClass(priority.score)}"></span></div>
                <p>${escapeHtml(format(copy().strength, { score: priority.score }))} · ${escapeHtml(format(copy().importantCount, { count: priority.importantCount }))}</p></div>`;
            container.append(article);
        });
    }

    async function loadCompass() {
        const section = document.querySelector('#priorityCompassSection');
        const loading = document.querySelector('#priorityCompassLoading');
        const results = document.querySelector('#priorityCompassResults');
        const c = copy();

        document.querySelector('#priorityCompassTitle').textContent = c.compassTitle;
        document.querySelector('#priorityCompassIntro').textContent = c.compassIntro;
        document.querySelector('#priorityCompassLoadingText').textContent = c.compassLoading;
        document.querySelector('#priorityCompassDisclaimer').textContent = c.compassDisclaimer;

        section.hidden = false;
        renderPriorityRadar(currentPriorities);
        loading.hidden = false;
        results.replaceChildren();

        const answers = Object.entries(state.answers).map(([questionKey, a]) => ({ questionKey, ...a }));
        try {
            const options = await window.FhemniAuth.withCsrf({
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ language: currentLocale, answers })
            });
            const response = await fetch('/api/catalog/questionnaires/current/compass', options);
            if (!response.ok) throw new Error(`Compass request failed: ${response.status}`);
            const compass = await response.json();
            loading.hidden = true;

            if (compass.totalPositionsReviewed > 0) {
                document.querySelector('#priorityCompassIntro').textContent +=
                    ' ' + format(c.compassReviewed, { count: compass.totalPositionsReviewed });
            }

            if (!compass.parties || compass.parties.length === 0) {
                results.innerHTML = `<p class="priority-compass-empty">${escapeHtml(c.compassEmpty)}</p>`;
                return;
            }
            renderCompass(compass, c);
        } catch (err) {
            console.error('Compass error:', err);
            loading.hidden = true;
            section.hidden = true;
        }
    }

    let compassData = null;
    let currentPriorities = [];

    function renderCompass(compass, c) {
        compassData = compass;
        const container = document.querySelector('#compassBarChart');
        container.replaceChildren();
        document.querySelector('#priorityCompassResults').replaceChildren();

        const parties = compass.parties.filter(p => p.coveredQuestions > 0).slice(0, 5);
        if (!parties.length) return;

        const list = document.createElement('div');
        list.className = 'compass-match-list';
        parties.slice(0, 3).forEach((party, i) => {
            list.append(createCompassMatchRow(party, i, c));
        });
        container.append(list);

        const remaining = document.querySelector('#priorityCompassRemaining');
        const remainingList = document.querySelector('#priorityCompassMore');
        remainingList.replaceChildren();
        const otherParties = parties.slice(3);
        remaining.hidden = !otherParties.length;
        otherParties.forEach((party, i) => remainingList.append(createCompassMatchRow(party, i + 3, c)));
        initCompassShare(compass, c);
    }

    function createCompassMatchRow(party, index, c) {
            const item = document.createElement('button');
            item.type = 'button';
            item.className = 'compass-match-row' + (index === 0 ? ' compass-match-top' : '');
            item.classList.add(partyAccentClass(party.code));
            item.addEventListener('click', () => showPartyDetail(party, c));

            const rank = document.createElement('span');
            rank.className = 'compass-match-rank';
            rank.textContent = index + 1;

            const logo = document.createElement('img');
            logo.className = 'compass-match-logo';
            logo.src = party.symbolAsset;
            logo.alt = '';

            const identity = document.createElement('span');
            identity.className = 'compass-match-identity';
            const label = document.createElement('strong');
            label.className = 'compass-match-name';
            label.textContent = party.name;
            const coverage = document.createElement('small');
            coverage.className = 'compass-match-coverage';
            coverage.textContent = format(c.compassCoverage, {
                covered: party.coveredQuestions,
                total: party.answeredQuestions
            });
            identity.append(label, coverage);

            const pct = document.createElement('span');
            pct.className = 'compass-match-pct';
            pct.textContent = party.compatibility + '%';

            const bar = document.createElement('span');
            bar.className = 'compass-match-bar';
            const fill = document.createElement('span');
            fill.className = widthClass(party.compatibility);
            bar.append(fill);

            item.setAttribute('aria-label', `${party.name}, ${party.compatibility}%, ${coverage.textContent}`);
            item.append(rank, logo, identity, pct, bar);
            return item;
    }

    function initCompassShare(compass, c) {
        const btn = document.querySelector('#priorityCompassShare');
        btn.hidden = false;
        btn.onclick = openShareDialog;
        prepareShareAssets(compass, c);
    }

    let shareAssetGeneration = 0;
    let shareAssetPromises = new Map();
    const shareAssets = new Map();
    const publicShareUrls = new Map();
    let selectedPublicShare;
    let selectedShareKind = 'compass';

    function prepareShareAssets(compass, c) {
        const generation = ++shareAssetGeneration;
        clearShareAssets();
        const promises = new Map();
        for (const kind of ['compass', 'parties']) {
            const promise = buildShareAsset(kind, compass, c, generation)
                .then(asset => {
                    if (generation !== shareAssetGeneration) {
                        URL.revokeObjectURL(asset.previewUrl);
                        throw new Error('Share result was replaced');
                    }
                    shareAssets.set(kind, asset);
                    return asset;
                });
            promise.catch(() => {});
            promises.set(kind, promise);
        }
        shareAssetPromises = promises;
    }

    function clearShareAssets() {
        shareAssets.forEach(asset => URL.revokeObjectURL(asset.previewUrl));
        shareAssets.clear();
        publicShareUrls.clear();
        selectedPublicShare = undefined;
        shareAssetPromises = new Map();
    }

    async function buildShareAsset(kind, compass, c, generation) {
        if (!window.htmlToImage?.toBlob) throw new Error('Image renderer is unavailable');
        await document.fonts?.ready;
        if (generation !== shareAssetGeneration) throw new Error('Share result was replaced');

        const renderRoot = document.createElement('div');
        renderRoot.className = 'priority-share-render-root';
        const card = createShareCard(kind, compass, c);
        const story = createStoryCard(kind, compass, c);
        renderRoot.append(card, story);
        document.body.append(renderRoot);

        try {
            await Promise.all([waitForShareImages(card), waitForShareImages(story)]);
            const [blob, storyBlob] = await Promise.all([
                window.htmlToImage.toBlob(card, {
                    width: 1080,
                    height: 566,
                    canvasWidth: 1200,
                    canvasHeight: 630,
                    // Produce a 2400×1260 retina card in one browser render. Keeping
                    // the final social asset at this size avoids a second lossy-looking
                    // enlargement in high-density previews and preserves Arabic type.
                    pixelRatio: 2,
                    cacheBust: true,
                    backgroundColor: '#fffdf7'
                }),
                window.htmlToImage.toBlob(story, {
                    width: 1080,
                    height: 1920,
                    pixelRatio: 1,
                    cacheBust: true,
                    backgroundColor: '#fffdf7'
                })
            ]);
            if (!blob || !storyBlob) throw new Error('Image renderer returned no data');
            const filename = kind === 'compass' ? 'fhemni-priority-compass.png' : 'fhemni-party-matches.png';
            const storyFilename = kind === 'compass' ? 'fhemni-priority-compass-story.png' : 'fhemni-party-matches-story.png';
            return {
                blob,
                file: new File([storyBlob], storyFilename, { type: 'image/png' }),
                filename,
                previewUrl: URL.createObjectURL(blob),
                text: shareText(kind, compass, c),
                title: kind === 'compass' ? c.shareCompassTitle : c.sharePartiesTitle
            };
        } finally {
            renderRoot.remove();
        }
    }

    function createShareCard(kind, compass, c) {
        const card = document.createElement('article');
        card.className = `priority-share-card priority-share-card-${kind}`;
        card.lang = currentLocale;
        card.dir = currentLocale === 'ar' ? 'rtl' : 'ltr';

        const header = document.createElement('header');
        header.className = 'priority-share-card-header';
        const logo = document.createElement('img');
        logo.src = '/assets/brand/fhemni-logo.png';
        logo.alt = '';
        const domain = document.createElement('span');
        domain.textContent = 'fhemni.ma';
        header.append(logo, domain);

        const heading = document.createElement('h2');
        heading.textContent = kind === 'compass' ? c.shareCompassTitle : c.sharePartiesTitle;
        card.append(header, heading);
        card.append(kind === 'compass' ? createCompassShareBody(c) : createPartyShareBody(compass, c));

        const footer = document.createElement('footer');
        footer.className = 'priority-share-card-footer';
        const note = document.createElement('span');
        note.textContent = c.shareCardNote;
        const cta = document.createElement('strong');
        cta.textContent = c.shareCardCta;
        footer.append(note, cta);
        card.append(footer);
        return card;
    }

    function createStoryCard(kind, compass, c) {
        const card = document.createElement('article');
        card.className = `priority-story-card priority-story-card-${kind}`;
        card.lang = currentLocale;
        card.dir = currentLocale === 'ar' ? 'rtl' : 'ltr';

        const header = document.createElement('header');
        header.className = 'priority-story-header';
        const logo = document.createElement('img');
        logo.src = '/assets/brand/fhemni-logo.png';
        logo.alt = '';
        const domain = document.createElement('span');
        domain.textContent = 'fhemni.ma';
        header.append(logo, domain);

        const heading = document.createElement('h2');
        heading.textContent = kind === 'compass' ? c.shareCompassTitle : c.sharePartiesTitle;

        const result = document.createElement('div');
        result.className = 'priority-story-result';
        if (kind === 'compass') {
            result.append(createStoryPriorityList(c));
            const visual = document.createElement('div');
            visual.className = 'priority-story-radar-shell';
            const radar = createShareRadar('priority-story-radar', 1.18, 140, 58, 1.08, 14);
            if (radar) visual.append(radar);
            result.append(visual);
        } else {
            const parties = createPartyShareBody(compass, c);
            parties.classList.add('priority-story-party-list');
            result.append(parties);
        }

        const note = document.createElement('p');
        note.className = 'priority-story-note';
        note.textContent = c.shareCardNote;

        const callout = document.createElement('section');
        callout.className = 'priority-story-callout';
        const question = document.createElement('h3');
        question.textContent = c.storyQuestion;
        const subtitle = document.createElement('p');
        subtitle.textContent = c.storySubtitle;
        const cta = document.createElement('strong');
        cta.textContent = c.storyCta;
        callout.append(question, subtitle, cta);

        card.append(header, heading, result, note, callout);
        return card;
    }

    function createStoryPriorityList(c) {
        const list = document.createElement('ol');
        list.className = 'priority-story-priority-list';
        currentPriorities.slice(0, 3).forEach(priority => {
            const item = document.createElement('li');
            const label = document.createElement('strong');
            label.textContent = priority.label;
            const score = document.createElement('span');
            score.textContent = format(c.strength, { score: priority.score });
            item.append(label, score);
            list.append(item);
        });
        return list;
    }

    function createCompassShareBody(c) {
        const body = document.createElement('div');
        body.className = 'priority-share-card-body priority-share-card-compass-body';
        const radar = createShareRadar('priority-share-card-radar', 1.3, 148, 50, 1.13, 10);
        if (radar) body.append(radar);

        const list = document.createElement('ol');
        list.className = 'priority-share-priority-list';
        currentPriorities.slice(0, 3).forEach(priority => {
            const item = document.createElement('li');
            const label = document.createElement('strong');
            label.textContent = priority.label;
            const score = document.createElement('span');
            score.textContent = format(c.strength, { score: priority.score });
            item.append(label, score);
            list.append(item);
        });
        body.append(list);
        return body;
    }

    function createShareRadar(className, geometryFactor, labelWidth, labelHeight, labelFactor, padding) {
        const radar = document.querySelector('#priorityRadarChart svg')?.cloneNode(true);
        if (!radar) return null;
        radar.removeAttribute('role');
        radar.removeAttribute('aria-label');
        radar.classList.add(className);
        expandShareRadarGeometry(radar, geometryFactor);
        widenShareRadarLabels(radar, labelWidth, labelHeight);
        spreadShareRadarLabels(radar, labelFactor);
        fitShareRadarViewBox(radar, padding);
        radar.querySelectorAll('.compass-profile-grid').forEach(grid => {
            grid.setAttribute('fill', 'none');
            grid.setAttribute('stroke', 'rgba(19,44,43,.17)');
            grid.setAttribute('stroke-width', '1.2');
            grid.setAttribute('stroke-dasharray', grid.classList.contains('compass-profile-grid-outer') ? 'none' : '3 4');
        });
        radar.querySelectorAll('.compass-profile-axis').forEach(axis => {
            axis.setAttribute('stroke', 'rgba(19,44,43,.15)');
            axis.setAttribute('stroke-width', '1.1');
        });
        const shape = radar.querySelector('.compass-profile-shape');
        shape?.setAttribute('fill', 'rgba(15,81,69,.26)');
        shape?.setAttribute('stroke', '#0b4f49');
        shape?.setAttribute('stroke-width', '4');
        shape?.setAttribute('stroke-linejoin', 'round');
        radar.querySelectorAll('.compass-profile-dot').forEach(dot => {
            dot.setAttribute('fill', '#e86f3c');
            dot.setAttribute('stroke', '#fffdf7');
            dot.setAttribute('stroke-width', '2.5');
        });
        return radar;
    }

    function expandShareRadarGeometry(radar, factor) {
        const viewBox = (radar.getAttribute('viewBox') || '').trim().split(/\s+/).map(Number);
        if (viewBox.length !== 4 || viewBox.some(value => !Number.isFinite(value))) return;
        const [x, y, width, height] = viewBox;
        const cx = x + width / 2;
        const cy = y + height / 2;
        const scaledX = value => cx + (Number(value) - cx) * factor;
        const scaledY = value => cy + (Number(value) - cy) * factor;

        radar.querySelectorAll('.compass-profile-grid, .compass-profile-shape').forEach(polygon => {
            const points = (polygon.getAttribute('points') || '').trim().split(/\s+/).map(point => {
                const [px, py] = point.split(',').map(Number);
                return `${scaledX(px)},${scaledY(py)}`;
            });
            polygon.setAttribute('points', points.join(' '));
        });
        radar.querySelectorAll('.compass-profile-axis').forEach(axis => {
            axis.setAttribute('x1', scaledX(axis.getAttribute('x1')));
            axis.setAttribute('y1', scaledY(axis.getAttribute('y1')));
            axis.setAttribute('x2', scaledX(axis.getAttribute('x2')));
            axis.setAttribute('y2', scaledY(axis.getAttribute('y2')));
        });
        radar.querySelectorAll('.compass-profile-dot').forEach(dot => {
            dot.setAttribute('cx', scaledX(dot.getAttribute('cx')));
            dot.setAttribute('cy', scaledY(dot.getAttribute('cy')));
        });
    }

    function fitShareRadarViewBox(radar, padding) {
        const bounds = [];
        radar.querySelectorAll('polygon').forEach(polygon => {
            (polygon.getAttribute('points') || '').trim().split(/\s+/).forEach(point => {
                const [x, y] = point.split(',').map(Number);
                if (Number.isFinite(x) && Number.isFinite(y)) bounds.push({ x, y, width: 0, height: 0 });
            });
        });
        radar.querySelectorAll('line').forEach(line => {
            const x1 = Number(line.getAttribute('x1'));
            const y1 = Number(line.getAttribute('y1'));
            const x2 = Number(line.getAttribute('x2'));
            const y2 = Number(line.getAttribute('y2'));
            if ([x1, y1, x2, y2].every(Number.isFinite)) {
                bounds.push({ x: Math.min(x1, x2), y: Math.min(y1, y2), width: Math.abs(x2 - x1), height: Math.abs(y2 - y1) });
            }
        });
        radar.querySelectorAll('circle').forEach(circle => {
            const cx = Number(circle.getAttribute('cx'));
            const cy = Number(circle.getAttribute('cy'));
            const radius = Number(circle.getAttribute('r')) || 0;
            if (Number.isFinite(cx) && Number.isFinite(cy)) {
                bounds.push({ x: cx - radius, y: cy - radius, width: radius * 2, height: radius * 2 });
            }
        });
        radar.querySelectorAll('foreignObject').forEach(label => {
            const x = Number(label.getAttribute('x'));
            const y = Number(label.getAttribute('y'));
            const width = Number(label.getAttribute('width'));
            const height = Number(label.getAttribute('height'));
            if ([x, y, width, height].every(Number.isFinite)) bounds.push({ x, y, width, height });
        });
        if (!bounds.length) return;
        const left = Math.min(...bounds.map(bound => bound.x)) - padding;
        const top = Math.min(...bounds.map(bound => bound.y)) - padding;
        const right = Math.max(...bounds.map(bound => bound.x + bound.width)) + padding;
        const bottom = Math.max(...bounds.map(bound => bound.y + bound.height)) + padding;
        radar.setAttribute('viewBox', `${left} ${top} ${right - left} ${bottom - top}`);
    }

    function spreadShareRadarLabels(radar, factor) {
        const viewBox = (radar.getAttribute('viewBox') || '').trim().split(/\s+/).map(Number);
        if (viewBox.length !== 4 || viewBox.some(value => !Number.isFinite(value))) return;
        const [x, y, width, height] = viewBox;
        const cx = x + width / 2;
        const cy = y + height / 2;
        radar.querySelectorAll('foreignObject').forEach(label => {
            const labelX = Number(label.getAttribute('x'));
            const labelY = Number(label.getAttribute('y'));
            const labelWidth = Number(label.getAttribute('width'));
            const labelHeight = Number(label.getAttribute('height'));
            if (![labelX, labelY, labelWidth, labelHeight].every(Number.isFinite)) return;
            const labelCx = labelX + labelWidth / 2;
            const labelCy = labelY + labelHeight / 2;
            label.setAttribute('x', cx + (labelCx - cx) * factor - labelWidth / 2);
            label.setAttribute('y', cy + (labelCy - cy) * factor - labelHeight / 2);
        });
    }

    function widenShareRadarLabels(radar, minimumWidth, minimumHeight) {
        radar.querySelectorAll('foreignObject').forEach(label => {
            const x = Number(label.getAttribute('x'));
            const y = Number(label.getAttribute('y'));
            const width = Number(label.getAttribute('width'));
            const height = Number(label.getAttribute('height'));
            if (![x, y, width, height].every(Number.isFinite)) return;
            const nextWidth = Math.max(width, minimumWidth);
            const nextHeight = Math.max(height, minimumHeight);
            label.setAttribute('x', x - (nextWidth - width) / 2);
            label.setAttribute('y', y - (nextHeight - height) / 2);
            label.setAttribute('width', nextWidth);
            label.setAttribute('height', nextHeight);
        });
    }

    function createPartyShareBody(compass, c) {
        const list = document.createElement('ol');
        list.className = 'priority-share-party-list';
        compass.parties.filter(party => party.coveredQuestions > 0).slice(0, 3).forEach((party, index) => {
            const item = document.createElement('li');
            item.classList.add(partyAccentClass(party.code));
            const rank = document.createElement('span');
            rank.className = 'priority-share-party-rank';
            rank.textContent = index + 1;
            const logo = document.createElement('img');
            logo.src = party.symbolAsset;
            logo.alt = '';
            const identity = document.createElement('span');
            identity.className = 'priority-share-party-identity';
            const name = document.createElement('strong');
            name.textContent = party.name;
            const coverage = document.createElement('small');
            coverage.textContent = format(c.compassCoverage, {
                covered: party.coveredQuestions,
                total: party.answeredQuestions
            });
            identity.append(name, coverage);
            const percentage = document.createElement('span');
            percentage.className = 'priority-share-party-percentage';
            percentage.textContent = `${party.compatibility}%`;
            const bar = document.createElement('span');
            bar.className = 'priority-share-party-bar';
            const fill = document.createElement('span');
            fill.className = widthClass(party.compatibility);
            bar.append(fill);
            item.append(rank, logo, identity, percentage, bar);
            list.append(item);
        });
        return list;
    }

    function widthClass(value) {
        return `priority-width-${Math.max(0, Math.min(100, Math.round(Number(value) || 0)))}`;
    }

    function setWidthClass(element, value) {
        for (const name of [...element.classList]) {
            if (name.startsWith('priority-width-')) element.classList.remove(name);
        }
        element.classList.add(widthClass(value));
    }

    function partyAccentClass(code) {
        const supported = new Set(['rni', 'pam', 'pi', 'pjd', 'usfp', 'pps', 'mp', 'fgd', 'uc', 'ffd', 'mds', 'pud']);
        const normalized = String(code || '').toLowerCase();
        return supported.has(normalized) ? `party-accent-${normalized}` : 'party-accent-default';
    }

    function waitForShareImages(container) {
        return Promise.all([...container.querySelectorAll('img')].map(image => {
            if (image.complete && image.naturalWidth > 0) return Promise.resolve();
            return new Promise(resolve => {
                image.addEventListener('load', resolve, { once: true });
                image.addEventListener('error', resolve, { once: true });
            });
        }));
    }

    function shareText(kind, compass, c) {
        const parties = compass.parties.filter(party => party.coveredQuestions > 0).slice(0, 3);
        const items = kind === 'compass'
            ? currentPriorities.slice(0, 3).map(priority => priority.label).join(currentLocale === 'ar' ? '، ' : ', ')
            : parties.map((party, index) => `${index + 1}. ${party.name} — ${party.compatibility}%`).join('\n');
        return format(kind === 'compass' ? c.shareCompassText : c.sharePartiesText, { items });
    }

    function openShareDialog() {
        const dialog = document.querySelector('#priorityShareDialog');
        dialog.returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
        if (!dialog.open) dialog.showModal();
        selectShareKind(selectedShareKind);
    }

    function closeShareDialog() {
        const dialog = document.querySelector('#priorityShareDialog');
        if (dialog.open) dialog.close();
    }

    async function selectShareKind(kind) {
        selectedShareKind = kind;
        document.querySelectorAll('[data-priority-share-kind]').forEach(button => {
            button.setAttribute('aria-pressed', String(button.dataset.priorityShareKind === kind));
        });
        setShareControls(null);
        const preview = document.querySelector('#prioritySharePreview');
        const loader = document.querySelector('#priorityShareLoader');
        const previewContainer = document.querySelector('.priority-share-preview');
        const status = document.querySelector('#priorityShareStatus');
        closeSocialShareMenu(true);
        preview.hidden = true;
        loader.hidden = false;
        previewContainer.setAttribute('aria-busy', 'true');
        status.classList.remove('error');
        status.textContent = copy().sharePreparing;

        try {
            const asset = shareAssets.get(kind) || await shareAssetPromises.get(kind);
            if (selectedShareKind !== kind) return;
            preview.src = asset.previewUrl;
            preview.alt = kind === 'compass' ? copy().sharePreviewCompass : copy().sharePreviewParties;
            preview.hidden = false;
            loader.hidden = true;
            previewContainer.setAttribute('aria-busy', 'false');
            status.textContent = '';
            setShareControls(asset);
        } catch (_) {
            if (selectedShareKind !== kind) return;
            loader.hidden = true;
            previewContainer.setAttribute('aria-busy', 'false');
            status.textContent = copy().shareError;
            status.classList.add('error');
        }
    }

    function setShareControls(asset) {
        const nativeButton = document.querySelector('#priorityShareNative');
        const linkButton = document.querySelector('#priorityShareLink');
        const download = document.querySelector('#priorityShareDownload');
        const canShareFile = Boolean(asset && isMobileShareDevice()
            && navigator.share && navigator.canShare?.({ files: [asset.file] }));

        nativeButton.hidden = !canShareFile;
        nativeButton.disabled = !canShareFile;
        linkButton.disabled = !asset;
        download.classList.toggle('disabled', !asset);
        download.setAttribute('aria-disabled', String(!asset));
        download.href = asset?.previewUrl || '#';
        download.download = asset?.filename || '';
    }

    async function shareSelectedAsset() {
        const asset = shareAssets.get(selectedShareKind);
        if (!asset || !navigator.canShare?.({ files: [asset.file] })) return;
        try {
            await navigator.share({ title: asset.title, text: asset.text, files: [asset.file] });
            track('priority_result_shared', { kind: selectedShareKind, method: 'native' });
        } catch (error) {
            if (error?.name !== 'AbortError') showShareError();
        }
    }

    async function shareSelectedPublicLink() {
        const kind = selectedShareKind;
        const asset = shareAssets.get(kind);
        if (!asset) return;
        const button = document.querySelector('#priorityShareLink');
        const status = document.querySelector('#priorityShareStatus');
        button.disabled = true;
        status.classList.remove('error');
        status.textContent = copy().sharePublishing;
        try {
            const url = publicShareUrls.get(kind) || await publishShareAsset(kind, asset);
            publicShareUrls.set(kind, url);
            if (selectedShareKind !== kind) return;
            const text = asset.text.replace(/https:\/\/fhemni\.ma\/priorities/g, '').trim();
            selectedPublicShare = { kind, title: asset.title, text, url };
            if (isMobileShareDevice() && navigator.share) {
                await navigator.share({ title: asset.title, text, url });
                status.textContent = '';
                track('priority_result_shared', { kind, method: 'public_link_native' });
            } else {
                const menu = document.querySelector('#priorityShareLinkMenu');
                document.querySelector('#prioritySocialShares').hidden = false;
                menu.classList.add('is-open');
                button.setAttribute('aria-expanded', 'true');
                status.textContent = copy().shareChooseNetwork;
                track('priority_result_shared', { kind, method: 'public_link_options' });
            }
        } catch (error) {
            if (error?.name === 'AbortError') status.textContent = '';
            else showShareError();
        } finally {
            if (selectedShareKind === kind) button.disabled = false;
        }
    }

    async function publishShareAsset(kind, asset) {
        const endpoint = new URL('/api/catalog/questionnaires/current/shares', window.location.origin);
        endpoint.searchParams.set('kind', kind);
        endpoint.searchParams.set('language', currentLocale);
        const options = await window.FhemniAuth.withCsrf({
            method: 'POST',
            headers: { 'Content-Type': 'image/png' },
            body: asset.blob
        });
        const response = await fetch(endpoint, options);
        if (!response.ok) throw new Error(`Share link request failed: ${response.status}`);
        const created = await response.json();
        return new URL(created.url, window.location.origin).href;
    }

    function isMobileShareDevice() {
        return navigator.maxTouchPoints > 0 && window.matchMedia('(pointer: coarse)').matches;
    }

    function closeSocialShareMenu(reset = false) {
        const menu = document.querySelector('#priorityShareLinkMenu');
        const button = document.querySelector('#priorityShareLink');
        const shares = document.querySelector('#prioritySocialShares');
        menu?.classList.remove('is-open');
        button?.setAttribute('aria-expanded', 'false');
        if (reset && shares) shares.hidden = true;
    }

    function shareToSocialNetwork(network) {
        if (!selectedPublicShare) return;
        const { kind, text, url } = selectedPublicShare;
        if (network === 'copy') {
            const status = document.querySelector('#priorityShareStatus');
            navigator.clipboard.writeText(url).then(() => {
                status.classList.remove('error');
                status.textContent = copy().shareLinkCopied;
                closeSocialShareMenu();
                track('priority_result_shared', { kind, method: 'public_link_copy' });
            }).catch(() => {
                status.classList.add('error');
                status.textContent = copy().shareLinkCopyError;
            });
            return;
        }
        const encodedUrl = encodeURIComponent(url);
        const encodedText = encodeURIComponent(text.trim());
        const destinations = {
            facebook: `https://www.facebook.com/sharer/sharer.php?u=${encodedUrl}`,
            whatsapp: `https://wa.me/?text=${encodedText}%0A%0A${encodedUrl}`,
            x: `https://twitter.com/intent/tweet?text=${encodedText}&url=${encodedUrl}`,
            linkedin: `https://www.linkedin.com/sharing/share-offsite/?url=${encodedUrl}`
        };
        const destination = destinations[network];
        if (!destination) return;
        const captionCopy = ['facebook', 'linkedin'].includes(network) && navigator.clipboard?.writeText
            ? navigator.clipboard.writeText(text.trim())
            : null;
        window.open(destination, '_blank', 'noopener,noreferrer,width=720,height=680');
        captionCopy?.then(() => {
            const status = document.querySelector('#priorityShareStatus');
            status.classList.remove('error');
            status.textContent = copy().shareSocialTextCopied;
        }).catch(() => {});
        track('priority_result_shared', { kind, method: `public_link_${network}` });
    }

    function showShareError() {
        const status = document.querySelector('#priorityShareStatus');
        status.classList.add('error');
        status.textContent = copy().shareError;
    }

    function renderPriorityRadar(priorities) {
        const container = document.querySelector('#priorityRadarChart');
        container.replaceChildren();
        if (!priorities.length) return;

        const width = Math.min(Math.max(container.clientWidth || 360, 320), 580);
        const height = Math.round(width * .9);
        const cx = width / 2;
        const cy = height / 2;
        const radius = Math.min(width * .28, height * .33);
        const labelRadius = radius + Math.min(44, width * .105);
        const angleSlice = 2 * Math.PI / priorities.length;
        const radarMaximum = Math.max(...priorities.map(priority => Number(priority.score) || 0), 1);
        const scaledRadarRadius = score => radius * Math.max(0, Math.min(1, (Number(score) || 0) / radarMaximum));
        const ns = 'http://www.w3.org/2000/svg';
        const svg = document.createElementNS(ns, 'svg');
        svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
        svg.setAttribute('class', 'compass-profile-radar');
        svg.setAttribute('role', 'img');
        svg.setAttribute('aria-label', copy().themesTitle);

        [50, 100].forEach(level => {
            const points = priorities.map((_, i) => {
                const angle = angleSlice * i - Math.PI / 2;
                const r = radius * level / 100;
                return `${cx + Math.cos(angle) * r},${cy + Math.sin(angle) * r}`;
            }).join(' ');
            const polygon = document.createElementNS(ns, 'polygon');
            polygon.setAttribute('points', points);
            polygon.setAttribute('class', level === 100 ? 'compass-profile-grid compass-profile-grid-outer' : 'compass-profile-grid');
            svg.append(polygon);
        });

        priorities.forEach((priority, i) => {
            const angle = angleSlice * i - Math.PI / 2;
            const axis = document.createElementNS(ns, 'line');
            axis.setAttribute('x1', cx);
            axis.setAttribute('y1', cy);
            axis.setAttribute('x2', cx + Math.cos(angle) * radius);
            axis.setAttribute('y2', cy + Math.sin(angle) * radius);
            axis.setAttribute('class', 'compass-profile-axis');
            svg.append(axis);

            const labelWidth = width < 400 ? 92 : 124;
            const labelHeight = width < 400 ? 42 : 46;
            const label = document.createElementNS(ns, 'foreignObject');
            label.setAttribute('x', cx + Math.cos(angle) * labelRadius - labelWidth / 2);
            label.setAttribute('y', cy + Math.sin(angle) * labelRadius - labelHeight / 2);
            label.setAttribute('width', labelWidth);
            label.setAttribute('height', labelHeight);
            const text = document.createElement('div');
            text.setAttribute('xmlns', 'http://www.w3.org/1999/xhtml');
            text.className = 'compass-profile-label';
            text.textContent = priority.label;
            label.append(text);
            svg.append(label);
        });

        const profilePoints = priorities.map((priority, i) => {
            const angle = angleSlice * i - Math.PI / 2;
            const r = scaledRadarRadius(priority.score);
            return `${cx + Math.cos(angle) * r},${cy + Math.sin(angle) * r}`;
        }).join(' ');
        const profile = document.createElementNS(ns, 'polygon');
        profile.setAttribute('points', profilePoints);
        profile.setAttribute('class', 'compass-profile-shape');
        svg.append(profile);

        priorities.forEach((priority, i) => {
            const angle = angleSlice * i - Math.PI / 2;
            const r = scaledRadarRadius(priority.score);
            const dot = document.createElementNS(ns, 'circle');
            dot.setAttribute('cx', cx + Math.cos(angle) * r);
            dot.setAttribute('cy', cy + Math.sin(angle) * r);
            dot.setAttribute('r', 4.5);
            dot.setAttribute('class', 'compass-profile-dot');
            svg.append(dot);
        });

        container.append(svg);
    }

    let activePartyCode = null;

    function showPartyDetail(party, c) {
        const container = document.querySelector('#priorityCompassResults');

        if (activePartyCode === party.code) {
            container.replaceChildren();
            activePartyCode = null;
            document.querySelectorAll('.compass-match-row').forEach(el => el.classList.remove('compass-match-active'));
            return;
        }

        container.replaceChildren();
        activePartyCode = party.code;
        document.querySelectorAll('.compass-match-row').forEach(el => el.classList.remove('compass-match-active'));
        const clickedItem = [...document.querySelectorAll('.compass-match-row')].find(el => el.querySelector('.compass-match-name')?.textContent === party.name);
        if (clickedItem) clickedItem.classList.add('compass-match-active');

        if (!party.questions || !party.questions.length) return;

        const header = document.createElement('h3');
        header.className = 'compass-detail-title';
        header.textContent = party.name + ' — ' + party.compatibility + '%';
        container.append(header);

        for (const q of party.questions) {
            const row = document.createElement('div');
            const cls = q.alignment >= 75 ? 'compass-q-agree' : q.alignment <= 25 ? 'compass-q-disagree' : 'compass-q-mixed';
            row.className = 'compass-q-row ' + cls;
            const icon = q.alignment >= 75 ? '✓' : q.alignment <= 25 ? '✗' : '~';
            row.innerHTML = `<span class="compass-q-icon">${icon}</span><span class="compass-q-prompt">${escapeHtml(q.prompt)}${q.important ? ' ★' : ''}</span><span class="compass-q-align">${q.alignment}%</span>`;
            container.append(row);
        }
        container.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    function restart() {
        window.localStorage.removeItem(storageKey(questionnaire.version));
        state = emptyState(questionnaire.version);
        currentIndex = 0;
        renderIntro();
    }

    function showError(message) {
        applyCopy();
        document.querySelector('#priorityErrorText').textContent = message;
        showOnly('priorityError');
    }

    function restoreState(version) {
        try {
            const restored = JSON.parse(window.localStorage.getItem(storageKey(version)) || 'null');
            if (restored?.version === version && restored.answers && Array.isArray(restored.skipped)) return restored;
        } catch (_) { /* A fresh local profile is safe when storage is unavailable or corrupt. */ }
        return emptyState(version);
    }

    function emptyState(version) {
        return { version, answers: {}, skipped: [], current: 0 };
    }

    function persistState() {
        try {
            window.localStorage.setItem(storageKey(questionnaire.version), JSON.stringify(state));
        } catch (_) { /* The questionnaire still works without persistence. */ }
    }

    function storageKey(version) {
        return `${STORAGE_PREFIX}.${version}`;
    }

    function showOnly(id) {
        ['priorityLoading', 'priorityIntro', 'priorityQuestionStage', 'priorityResults', 'priorityError']
            .forEach(candidate => document.querySelector(`#${candidate}`).hidden = candidate !== id);
    }

    function copy() {
        return COPY[currentLocale] || COPY.ar;
    }

    function format(template, values) {
        return Object.entries(values).reduce((text, [key, value]) => text.replaceAll(`{${key}}`, value), template);
    }

    function track(name, values) {
        window.FhemniAnalytics?.trackEvent(name, values);
    }

    function escapeHtml(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;').replaceAll("'", '&#039;');
    }

    function escapeAttr(value) {
        return escapeHtml(value);
    }
})();
