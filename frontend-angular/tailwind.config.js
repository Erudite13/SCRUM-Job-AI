/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#0050cb',
          container: '#0066ff',
          fixed: '#dae1ff'
        },
        secondary: {
          DEFAULT: '#505f76',
          container: '#d0e1fb',
          fixed: '#d3e4fe'
        },
        tertiary: {
          DEFAULT: '#6834d2',
          container: '#8252ec',
          fixed: '#e9ddff'
        },
        surface: {
          DEFAULT: '#faf8ff',
          dim: '#d2d9f4',
          bright: '#faf8ff',
          lowest: '#ffffff',
          low: '#f2f3ff',
          container: '#eaedff',
          high: '#e2e7ff',
          highest: '#dae2fd'
        },
        on: {
          surface: '#131b2e',
          surfaceVariant: '#424656',
          primary: '#ffffff',
          secondary: '#ffffff',
          tertiary: '#ffffff'
        },
        success: '#10b981',
        warning: '#f59e0b',
        critical: '#ef4444'
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace']
      },
      borderRadius: {
        sm: '0.25rem',     // 4px
        DEFAULT: '0.5rem',  // 8px
        md: '0.75rem',     // 12px
        lg: '1rem',        // 16px
        xl: '1.5rem',       // 24px
        full: '9999px'
      },
      spacing: {
        xs: '4px',
        sm: '8px',
        md: '16px',
        lg: '24px',
        xl: '32px'
      },
      boxShadow: {
        ambient: '0 4px 20px rgba(15, 23, 42, 0.05)',
        'ai-glow': '0 0 15px rgba(104, 52, 210, 0.25), inset 0 0 8px rgba(104, 52, 210, 0.1)'
      }
    },
  },
  plugins: [],
}
