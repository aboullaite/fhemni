/** @type {import('tailwindcss').Config} */
module.exports = {
    content: [
        "./src/main/resources/static/**/*.html",
        "./src/main/resources/static/**/*.js"
    ],
    theme: {
        extend: {
            colors: {
                "fhemni-red": "#b91c1c",
                "fhemni-green": "#166534",
                "fhemni-dark": "#0f172a",
                supported: "#16a34a",
                "needs-context": "#f59e0b",
                unverifiable: "#9ca3af"
            },
            fontFamily: {
                sans: [
                    "ui-sans-serif",
                    "system-ui",
                    "-apple-system",
                    "BlinkMacSystemFont",
                    "Segoe UI",
                    "sans-serif"
                ],
                arabic: [
                    "TIDO Arabic",
                    "Tahoma",
                    "Arial",
                    "Segoe UI",
                    "sans-serif"
                ]
            }
        }
    }
};
