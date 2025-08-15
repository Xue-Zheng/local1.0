'use client';
import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/common/Layout';
import api from '@/services/api';
import { toast } from 'react-toastify';
import Link from 'next/link';

interface MemberStats {
    totalMembers: number;
    emailSent: number;
    smsSent: number;
    emailPending: number;
    smsPending: number;
}

export default function SmartNotificationsPage() {
    const router = useRouter();
    const [memberStats, setMemberStats] = useState<MemberStats>({
        totalMembers: 0,
        emailSent: 0,
        smsSent: 0,
        emailPending: 0,
        smsPending: 0
    });
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        fetchMemberStats();
    }, []);

    const fetchMemberStats = async () => {
        try {
            const response = await api.get('/admin/notifications/stats');
            if (response.data.status === 'success') {
                setMemberStats(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch stats:', error);
            toast.error('Failed to fetch notification stats');
        } finally {
            setIsLoading(false);
        }
    };

    if (isLoading) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12 text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-purple-500"></div>
                    <p className="mt-4 text-gray-700 dark:text-gray-300">Loading...</p>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="container mx-auto px-4 py-8">
                <div className="flex justify-between items-center mb-8">
                    <div className="flex items-center">
                        <Link href="/admin/dashboard">
                            <button className="mr-4 text-gray-600 dark:text-gray-400 hover:text-black dark:hover:text-white">
                                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
                                </svg>
                            </button>
                        </Link>
                        <h1 className="text-3xl font-bold text-black dark:text-white">Smart Notifications</h1>
                    </div>
                </div>

                {/* Statistics Cards */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
                    <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
                        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">Total Members</h3>
                        <p className="text-3xl font-bold text-blue-600">{memberStats.totalMembers}</p>
                    </div>
                    <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
                        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">Emails Sent</h3>
                        <p className="text-3xl font-bold text-green-600">{memberStats.emailSent}</p>
                    </div>
                    <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
                        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">SMS Sent</h3>
                        <p className="text-3xl font-bold text-green-600">{memberStats.smsSent}</p>
                    </div>
                    <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
                        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">Pending</h3>
                        <p className="text-3xl font-bold text-orange-600">{memberStats.emailPending + memberStats.smsPending}</p>
                    </div>
                </div>

                {/* Action Buttons */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
                        <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">Email Notifications</h3>
                        <p className="text-gray-600 dark:text-gray-400 mb-4">Send bulk email notifications to members</p>
                        <Link href="/admin/email">
                            <button className="w-full bg-blue-600 hover:bg-blue-700 text-white py-2 px-4 rounded transition-colors">
                                Manage Emails
                            </button>
                        </Link>
                    </div>

                    <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
                        <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">SMS Notifications</h3>
                        <p className="text-gray-600 dark:text-gray-400 mb-4">Send SMS messages to members</p>
                        <Link href="/admin/sms">
                            <button className="w-full bg-green-600 hover:bg-green-700 text-white py-2 px-4 rounded transition-colors">
                                Manage SMS
                            </button>
                        </Link>
                    </div>

                    <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
                        <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">Templates</h3>
                        <p className="text-gray-600 dark:text-gray-400 mb-4">Manage notification templates</p>
                        <Link href="/admin/templates">
                            <button className="w-full bg-purple-600 hover:bg-purple-700 text-white py-2 px-4 rounded transition-colors">
                                Manage Templates
                            </button>
                        </Link>
                    </div>
                </div>
            </div>
        </Layout>
    );
}