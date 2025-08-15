'use client';
import React, {useEffect, useState} from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import { verifyMember } from '@/services/registrationService';
import api from '@/services/api';

export default function RegisterPage() {
    const router = useRouter();
    const [token, setToken] = useState('');
    const [membershipNumber, setMembershipNumber] = useState('');
    const [verificationCode, setVerificationCode] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState('');

    // 用于静态导出的客户端URL参数解析
    useEffect(() => {
        if (typeof window !== 'undefined') {
            const urlParams = new URLSearchParams(window.location.search);
            const tokenParam = urlParams.get('token') || '';
            setToken(tokenParam);
        }
    }, []);

    useEffect(() => {
        const fetchMemberInfo = async () => {
            if (token) {
                try {
                    const response = await api.get(`/event-registration/member-info/${token}`);
                    if (response.data.status === 'success') {
                        setMembershipNumber(response.data.data.membershipNumber);
                        setVerificationCode(response.data.data.verificationCode || '');
                    }
                } catch (error) {
                    console.error('Error retrieving member information', error);
                }
            }
        };
        fetchMemberInfo();
    }, [token]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!membershipNumber || !verificationCode) {
            setError('Please fill in all required fields');
            return;
        }
        if (!token) {
            setError('Invalid registration link. Please ensure you clicked the complete link in the email');
            return;
        }
        setIsLoading(true);
        setError('');
        try {
            const response = await verifyMember({
                membershipNumber,
                verificationCode,
                token,
            });
            toast.success('Verification successful!');
            // Redirect to form page after successful verification
            router.push(`/register/form?token=${token}`);
        } catch (err: any) {
            console.error('Verification failed:', err);
            setError(err.response?.data?.message || 'Verification failed. Please check your information');
            toast.error('Verification failed');
        } finally {
            setIsLoading(false);
        }
    };
    return (
        <Layout>
            <div className="container mx-auto px-4 py-12">
                <div className="max-w-md mx-auto">
                    <h1 className="text-3xl font-bold text-black dark:text-white mb-6 text-center">Member Registration</h1>
                    <div className="bg-white dark:bg-gray-800 p-8 rounded-lg shadow-md">
                        <form onSubmit={handleSubmit}>
                            <div className="mb-4">
                                <label htmlFor="membershipNumber" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Membership Number <span className="text-red-600">*</span></label>
                                <input
                                    type="text"
                                    id="membershipNumber"
                                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent"
                                    value={membershipNumber}
                                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => setMembershipNumber(e.target.value)}
                                    placeholder="Enter your membership number"
                                    required
                                />
                            </div>
                            <div className="mb-6">
                                <label htmlFor="verificationCode" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Verification Code <span className="text-red-600">*</span></label>
                                <input
                                    type="text"
                                    id="verificationCode"
                                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:border-transparent"
                                    value={verificationCode}
                                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => setVerificationCode(e.target.value)}
                                    placeholder="Enter 6-digit verification code"
                                    maxLength={6}
                                    required
                                />
                                <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                                    The verification code has been sent to your email. Please check your inbox.
                                </p>
                            </div>
                            {error && (
                                <div className="mb-4 p-3 bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-400 rounded">
                                    {error}
                                </div>
                            )}
                            <button
                                type="submit"
                                className="w-full bg-black hover:bg-gray-800 dark:bg-orange-600 dark:hover:bg-orange-700 text-white font-medium py-3 px-4 rounded transition-colors"
                                disabled={isLoading}
                            >
                                {isLoading ? 'Verifying...' : 'Verify Identity'}
                            </button>
                        </form>
                    </div>
                    <div className="mt-6 text-center text-sm text-gray-600 dark:text-gray-400">
                        <p>If you haven't received a verification code or are experiencing other issues, please contact the E tū support team:</p>
                        <p className="mt-1">Phone: 0800 186 466</p>
                        <p>Email: <a href="mailto:support@etu.nz" className="text-blue-500 hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300">support@etu.nz</a></p>
                    </div>
                </div>
            </div>
        </Layout>
    );
}