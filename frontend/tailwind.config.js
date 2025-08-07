/** @type {import('tailwindcss').Config} */
module.exports = {
    content: [
        './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
        './src/components/**/*.{js,ts,jsx,tsx,mdx}',
        './src/app/**/*.{js,ts,jsx,tsx,mdx}',
    ],
    darkMode: 'class',
    theme: {
        extend: {
            colors: {
                black: '#000000',
                orange: {
                    500: '#F17000',
                    600: '#D66200',
                    700: '#B85300',
                },
                purple: {
                    500: '#5D3587',
                    600: '#4E2D72',
                    700: '#3F2460',
                },
                teal: {
                    500: '#5BBB87',
                    600: '#4BA77A',
                    700: '#3B8760',
                },
                blue: {
                    500: '#5D8BAB',
                    600: '#4F768F',
                    700: '#41627A',
                },

                gray: {
                    50: '#F9FAFB',
                    100: '#F3F4F6',
                    200: '#E5E7EB',
                    300: '#D1D5DB',
                    400: '#9CA3AF',
                    500: '#6B7280',
                    600: '#4B5563',
                    700: '#374151',
                    800: '#1F2937',
                    900: '#111827',
                }
            },

        },
    },
    plugins: [],
}

