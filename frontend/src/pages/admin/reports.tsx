'use client';

import { useState, useEffect, useCallback, SetStateAction} from 'react';
import {useRouter} from 'next/router';
import {toast} from 'react-toastify';
import Layout from '@/components/common/Layout';
import Link from 'next/link';
import api from '@/services/api';

interface Event {
    id: number;
    name: string;
    eventCode: string;
    description?: string;
    eventType: string;
    eventDate?: string;
    venue?: string;
    isActive: boolean;
    isVotingEnabled: boolean;
    registrationOpen: boolean;
    totalMembers: number;
    registeredMembers: number;
    attendingMembers: number;
    specialVoteMembers: number;
    votedMembers: number;
    checkedInMembers: number;
    maxAttendees?: number;
    syncStatus?: string;
    lastSyncTime?: string;
    memberSyncCount?: number;
    attendeeSyncCount?: number;
    createdAt?: string;
    updatedAt?: string;
}

interface NotificationStats {
    primaryEmail: {
        manual_success: number;
        manual_failed: number;
        auto_success: number;
        auto_failed: number;
        total_success: number;
        total_failed: number;
    };
    sms: {
        manual_success: number;
        manual_failed: number;
        auto_success: number;
        auto_failed: number;
        total_success: number;
        total_failed: number;
    };
}

interface ReportData {
    event: Event;
    notifications: NotificationStats;
}

