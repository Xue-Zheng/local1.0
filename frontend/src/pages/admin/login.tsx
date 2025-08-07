'use client';
import { useState } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import axios from 'axios';
import Layout from '@/components/common/Layout';
import api from '@/services/api';

export default function AdminLoginPage() {
    const router = useRouter();
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState('');

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsLoading(true);
        setError('');
        try {
            console.log("Attempting login with:", { username, password });

            const response = await api.post('/auth/admin/login', {
                username,
                password
            });

            console.log("Login response:", response.data);

            if (response.data.status === 'success') {
                localStorage.setItem('adminToken', response.data.data.token);
                localStorage.setItem('adminName', response.data.data.name || 'Administrator');
                toast.success('Login successful!');
                router.push('/admin/dashboard');
            } else {
                throw new Error(response.data.message || 'Login failed');
            }
        } catch (err: any) {
            console.error('Login failed:', err);
            if (err.response) {
                console.error('Response status:', err.response.status);
                console.error('Response data:', err.response.data);
            }
            setError(err.response?.data?.message || 'Invalid username or password');
            toast.error('Login failed');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Layout>
            <div className="container mx-auto px-4 py-12">
                <div className="max-w-md mx-auto">
                    <h1 className="text-3xl font-bold text-black dark:text-white mb-6 text-center">Admin Login</h1>
                    <div className="bg-white dark:bg-gray-800 p-8 rounded-lg shadow-md">
                        <form onSubmit={handleSubmit}>
                            <div className="mb-4">
                                <label htmlFor="username" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Username</label>
                                <input
                                    type="text"
                                    id="username"
                                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                                    value={username}
                                    onChange={(e) => setUsername(e.target.value)}
                                    required
                                />
                            </div>
                            <div className="mb-6">
                                <label htmlFor="password" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Password</label>
                                <input
                                    type="password"
                                    id="password"
                                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent"
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    required
                                />
                            </div>
                            {error && (
                                <div className="mb-4 p-3 bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-400 rounded">
                                    {error}
                                </div>
                            )}
                            <button
                                type="submit"
                                className="w-full bg-purple-600 hover:bg-purple-700 text-white font-medium py-2 px-4 rounded transition-colors"
                                disabled={isLoading}
                            >
                                {isLoading ? 'Logging in...' : 'Log In'}
                            </button>
                        </form>
                    </div>
                </div>
            </div>
        </Layout>
    );
}