/** @type {import('tailwindcss').Config} */
export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: [
          "Inter",
          "ui-sans-serif",
          "system-ui",
          "-apple-system",
          "Segoe UI",
          "Roboto",
          "Helvetica",
          "Arial",
          "sans-serif",
        ],
        mono: ["JetBrains Mono", "ui-monospace", "SFMono-Regular", "Menlo", "monospace"],
      },
      colors: {
        // Surface + ink tokens are CSS variables so they swap with the theme.
        // The triplet format ("11 13 18") lets Tailwind compose alpha as
        // rgb(var(--tok) / <alpha-value>), so `bg-surface/40` etc. still work.
        surface: {
          DEFAULT: "rgb(var(--surface-bg) / <alpha-value>)",
          raised:  "rgb(var(--surface-raised) / <alpha-value>)",
          muted:   "rgb(var(--surface-muted) / <alpha-value>)",
          border:  "rgb(var(--surface-border) / <alpha-value>)",
        },
        ink: {
          primary:   "rgb(var(--ink-primary) / <alpha-value>)",
          secondary: "rgb(var(--ink-secondary) / <alpha-value>)",
          muted:     "rgb(var(--ink-muted) / <alpha-value>)",
        },
        // Brand: 300/400 shift between themes so text accents stay readable on
        // both light and dark surfaces. Solid fills (500/600) stay constant.
        // See --brand-* tokens in index.css.
        brand: {
          50:  "rgb(var(--brand-50)  / <alpha-value>)",
          100: "rgb(var(--brand-100) / <alpha-value>)",
          200: "rgb(var(--brand-200) / <alpha-value>)",
          300: "rgb(var(--brand-300) / <alpha-value>)",
          400: "rgb(var(--brand-400) / <alpha-value>)",
          500: "rgb(var(--brand-500) / <alpha-value>)",
          600: "rgb(var(--brand-600) / <alpha-value>)",
          700: "rgb(var(--brand-700) / <alpha-value>)",
          800: "rgb(var(--brand-800) / <alpha-value>)",
          900: "rgb(var(--brand-900) / <alpha-value>)",
        },
        success: { 500: "#22c55e", 600: "#16a34a" },
        warning: { 500: "#f59e0b", 600: "#d97706" },
        danger:  { 500: "#ef4444", 600: "#dc2626" },
      },
      borderRadius: {
        md: "0.5rem",
        lg: "0.75rem",
        xl: "1rem",
      },
      boxShadow: {
        card:  "var(--shadow-card)",
        focus: "0 0 0 2px rgba(59,110,240,0.45)",
        // Subtle elevated card (used by dialogs, dropdowns).
        pop:   "var(--shadow-pop)",
      },
      keyframes: {
        "fade-in":    { "0%": { opacity: 0 }, "100%": { opacity: 1 } },
        shimmer:      { "0%": { backgroundPosition: "-200% 0" }, "100%": { backgroundPosition: "200% 0" } },
        // Status-indicator animations.
        "pulse-soft": { "0%, 100%": { opacity: 1 }, "50%": { opacity: 0.55 } },
        "wave-bar":   {
          "0%, 100%": { transform: "scaleY(0.35)" },
          "50%":      { transform: "scaleY(1)" },
        },
        "stripe-shift": {
          "0%":   { backgroundPosition: "0 0" },
          "100%": { backgroundPosition: "0 -200%" },
        },
        // Micro-interactions (modal / dropdown / toast entrances).
        "scale-in": {
          "0%":   { opacity: 0, transform: "scale(0.96)" },
          "100%": { opacity: 1, transform: "scale(1)" },
        },
        "slide-up-fade": {
          "0%":   { opacity: 0, transform: "translateY(8px)" },
          "100%": { opacity: 1, transform: "translateY(0)" },
        },
        "slide-down-fade": {
          "0%":   { opacity: 0, transform: "translateY(-8px)" },
          "100%": { opacity: 1, transform: "translateY(0)" },
        },
        "skeleton-shimmer": {
          "0%":   { backgroundPosition: "-200% 0" },
          "100%": { backgroundPosition: "200% 0" },
        },
      },
      animation: {
        "fade-in":         "fade-in 140ms ease-out",
        shimmer:           "shimmer 1.5s linear infinite",
        "pulse-soft":      "pulse-soft 1.6s ease-in-out infinite",
        "wave-bar-1":      "wave-bar 1s ease-in-out infinite",
        "wave-bar-2":      "wave-bar 1s ease-in-out infinite 0.15s",
        "wave-bar-3":      "wave-bar 1s ease-in-out infinite 0.3s",
        "stripe-shift":    "stripe-shift 2.4s linear infinite",
        // Curves chosen to feel responsive but never sluggish — keep durations
        // under ~200ms so the UI feels instant.
        "scale-in":        "scale-in 160ms cubic-bezier(0.16, 1, 0.3, 1)",
        "slide-up-fade":   "slide-up-fade 180ms cubic-bezier(0.16, 1, 0.3, 1)",
        "slide-down-fade": "slide-down-fade 180ms cubic-bezier(0.16, 1, 0.3, 1)",
        skeleton:          "skeleton-shimmer 1.6s linear infinite",
      },
    },
  },
  plugins: [],
};
