(() => {
    const LIST_PATTERN = /^(\s*)([-+*]|\d+[.)])\s+(.+)$/;
    const HEADING_PATTERN = /^(#{1,4})\s+(.+)$/;

    function render(value) {
        const lines = String(value || '').replace(/\r\n?/g, '\n').split('\n');
        const blocks = [];
        let index = 0;

        while (index < lines.length) {
            if (!lines[index].trim()) {
                index += 1;
                continue;
            }

            const heading = lines[index].match(HEADING_PATTERN);
            if (heading) {
                blocks.push(`<h3 class="mb-2 mt-4 text-base font-black leading-snug first:mt-0">${renderInline(heading[2])}</h3>`);
                index += 1;
                continue;
            }

            if (LIST_PATTERN.test(lines[index])) {
                const list = readList(lines, index);
                blocks.push(renderList(list.items));
                index = list.nextIndex;
                continue;
            }

            const paragraph = [];
            while (index < lines.length
                && lines[index].trim()
                && !HEADING_PATTERN.test(lines[index])
                && !LIST_PATTERN.test(lines[index])) {
                paragraph.push(lines[index].trim());
                index += 1;
            }
            blocks.push(`<p class="m-0 leading-relaxed">${paragraph.map(renderInline).join('<br>')}</p>`);
        }

        return blocks.join('');
    }

    function readList(lines, startIndex) {
        const items = [];
        let index = startIndex;
        let minimumIndent = null;

        while (index < lines.length) {
            const match = lines[index].match(LIST_PATTERN);
            if (match) {
                const indentation = indentationWidth(match[1]);
                minimumIndent = minimumIndent === null ? indentation : Math.min(minimumIndent, indentation);
                items.push({ indentation, ordered: /^\d/.test(match[2]), content: match[3] });
                index += 1;
                continue;
            }

            if (!lines[index].trim()) {
                let next = index + 1;
                while (next < lines.length && !lines[next].trim()) next += 1;
                if (next < lines.length && LIST_PATTERN.test(lines[next])) {
                    index = next;
                    continue;
                }
            }
            break;
        }

        const baseIndent = minimumIndent || 0;
        return {
            items: items.map(item => ({
                ...item,
                depth: Math.min(3, Math.max(0, Math.floor((item.indentation - baseIndent) / 2)))
            })),
            nextIndex: index
        };
    }

    function renderList(items) {
        if (!items.length) return '';
        let html = '';
        let depth = -1;
        const listTypes = [];

        items.forEach(item => {
            if (item.depth > depth) {
                while (depth < item.depth) {
                    depth += 1;
                    const type = item.ordered ? 'ol' : 'ul';
                    listTypes[depth] = type;
                    html += `<${type} class="${listClasses(type, depth)}">`;
                }
            } else if (item.depth === depth) {
                html += '</li>';
            } else {
                while (depth > item.depth) {
                    html += `</li></${listTypes[depth]}>`;
                    listTypes.pop();
                    depth -= 1;
                }
                html += '</li>';
            }
            html += `<li>${renderInline(item.content)}`;
        });

        while (depth >= 0) {
            html += `</li></${listTypes[depth]}>`;
            depth -= 1;
        }
        return html;
    }

    function listClasses(type, depth) {
        const marker = type === 'ol' ? 'list-decimal' : 'list-disc';
        return depth === 0
            ? `my-2 ms-5 ${marker} space-y-2 leading-relaxed`
            : `mb-1 mt-2 ms-5 ${marker} space-y-2 leading-relaxed`;
    }

    function renderInline(value) {
        const input = String(value || '');
        const linkPattern = /\[([^\]\n]+)]\((https?:\/\/[^\s)]+)\)/gi;
        let rendered = '';
        let cursor = 0;
        let match;

        while ((match = linkPattern.exec(input)) !== null) {
            rendered += renderInlineText(input.slice(cursor, match.index));
            const url = safeUrl(match[2]);
            rendered += url
                ? `<a class="font-bold text-primary underline decoration-primary/35 underline-offset-2" href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${renderInlineText(match[1])}</a>`
                : renderInlineText(match[0]);
            cursor = match.index + match[0].length;
        }
        return rendered + renderInlineText(input.slice(cursor));
    }

    function renderInlineText(value) {
        return renderTimestampButtons(
            escapeHtml(value)
                .replace(/\*\*([^*\n]+)\*\*/g, '<strong class="font-black">$1</strong>')
                .replace(/__([^_\n]+)__/g, '<strong class="font-black">$1</strong>'));
    }

    function renderTimestampButtons(value) {
        return value.replace(/(?<!\d)(\d{1,2}):([0-5]\d)(?::([0-5]\d))?(?!\d)/g,
            (match, first, second, third) => {
                const seconds = third === undefined
                    ? Number(first) * 60 + Number(second)
                    : Number(first) * 3600 + Number(second) * 60 + Number(third);
                return `<button class="timestamp-button btn btn-primary btn-soft btn-xs mx-0.5 h-auto min-h-7 align-baseline font-mono font-extrabold" type="button" data-seconds="${seconds}">${match}</button>`;
            });
    }

    function indentationWidth(value) {
        return [...value].reduce((width, character) => width + (character === '\t' ? 2 : 1), 0);
    }

    function safeUrl(value) {
        try {
            const url = new URL(value);
            return ['http:', 'https:'].includes(url.protocol) ? url.href : '';
        } catch (_) {
            return '';
        }
    }

    function escapeHtml(value) {
        return String(value)
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    }

    window.FhemniMarkdown = Object.freeze({ render });
})();
