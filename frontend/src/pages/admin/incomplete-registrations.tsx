'use client';
import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import api from '@/services/api';

interface IncompleteRegistration {
    membershipNumber: string;
    name: string;
    primaryEmail: string;
    registrationTime: string;
    hasRegistered: boolean;
    isAttending: boolean;
    absenceReason: string | null;
    isSpecialVote: boolean;
}

interface Event {
    id: number;
    name: string;
    eventCode: string;
    eventType: string;
}

export default function IncompleteRegistrationsPage() {
    const router = useRouter();
    const [isAuthorized, setIsAuthorized] = useState(false);
    const [loading, setLoading] = useState(true);
    const [events, setEvents] = useState<Event[]>([]);
    const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
    const [incompleteRegistrations, setIncompleteRegistrations] = useState<IncompleteRegistration[]>([]);
    const [stats, setStats] = useState({ totalCount: 0, eventName: '' });

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }
        setIsAuthorized(true);
        fetchEvents();
    }, [router]);

    useEffect(() => {
        if (selectedEventId) {
            fetchIncompleteRegistrations(selectedEventId);
        }
    }, [selectedEventId]);

    const fetchEvents = async () => {
        try {
            const response = await api.get('/admin/events');
            setEvents(response.data.data || []);
        } catch (error) {
            console.error('Failed to fetch events:', error);
            toast.error('Failed to fetch events');
        } finally {
            setLoading(false);
        }
    };

    const fetchIncompleteRegistrations = async (eventId: number) => {
        try {
            setLoading(true);
            const response = await api.get(`/admin/checkin/events/${eventId}/incomplete-registrations`);

            if (response.data.status === 'success') {
                setIncompleteRegistrations(response.data.data.incompleteMembers);
                setStats({
                    totalCount: response.data.data.totalCount,
                    eventName: response.data.data.eventName
                });
            }
        } catch (error: any) {
            console.error('Failed to fetch incomplete registrations:', error);
            toast.error('Failed to fetch incomplete registrations');
        } finally {
            setLoading(false);
        }
    };

    const exportToCSV = () => {
        if (incompleteRegistrations.length === 0) {
            toast.warning('No data to export');
            return;
        }

        const headers = ['Membership Number', 'Name', 'Email', 'Registration Time', 'Status'];
        const csvContent = [
            headers.join(','),
            ...incompleteRegistrations.map(reg => [
                reg.membershipNumber,
                `"${reg.name}"`,
                reg.primaryEmail,
                new Date(reg.registrationTime).toLocaleString(),
                'Incomplete - No attendance choice'
            ].join(','))
        ].join('\n');

        const blob = new Blob([csvContent], { type: 'text/csv' });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `incomplete-registrations-${stats.eventName}-${new Date().toISOString().split('T')[0]}.csv`;
        a.click();
        window.URL.revokeObjectURL(url);
    };

    if (loading && events.length === 0) {
        return (
            <Layout>
                <div className="flex items-center justify-center min-h-screen">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="min-h-screen bg-gray-50 dark:bg-gray-900 py-6">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="bg-white dark:bg-gray-800 shadow rounded-lg">
                        <div className="px-4 py-5 sm:p-6">
                            {/* Header */}
                            <div className="flex justify-between items-center mb-6">
                                <div>
                                    <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
                                        Incomplete Registrations
                                    </h1>
                                    <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
                                        Members who registered but may not have completed their attendance choice
                                    </p>
                                </div>
                                <button
                                    onClick={() => router.push('/admin/dashboard')}
                                    className="inline-flex items-center px-4 py-2 border border-gray-300 dark:border-gray-600 shadow-sm text-sm font-medium rounded-md text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700"
                                >
                                    Back to Dashboard
                                </button>
                            </div>

                            {/* Event Selection */}
                            <div className="mb-6">
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                    Select Event
                                </label>
                                <select
                                    value={selectedEventId || ''}
                                    onChange={(e) => setSelectedEventId(Number(e.target.value) || null)}
                                    className="w-full max-w-md px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white"
                                >
                                    <option value="">Select an event...</option>
                                    {events.map((event) => (
                                        <option key={event.id} value={event.id}>
                                            {event.name} ({event.eventCode})
                                        </option>
                                    ))}
                                </select>
                            </div>

                            {/* Stats and Export */}
                            {selectedEventId && (
                                <div className="mb-6 flex justify-between items-center">
                                    <div className="bg-orange-50 dark:bg-orange-900/30 border border-orange-200 dark:border-orange-700 rounded-lg p-4">
                                        <div className="flex items-center">
                                            <div className="text-orange-500 text-2xl mr-3">⚠️</div>
                                            <div>
                                                <h3 className="text-lg font-medium text-orange-800 dark:text-orange-200">
                                                    {stats.totalCount} Incomplete Registrations Found
                                                </h3>
                                                <p className="text-sm text-orange-700 dark:text-orange-300">
                                                    These members registered but didn't complete their attendance choice
                                                </p>
                                            </div>
                                        </div>
                                    </div>

                                    {incompleteRegistrations.length > 0 && (
                                        <button
                                            onClick={exportToCSV}
                                            className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
                                        >
                                            📊 Export to CSV
                                        </button>
                                    )}
                                </div>
                            )}

                            {/* Results Table */}
                            {selectedEventId && (
                                <div className="overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg">
                                    {loading ? (
                                        <div className="text-center py-12">
                                            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto"></div>
                                            <p className="mt-2 text-gray-500 dark:text-gray-400">Loading...</p>
                                        </div>
                                    ) : incompleteRegistrations.length === 0 ? (
                                        <div className="text-center py-12">
                                            <div className="text-green-500 text-6xl mb-4">✅</div>
                                            <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
                                                All Good!
                                            </h3>
                                            <p className="text-gray-500 dark:text-gray-400">
                                                No incomplete registrations found for this event.
                                            </p>
                                        </div>
                                    ) : (
                                        <table className="min-w-full divide-y divide-gray-300 dark:divide-gray-600">
                                            <thead className="bg-gray-50 dark:bg-gray-700">
                                            <tr>
                                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                                                    Member
                                                </th>
                                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                                                    Contact
                                                </th>
                                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                                                    Registration Time
                                                </th>
                                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                                                    Status
                                                </th>
                                            </tr>
                                            </thead>
                                            <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                                            {incompleteRegistrations.map((registration, index) => (
                                                <tr key={index} className="hover:bg-gray-50 dark:hover:bg-gray-700">
                                                    <td className="px-6 py-4 whitespace-nowrap">
                                                        <div>
                                                            <div className="text-sm font-medium text-gray-900 dark:text-white">
                                                                {registration.name}
                                                            </div>
                                                            <div className="text-sm text-gray-500 dark:text-gray-400">
                                                                {registration.membershipNumber}
                                                            </div>
                                                        </div>
                                                    </td>
                                                    <td className="px-6 py-4 whitespace-nowrap">
                                                        <div className="text-sm text-gray-900 dark:text-white">
                                                            {registration.primaryEmail}
                                                        </div>
                                                    </td>
                                                    <td className="px-6 py-4 whitespace-nowrap">
                                                        <div className="text-sm text-gray-900 dark:text-white">
                                                            {new Date(registration.registrationTime).toLocaleString()}
                                                        </div>
                                                    </td>
                                                    <td className="px-6 py-4 whitespace-nowrap">
                                                            <span className="inline-flex px-2 py-1 text-xs font-semibold rounded-full bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200">
                                                                ⚠️ Incomplete - No attendance choice
                                                            </span>
                                                    </td>
                                                </tr>
                                            ))}
                                            </tbody>
                                        </table>
                                    )}
                                </div>
                            )}

                            {/* Help Section */}
                            <div className="mt-8 bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg p-6">
                                <h3 className="text-lg font-medium text-blue-800 dark:text-blue-200 mb-2">
                                    💡 How to Fix Incomplete Registrations
                                </h3>
                                <ul className="text-sm text-blue-700 dark:text-blue-300 space-y-2">
                                    <li>• Contact these members directly to complete their attendance choice</li>
                                    <li>• Send them their registration link again via email or SMS</li>
                                    <li>• These members registered but the page may have scrolled past the attendance selection</li>
                                    <li>• Export the list to CSV for follow-up communications</li>
                                </ul>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </Layout>
    );
} 