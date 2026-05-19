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
        // Surface tokens (neutral, slightly cool)
        surface: {
          DEFAULT: "#0b0d12",   // app background
          raised:  "#121419",   // cards
          muted:   "#191c22",   // hover / muted areas
          border:  "#23272f",
        },
        ink: {
          primary:   "#f4f5f7",
          secondary: "#a4abb6",
          muted:     "#6b727d",
        },
        brand: {
          50:  "#eff5ff",
          100: "#dbe7fe",
          200: "#bcd2fd",
          300: "#8db4fb",
          400: "#5b8df7",
          500: "#3b6ef0",  // primary
          600: "#2c55da",
          700: "#2643b1",
          800: "#23398d",
          900: "#1f3171",
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
        card: "0 1px 0 0 rgba(255,255,255,0.03) inset, 0 1px 2px 0 rgba(0,0,0,0.4)",
        focus: "0 0 0 2px rgba(59,110,240,0.45)",
      },
      keyframes: {
        "fade-in": { "0%": { opacity: 0 }, "100%": { opacity: 1 } },
        shimmer:   { "0%": { backgroundPosition: "-200% 0" }, "100%": { backgroundPosition: "200% 0" } },
      },
      animation: {
        "fade-in": "fade-in 120ms ease-out",
        shimmer: "shimmer 1.5s linear infinite",
      },
    },
  },
  plugins: [],
};
