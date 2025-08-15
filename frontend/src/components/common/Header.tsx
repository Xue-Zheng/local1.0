'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useTheme } from 'next-themes';
const Header = () => {
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    const { theme, setTheme } = useTheme();
    const toggleTheme = () => {
        setTheme(theme === 'dark' ? 'light' : 'dark');
    };
    return (
        <header className="bg-black text-white shadow-md">
            <div className="container mx-auto px-4 py-4">
                <div className="flex items-center justify-between">
                    <Link href="/" className="flex items-center space-x-3">
                        <div className="relative w-12 h-12 bg-white rounded-full flex items-center justify-center" style={{ aspectRatio: '1/1'}}>
                            <span className="text-black text-xl font-bold">E tū</span>
                        </div>
                        <span className="text-xl font-bold tracking-wide">E tū Event Portal</span>
                    </Link>
                    <div className="flex items-center space-x-4">
                        <button
                            onClick={toggleTheme}
                            className="p-2 rounded-full hover:bg-gray-700 transition-colors"
                            aria-label="Toggle theme"
                        >
                            {theme === 'dark' ? (
                                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6">
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 3v2.25m6.364.386-1.591 1.591M21 12h-2.25m-.386 6.364-1.591-1.591M12 18.75V21m-4.773-4.227-1.591 1.591M5.25 12H3m4.227-4.773L5.636 5.636M15.75 12a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0Z" />
                                </svg>
                            ) : (
                                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6">
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M21.752 15.002A9.718 9.718 0 0 1 18 15.75c-5.385 0-9.75-4.365-9.75-9.75 0-1.33.266-2.597.748-3.752A9.753 9.753 0 0 0 3 11.25C3 16.635 7.365 21 12.75 21a9.753 9.753 0 0 0 9.002-5.998Z" />
                                </svg>
                            )}
                        </button>
                        <div className="lg:hidden">
                            <button
                                type="button"
                                className="p-2 text-white hover:text-orange-500 transition-colors focus:outline-none"
                                onClick={() => setIsMenuOpen(!isMenuOpen)}
                                aria-label="Toggle menu"
                            >
                                {!isMenuOpen ? (
                                    <svg className="h-7 w-7" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                                    </svg>
                                ) : (
                                    <svg className="h-7 w-7" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                    </svg>
                                )}
                            </button>
                        </div>
                    </div>
                    <nav className="hidden lg:flex space-x-8 text-sm items-center">
                        <Link href="/register" className="hover:text-orange-500 transition-colors">
                            Register
                        </Link>
                        <Link href="/ticket" className="hover:text-orange-500 transition-colors">
                            My Ticket
                        </Link>
                        <Link href="https://etu.nz/" target="_blank" className="hover:text-orange-500 transition-colors">
                            E tū Website
                        </Link>
                    </nav>
                </div>
                <div
                    className={`lg:hidden transition-all duration-300 ease-in-out overflow-hidden ${
                        isMenuOpen ? 'max-h-60 opacity-100 mt-4' : 'max-h-0 opacity-0'
                    }`}
                >
                    <nav className="flex flex-col space-y-4 text-sm">
                        <Link
                            href="/register"
                            className="hover:text-orange-500 transition-colors"
                            onClick={() => setIsMenuOpen(false)}
                        >
                            Register
                        </Link>
                        <Link
                            href="/ticket"
                            className="hover:text-orange-500 transition-colors"
                            onClick={() => setIsMenuOpen(false)}
                        >
                            My Ticket
                        </Link>
                        <Link
                            href="https://etu.nz/"
                            target="_blank"
                            className="hover:text-orange-500 transition-colors"
                            onClick={() => setIsMenuOpen(false)}
                        >
                            E tū Website
                        </Link>
                    </nav>
                </div>
            </div>
        </header>
    );
};
export default Header;