export default function ReportsPage() {
    const router = useRouter();
    const [isAuthorized, setIsAuthorized] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [events, setEvents] = useState<Event[]>([]);
    const [selectedEvent, setSelectedEvent] = useState('');
    const [reportData, setReportData] = useState<ReportData | null>(null);
    const [activeTab, setActiveTab] = useState('overview');
    const [upcomingEvent, setUpcomingEvents] = useState<Event[]>([]);

    const fetchEvents = useCallback(async () => {
        try {
            const response = await api.get('/admin/events');
            if (response.data.status === 'success') {
                setEvents(response.data.data);
                if (response.data.data.length > 0) {
                    setSelectedEvent(response.data.data[0].id.toString());
                    fetchReportData(response.data.data[0].id);
                }
            }
        } catch (error) {
            console.error('Failed to fetch events', error);
            toast.error('Failed to load events');
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }
        setIsAuthorized(true);
        fetchEvents();
    }, [router, fetchEvents]);

    const fetchReportData = async (eventId: SetStateAction<string>) => {
        try {
            const [eventResponse, notificationResponse] = await Promise.all([
                api.get(`/admin/events/${eventId}`),
                api.get(`/admin/notifications/stats/${eventId}`)
            ]);

            if (eventResponse.data.status === 'success' && notificationResponse.data.status === 'success') {
                setReportData({
                    event: eventResponse.data.data,
                    notifications: notificationResponse.data.data
                });
            }
        } catch (error) {
            console.error('Failed to fetch report data', error);
            toast.error('Failed to load report data');
        }
    };

    const handleEventChange = (eventId: SetStateAction<string>) => {
        setSelectedEvent(eventId);
        fetchReportData(eventId);
    };
    const exportToExcel = async (type: string, category: string) => {
        try {
            let endpoint = '';

            if (type === 'members') {
                if (category === 'all') {
                    endpoint = `/admin/events/${selectedEvent}/export/members`;
                } else if (category === 'notifications') {
                    endpoint = `/admin/events/${selectedEvent}/export/notifications`;
                } else {
                    // For filtered categories, use the filtered export endpoint
                    endpoint = `/admin/events/${selectedEvent}/export/filtered/${category}`;
                }
            } else if (type === 'checkin') {
                endpoint = `/admin/events/${selectedEvent}/export/checkin/${category}`;
            } else {
                endpoint = `/admin/events/${selectedEvent}/export/${type}`;
            }

            const response = await api.get(endpoint, {
                responseType: 'blob'
            });

            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;

            const event = events.find(e => e.id.toString() === selectedEvent);
            const eventName = event ? event.name.replace(/[^a-zA-Z0-9]/g, '_') : 'event';
            link.setAttribute('download', `${eventName}_${type}_${category}.xlsx`);

            document.body.appendChild(link);
            link.click();
            link.remove();
            window.URL.revokeObjectURL(url);

            toast.success(`${type} data exported successfully`);
        } catch (error) {
            console.error('Export failed:', error);
            toast.error('Export failed. Please try again.');
        }
    };

    const exportNotifications = async () => {
        try {
            const response = await api.get(`/admin/events/${selectedEvent}/export/notifications`, {
                responseType: 'blob'
            });

            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;

            const event = events.find(e => e.id.toString() === selectedEvent);
            const eventName = event ? event.name.replace(/[^a-zA-Z0-9]/g, '_') : 'event';
            link.setAttribute('download', `${eventName}_notifications.xlsx`);

            document.body.appendChild(link);
            link.click();
            link.remove();
            window.URL.revokeObjectURL(url);

            toast.success('Notifications exported successfully');
        } catch (error) {
            console.error('Export failed:', error);
            toast.error('Export failed. Please try again.');
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

    if (!isAuthorized) {
        return null;
    }

    return (
        <Layout>
            <div className="container mx-auto px-4 py-8">
                <div className="flex justify-between items-center mb-6">
                    <div className="flex items-center">
                        <Link href="/admin/dashboard">
                            <button className="mr-4 text-gray-600 dark:text-gray-400 hover:text-black dark:hover:text-white">
                                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
                                </svg>
                            </button>
                        </Link>
                        <h1 className="text-3xl font-bold text-black dark:text-white">Reports & Analytics</h1>
                    </div>
                </div>

                {/* Event Selection */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6 mb-6">
                    <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                        <div className="flex-1">
                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                Select Event for Report
                            </label>
                            <select
                                className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2"
                                value={selectedEvent}
                                onChange={(e) => handleEventChange(e.target.value)}
                            >
                                {events.map(event => (
                                    <option key={event.id} value={event.id}>
                                        {event.name} ({event.eventCode}) - {event.eventType.replace('_', ' ')}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div className="flex gap-2">
                            <select
                                className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md dark:bg-gray-700 dark:text-white"
                                onChange={(e) => exportToExcel('members', e.target.value)}
                                defaultValue=""
                            >
                                <option value="" disabled>Export Members by Category</option>
                                <option value="all">All Members</option>
                                <option value="registered">Registered Members</option>
                                <option value="attending">Attending Members</option>
                                <option value="not_attending">Not Attending Members</option>
                                <option value="special_vote">Special Vote Members</option>
                                <option value="has_email">Members with Email</option>
                                <option value="has_mobile">Members with Mobile</option>
                                <option value="email_only">Email Only Members</option>
                                <option value="sms_only">SMS Only Members</option>
                                <option value="northern">Northern Region</option>
                                <option value="central">Central Region</option>
                                <option value="southern">Southern Region</option>
                            </select>
                            <select
                                className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md dark:bg-gray-700 dark:text-white"
                                onChange={(e) => exportToExcel('checkin', e.target.value)}
                                defaultValue=""
                            >
                                <option value="" disabled>Export Check-in Data</option>
                                <option value="all">All Check-in Records</option>
                                <option value="checked_in">Checked In Members</option>
                                <option value="not_checked_in">Not Checked In Members</option>
                                <option value="voted">Voted Members</option>
                                <option value="not_voted">Not Voted Members</option>
                                <option value="northern">Northern Region Check-ins</option>
                                <option value="central">Central Region Check-ins</option>
                                <option value="southern">Southern Region Check-ins</option>
                            </select>
                            <button
                                onClick={() => exportToExcel('attendees', 'all')}
                                className="bg-green-500 hover:bg-green-600 text-white px-4 py-2 rounded flex items-center"
                            >
                                <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                </svg>
                                Export All Attendees
                            </button>
                            <button
                                onClick={() => exportNotifications()}
                                className="bg-purple-500 hover:bg-purple-600 text-white px-4 py-2 rounded flex items-center"
                            >
                                <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-5 5v-5zM4 5v10a1 1 0 001 1h10l5-5V5a1 1 0 00-1-1H5a1 1 0 00-1 1z" />
                                </svg>
                                Export Notifications
                            </button>
                        </div>
                    </div>
                </div>

                {/* Tab Navigation */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md mb-6">
                    <div className="border-b border-gray-200 dark:border-gray-700">
                        <nav className="-mb-px flex">
                            <button
                                onClick={() => setActiveTab('overview')}
                                className={`py-4 px-6 text-sm font-medium border-b-2 ${
                                    activeTab === 'overview'
                                        ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                        : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600'
                                }`}
                            >
                                Overview
                            </button>
                            <button
                                onClick={() => setActiveTab('notifications')}
                                className={`py-4 px-6 text-sm font-medium border-b-2 ${
                                    activeTab === 'notifications'
                                        ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                        : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600'
                                }`}
                            >
                                Notifications
                            </button>
                            <button
                                onClick={() => setActiveTab('analytics')}
                                className={`py-4 px-6 text-sm font-medium border-b-2 ${
                                    activeTab === 'analytics'
                                        ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                        : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600'
                                }`}
                            >
                                Analytics
                            </button>
                        </nav>
                    </div>

                    <div className="p-6">
                        {activeTab === 'overview' && reportData && (
                            <div className="space-y-6">
                                {/* Event Information */}
                                <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-6">
                                    <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Event Information</h3>
                                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                        <div>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Event Name</p>
                                            <p className="font-medium text-gray-900 dark:text-white">{reportData.event.name}</p>
                                        </div>
                                        <div>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Event Code</p>
                                            <p className="font-medium text-gray-900 dark:text-white">{reportData.event.eventCode}</p>
                                        </div>
                                        <div>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Event Type</p>
                                            <p className="font-medium text-gray-900 dark:text-white">{reportData.event.eventType.replace('_', ' ')}</p>
                                        </div>
                                        <div>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Event Date</p>
                                            <p className="font-medium text-gray-900 dark:text-white">
                                                {reportData.event.eventDate ? new Date(reportData.event.eventDate).toLocaleDateString() : 'Not set'}
                                            </p>
                                        </div>
                                        <div>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Venue</p>
                                            <p className="font-medium text-gray-900 dark:text-white">{reportData.event.venue || 'Not specified'}</p>
                                        </div>
                                        <div>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Status</p>
                                            <span className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                                                reportData.event.isActive
                                                    ? 'bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200'
                                                    : 'bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200'
                                            }`}>
{reportData.event.isActive ? 'Active' : 'Inactive'}
</span>
                                        </div>
                                    </div>
                                </div>

                                {/* Member Statistics */}
                                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                                    <div className="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-6">
                                        <div className="flex items-center">
                                            <div className="flex-shrink-0">
                                                <svg className="w-8 h-8 text-blue-600 dark:text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                                                </svg>
                                            </div>
                                            <div className="ml-4">
                                                <p className="text-sm font-medium text-blue-600 dark:text-blue-400">Total Members</p>
                                                <p className="text-2xl font-bold text-blue-900 dark:text-blue-100">{reportData.event.totalMembers}</p>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="bg-green-50 dark:bg-green-900/20 rounded-lg p-6">
                                        <div className="flex items-center">
                                            <div className="flex-shrink-0">
                                                <svg className="w-8 h-8 text-green-600 dark:text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                                                </svg>
                                            </div>
                                            <div className="ml-4">
                                                <p className="text-sm font-medium text-green-600 dark:text-green-400">Registered</p>
                                                <p className="text-2xl font-bold text-green-900 dark:text-green-100">{reportData.event.registeredMembers}</p>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="bg-purple-50 dark:bg-purple-900/20 rounded-lg p-6">
                                        <div className="flex items-center">
                                            <div className="flex-shrink-0">
                                                <svg className="w-8 h-8 text-purple-600 dark:text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                                                </svg>
                                            </div>
                                            <div className="ml-4">
                                                <p className="text-sm font-medium text-purple-600 dark:text-purple-400">Attending</p>
                                                <p className="text-2xl font-bold text-purple-900 dark:text-purple-100">{reportData.event.attendingMembers}</p>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="bg-yellow-50 dark:bg-yellow-900/20 rounded-lg p-6">
                                        <div className="flex items-center">
                                            <div className="flex-shrink-0">
                                                <svg className="w-8 h-8 text-yellow-600 dark:text-yellow-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
                                                </svg>
                                            </div>
                                            <div className="ml-4">
                                                <p className="text-sm font-medium text-yellow-600 dark:text-yellow-400">Special Vote</p>
                                                <p className="text-2xl font-bold text-yellow-900 dark:text-yellow-100">{reportData.event.specialVoteMembers}</p>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="bg-indigo-50 dark:bg-indigo-900/20 rounded-lg p-6">
                                        <div className="flex items-center">
                                            <div className="flex-shrink-0">
                                                <svg className="w-8 h-8 text-indigo-600 dark:text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
                                                </svg>
                                            </div>
                                            <div className="ml-4">
                                                <p className="text-sm font-medium text-indigo-600 dark:text-indigo-400">Voted</p>
                                                <p className="text-2xl font-bold text-indigo-900 dark:text-indigo-100">{reportData.event.votedMembers}</p>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="bg-red-50 dark:bg-red-900/20 rounded-lg p-6">
                                        <div className="flex items-center">
                                            <div className="flex-shrink-0">
                                                <svg className="w-8 h-8 text-red-600 dark:text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                                                </svg>
                                            </div>
                                            <div className="ml-4">
                                                <p className="text-sm font-medium text-red-600 dark:text-red-400">Checked In</p>
                                                <p className="text-2xl font-bold text-red-900 dark:text-red-100">{reportData.event.checkedInMembers}</p>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}

                        {activeTab === 'notifications' && reportData && (
                            <div className="space-y-6">
                                <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Notification Statistics</h3>
                                {/* Email Statistics */}
                                <div className="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-6">
                                    <h4 className="text-md font-semibold text-blue-900 dark:text-blue-100 mb-4 flex items-center">
                                        <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                                        </svg>
                                        Email Notifications
                                    </h4>
                                    <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                                        <div className="text-center">
                                            <p className="text-2xl font-bold text-green-600 dark:text-green-400">{reportData.notifications.primaryEmail.total_success}</p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Total Success</p>
                                        </div>
                                        <div className="text-center">
                                            <p className="text-2xl font-bold text-red-600 dark:text-red-400">{reportData.notifications.primaryEmail.total_failed}</p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Total Failed</p>
                                        </div>
                                        <div className="text-center">
                                            <p className="text-2xl font-bold text-blue-600 dark:text-blue-400">{reportData.notifications.primaryEmail.manual_success}</p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Manual Success</p>
                                        </div>
                                        <div className="text-center">
                                            <p className="text-2xl font-bold text-purple-600 dark:text-purple-400">{reportData.notifications.primaryEmail.auto_success}</p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Auto Success</p>
                                        </div>
                                        <div className="text-center">
                                            <p className="text-2xl font-bold text-orange-600 dark:text-orange-400">{reportData.notifications.primaryEmail.manual_failed}</p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Manual Failed</p>
                                        </div>
                                        <div className="text-center">
                                            <p className="text-2xl font-bold text-pink-600 dark:text-pink-400">{reportData.notifications.primaryEmail.auto_failed}</p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Auto Failed</p>
                                        </div>
                                    </div>
                                </div>

                                {/* SMS Statistics */}
                                <div className="bg-green-50 dark:bg-green-900/20 rounded-lg p-6">
                                    <h4 className="text-md font-semibold text-green-900 dark:text-green-100 mb-4 flex items-center">
                                        <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                                        </svg>
                                        SMS Notifications
                                    </h4>
                                    <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                                        <div className="text-center">
                                            <p className="text-2xl font-bold text-green-600 dark:text-green-400">{reportData.notifications.sms.total_success}</p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Total Success</p>
                                        </div>
                                        <div className="text-center">
                                            <p className="text-2xl font-bold text-red-600 dark:text-red-400">{reportData.notifications.sms.total_failed}</p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Total Failed</p>
                                        </div>
                                        <div className="text-center">
                                            <p className="text-2xl font-bold text-blue-600 dark:text-blue-400">{reportData.notifications.sms.manual_success}</p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Manual Success</p>
                                        </div>
                                        <div className="text-center">
                                            <p className="text-2xl font-bold text-purple-600 dark:text-purple-400">{reportData.notifications.sms.auto_success}</p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Auto Success</p>
                                        </div>
                                        <div className="text-center">
                                            <p className="text-2xl font-bold text-orange-600 dark:text-orange-400">{reportData.notifications.sms.manual_failed}</p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Manual Failed</p>
                                        </div>
                                        <div className="text-center">
                                            <p className="text-2xl font-bold text-pink-600 dark:text-pink-400">{reportData.notifications.sms.auto_failed}</p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Auto Failed</p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}

                        {activeTab === 'analytics' && (
                            <div className="space-y-6">
                                <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Analytics Dashboard</h3>
                                <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-8 text-center">
                                    <svg className="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                                    </svg>
                                    <p className="text-gray-600 dark:text-gray-400">Advanced analytics coming soon...</p>
                                    <p className="text-sm text-gray-500 dark:text-gray-500 mt-2">Charts, trends, and detailed insights will be available here.</p>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </Layout>
    );
}