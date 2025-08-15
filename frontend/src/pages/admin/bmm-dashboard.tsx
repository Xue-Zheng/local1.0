import { useState, useEffect } from 'react';
import Layout from '@/components/common/Layout';
import { toast } from 'react-toastify';
import api from '@/services/api';
import { useRouter } from 'next/router';

interface RegionStats {
    region: string;
    totalMembers: number;
    preRegistered: number;
    confirmed: number;
    checkedIn: number;
    notAttending: number;
    noResponse: number;
    emailSent: number;
    smsSent: number;
}

interface VenueStats {
    venue: string;
    expectedAttendees: number;
    actualCheckedIn: number;
    utilizationRate: number;
}

interface BMMStats {
    totalMembers: number;
    registeredMembers: number;
    attendingMembers: number;
    checkedInMembers: number;
    specialVoteMembers: number;
    hasEmailMembers: number;
    hasMobileMembers: number;
    emailSentCount: number;
    smsSentCount: number;
    regionBreakdown: { [key: string]: number };
    workplaceBreakdown: { [key: string]: number };
    currentBmmEvent: string;
}

interface CheckedInMember {
    id: number;
    membershipNumber: string;
    name: string;
    regionDesc: string;
    assignedVenue: string;
    checkInTime: string;
}

export default function BMMDashboard() {
    const [bmmStats, setBmmStats] = useState<BMMStats | null>(null);
    const [emailHistory, setEmailHistory] = useState<any[]>([]);
    const [loading, setLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [showTicketManager, setShowTicketManager] = useState(false);
    const [ticketStats, setTicketStats] = useState<any>(null);
    const [ticketPreview, setTicketPreview] = useState<any>(null);
    const [sendingTickets, setSendingTickets] = useState(false);
    const [venueStats, setVenueStats] = useState<any>(null);
    const [checkedInMembers, setCheckedInMembers] = useState<CheckedInMember[]>([]);
    const router = useRouter();

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }

        fetchBMMData();
        fetchTicketStats();
        fetchVenueStats();
        // Auto refresh every 30 seconds
        const interval = setInterval(() => {
            fetchBMMData();
            fetchVenueStats();
        }, 30000);
        return () => clearInterval(interval);
    }, [router]);

    const fetchBMMData = async () => {
        if (!loading) setRefreshing(true);
        try {
            // Fetch BMM statistics from reports API
            const statsResponse = await api.get('/admin/reports/members/overview');
            if (statsResponse.data.status === 'success') {
                setBmmStats(statsResponse.data.data);
            }

            // Fetch email history - get all BMM emails
            const emailResponse = await api.get('/admin/email/history?limit=100&type=BMM');
            if (emailResponse.data.status === 'success') {
                // Sort by date descending (newest first)
                const sortedEmails = (emailResponse.data.data || []).sort((a: any, b: any) => {
                    const dateA = new Date(a.sentAt || a.createdAt).getTime();
                    const dateB = new Date(b.sentAt || b.createdAt).getTime();
                    return dateB - dateA;
                });
                setEmailHistory(sortedEmails);
            }

            // Fetch check-in data
            const checkinResponse = await api.get('/admin/ticket-emails/bmm-checkin/list');
            if (checkinResponse.data.status === 'success') {
                setCheckedInMembers(checkinResponse.data.data.checkedInMembers || []);
            }
        } catch (error) {
            console.error('Failed to fetch BMM data:', error);
            toast.error('Failed to load BMM dashboard data');
        } finally {
            setLoading(false);
            setRefreshing(false);
        }
    };

    const fetchTicketStats = async () => {
        try {
            const response = await api.get('/admin/bmm/ticket-stats');
            if (response.data.status === 'success') {
                setTicketStats(response.data.data);
            }
        } catch (error) {
            console.error('Error fetching ticket stats:', error);
        }
    };

    const fetchVenueStats = async () => {
        try {
            const response = await api.get('/admin/bmm/regional-dashboard');
            if (response.data.status === 'success') {
                setVenueStats(response.data.data);
            }
        } catch (error) {
            console.error('Error fetching venue stats:', error);
        }
    };

    const previewTicketRecipients = async (previewType: string) => {
        try {
            const response = await api.post('/admin/bmm/preview-ticket-recipients', {
                previewType: previewType
            });
            if (response.data.status === 'success') {
                setTicketPreview(response.data.data);
                console.log('Ticket preview:', response.data.data);
            }
        } catch (error) {
            console.error('Error previewing ticket recipients:', error);
            toast.error('Failed to preview ticket recipients');
        }
    };

    const sendTicketsBatch = async (sendType: string, selectedMembers: string[] = []) => {
        try {
            setSendingTickets(true);
            const response = await api.post('/admin/bmm/send-bmm-tickets', {
                sendType: sendType,
                selectedMembers: selectedMembers
            });

            if (response.data.status === 'success') {
                const result = response.data.data;
                toast.success(`Tickets sent: ${result.emailsSent} emails, ${result.smsSent} SMS, ${result.failed} failed`);

                // Refresh statistics data
                await fetchTicketStats();
                await fetchBMMData();
            }
        } catch (error) {
            console.error('Error sending tickets batch:', error);
            toast.error('Failed to send tickets');
        } finally {
            setSendingTickets(false);
        }
    };

    const refreshData = () => {
        fetchBMMData();
        fetchVenueStats();
        toast.success('Data refreshed');
    };

    if (loading) {
        return (
            <Layout>
                <div className="flex items-center justify-center h-96">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
                </div>
            </Layout>
        );
    }

    if (!bmmStats) {
        return (
            <Layout>
                <div className="flex items-center justify-center h-96">
                    <div className="text-center">
                        <div className="text-xl text-gray-600">No BMM data available</div>
                        <button
                            onClick={refreshData}
                            className="mt-4 px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
                        >
                            Retry
                        </button>
                    </div>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="py-8">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    {/* Header */}
                    <div className="mb-8">
                        <div className="flex justify-between items-center">
                            <div>
                                <h1 className="text-3xl font-bold text-gray-900 dark:text-white">BMM 2025 Dashboard</h1>
                                <p className="mt-2 text-gray-600 dark:text-gray-300">
                                    {bmmStats.currentBmmEvent} - Real-time monitoring and management
                                </p>
                            </div>
                            <div className="flex space-x-3">
                                <button
                                    onClick={() => window.location.href = '/admin/bmm-venue-assignment'}
                                    className="px-4 py-2 bg-purple-600 text-white rounded hover:bg-purple-700 flex items-center"
                                >
                                    🎯 Venue Assignment
                                </button>
                                <button
                                    onClick={refreshData}
                                    disabled={refreshing}
                                    className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50 flex items-center"
                                >
                                    {refreshing ? (
                                        <>
                                            <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
                                            Refreshing...
                                        </>
                                    ) : (
                                        <>
                                            🔄 Refresh Data
                                        </>
                                    )}
                                </button>
                            </div>
                        </div>
                        <div className="mt-4 flex space-x-4 text-sm">
                            <span className="px-3 py-1 bg-blue-100 text-blue-800 rounded-full">📅 Pre-registration: Until 31 July 2025</span>
                            <span className="px-3 py-1 bg-green-100 text-green-800 rounded-full">✅ Confirmation: From 1 August 2025</span>
                            <span className="px-3 py-1 bg-purple-100 text-purple-800 rounded-full">🗳️ Central/Southern Special Vote</span>
                        </div>
                    </div>

                    {/* Overall Statistics */}
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
                        <div className="bg-white dark:bg-gray-800 overflow-hidden shadow rounded-lg">
                            <div className="p-5">
                                <div className="flex items-center">
                                    <div className="flex-shrink-0">
                                        <div className="w-8 h-8 bg-blue-500 rounded-md flex items-center justify-center">
                                            <span className="text-white font-bold">👥</span>
                                        </div>
                                    </div>
                                    <div className="ml-5 w-0 flex-1">
                                        <dl>
                                            <dt className="text-sm font-medium text-gray-500 dark:text-gray-400 truncate">Total Members</dt>
                                            <dd className="text-2xl font-bold text-gray-900 dark:text-white">{bmmStats.totalMembers.toLocaleString()}</dd>
                                        </dl>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="bg-white dark:bg-gray-800 overflow-hidden shadow rounded-lg">
                            <div className="p-5">
                                <div className="flex items-center">
                                    <div className="flex-shrink-0">
                                        <div className="w-8 h-8 bg-green-500 rounded-md flex items-center justify-center">
                                            <span className="text-white font-bold">✅</span>
                                        </div>
                                    </div>
                                    <div className="ml-5 w-0 flex-1">
                                        <dl>
                                            <dt className="text-sm font-medium text-gray-500 dark:text-gray-400 truncate">Registered</dt>
                                            <dd className="text-2xl font-bold text-green-600">{bmmStats.registeredMembers.toLocaleString()}</dd>
                                            <dd className="text-xs text-gray-500">
                                                {((bmmStats.registeredMembers / bmmStats.totalMembers) * 100).toFixed(1)}% of total
                                            </dd>
                                        </dl>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="bg-white dark:bg-gray-800 overflow-hidden shadow rounded-lg">
                            <div className="p-5">
                                <div className="flex items-center">
                                    <div className="flex-shrink-0">
                                        <div className="w-8 h-8 bg-purple-500 rounded-md flex items-center justify-center">
                                            <span className="text-white font-bold">🎫</span>
                                        </div>
                                    </div>
                                    <div className="ml-5 w-0 flex-1">
                                        <dl>
                                            <dt className="text-sm font-medium text-gray-500 dark:text-gray-400 truncate">Confirmed Attendance</dt>
                                            <dd className="text-2xl font-bold text-purple-600">{bmmStats.attendingMembers.toLocaleString()}</dd>
                                            <dd className="text-xs text-gray-500">
                                                {bmmStats.registeredMembers > 0 ? ((bmmStats.attendingMembers / bmmStats.registeredMembers) * 100).toFixed(1) : 0}% of registered
                                            </dd>
                                        </dl>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="bg-white dark:bg-gray-800 overflow-hidden shadow rounded-lg">
                            <div className="p-5">
                                <div className="flex items-center">
                                    <div className="flex-shrink-0">
                                        <div className="w-8 h-8 bg-orange-500 rounded-md flex items-center justify-center">
                                            <span className="text-white font-bold">📍</span>
                                        </div>
                                    </div>
                                    <div className="ml-5 w-0 flex-1">
                                        <dl>
                                            <dt className="text-sm font-medium text-gray-500 dark:text-gray-400 truncate">Checked In</dt>
                                            <dd className="text-2xl font-bold text-orange-600">{bmmStats.checkedInMembers.toLocaleString()}</dd>
                                            <dd className="text-xs text-gray-500">
                                                {bmmStats.attendingMembers > 0 ? ((bmmStats.checkedInMembers / bmmStats.attendingMembers) * 100).toFixed(1) : 0}% of attending
                                            </dd>
                                        </dl>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Communication Statistics */}
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
                        <div className="bg-white dark:bg-gray-800 overflow-hidden shadow rounded-lg">
                            <div className="p-5">
                                <div className="flex items-center">
                                    <div className="flex-shrink-0">
                                        <div className="w-8 h-8 bg-blue-500 rounded-md flex items-center justify-center">
                                            <span className="text-white font-bold">📧</span>
                                        </div>
                                    </div>
                                    <div className="ml-5 w-0 flex-1">
                                        <dl>
                                            <dt className="text-sm font-medium text-gray-500 dark:text-gray-400 truncate">Emails Sent</dt>
                                            <dd className="text-2xl font-bold text-blue-600">{bmmStats.emailSentCount?.toLocaleString() || 0}</dd>
                                            <dd className="text-xs text-gray-500">
                                                {bmmStats.hasEmailMembers > 0 ? ((bmmStats.emailSentCount / bmmStats.hasEmailMembers) * 100).toFixed(1) : 0}% coverage
                                            </dd>
                                        </dl>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="bg-white dark:bg-gray-800 overflow-hidden shadow rounded-lg">
                            <div className="p-5">
                                <div className="flex items-center">
                                    <div className="flex-shrink-0">
                                        <div className="w-8 h-8 bg-green-500 rounded-md flex items-center justify-center">
                                            <span className="text-white font-bold">📱</span>
                                        </div>
                                    </div>
                                    <div className="ml-5 w-0 flex-1">
                                        <dl>
                                            <dt className="text-sm font-medium text-gray-500 dark:text-gray-400 truncate">SMS Sent</dt>
                                            <dd className="text-2xl font-bold text-green-600">{bmmStats.smsSentCount?.toLocaleString() || 0}</dd>
                                            <dd className="text-xs text-gray-500">
                                                {bmmStats.hasMobileMembers > 0 ? ((bmmStats.smsSentCount / bmmStats.hasMobileMembers) * 100).toFixed(1) : 0}% coverage
                                            </dd>
                                        </dl>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="bg-white dark:bg-gray-800 overflow-hidden shadow rounded-lg">
                            <div className="p-5">
                                <div className="flex items-center">
                                    <div className="flex-shrink-0">
                                        <div className="w-8 h-8 bg-purple-500 rounded-md flex items-center justify-center">
                                            <span className="text-white font-bold">🗳️</span>
                                        </div>
                                    </div>
                                    <div className="ml-5 w-0 flex-1">
                                        <dl>
                                            <dt className="text-sm font-medium text-gray-500 dark:text-gray-400 truncate">Special Vote</dt>
                                            <dd className="text-2xl font-bold text-purple-600">{bmmStats.specialVoteMembers?.toLocaleString() || 0}</dd>
                                            <dd className="text-xs text-gray-500">Southern region non-attending members</dd>
                                        </dl>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="bg-white dark:bg-gray-800 overflow-hidden shadow rounded-lg">
                            <div className="p-5">
                                <div className="flex items-center">
                                    <div className="flex-shrink-0">
                                        <div className="w-8 h-8 bg-gray-500 rounded-md flex items-center justify-center">
                                            <span className="text-white font-bold">📊</span>
                                        </div>
                                    </div>
                                    <div className="ml-5 w-0 flex-1">
                                        <dl>
                                            <dt className="text-sm font-medium text-gray-500 dark:text-gray-400 truncate">Response Rate</dt>
                                            <dd className="text-2xl font-bold text-gray-600">
                                                {((bmmStats.registeredMembers / bmmStats.totalMembers) * 100).toFixed(1)}%
                                            </dd>
                                            <dd className="text-xs text-gray-500">Registered / Total</dd>
                                        </dl>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Region Breakdown */}
                    {bmmStats.regionBreakdown && Object.keys(bmmStats.regionBreakdown).length > 0 && (
                        <div className="bg-white shadow overflow-hidden sm:rounded-md mb-8">
                            <div className="px-4 py-5 sm:px-6">
                                <h3 className="text-lg leading-6 font-medium text-gray-900 dark:text-white">Regional Distribution</h3>
                                <p className="mt-1 max-w-2xl text-sm text-gray-500 dark:text-gray-400">BMM member distribution by region</p>
                            </div>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 p-6">
                                {Object.entries(bmmStats.regionBreakdown).map(([region, count]) => (
                                    <div key={region} className="bg-gray-50 p-4 rounded-lg">
                                        <div className="text-lg font-semibold text-gray-900 dark:text-white">{region}</div>
                                        <div className="text-2xl font-bold text-blue-600">{count.toLocaleString()}</div>
                                        <div className="text-sm text-gray-500 dark:text-gray-400">
                                            {((count / bmmStats.totalMembers) * 100).toFixed(1)}% of total
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Venue Preferences Statistics */}
                    {venueStats && venueStats.regions && (
                        <div className="bg-white shadow overflow-hidden sm:rounded-md mb-8">
                            <div className="px-4 py-5 sm:px-6">
                                <div className="flex justify-between items-center">
                                    <div>
                                        <h3 className="text-lg leading-6 font-medium text-gray-900">🏢 Venue Preference Statistics</h3>
                                        <p className="mt-1 max-w-2xl text-sm text-gray-500">Real-time venue/time preference analysis by region</p>
                                    </div>
                                    <div className="text-sm text-gray-500">
                                        Last Updated: {venueStats.lastUpdated ? new Date(venueStats.lastUpdated).toLocaleTimeString() : 'N/A'}
                                    </div>
                                </div>
                            </div>
                            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 p-6">
                                {Object.entries(venueStats.regions).map(([region, stats]: [string, any]) => (
                                    <div key={region} className="bg-gray-50 rounded-lg p-4">
                                        <div className="flex items-center justify-between mb-4">
                                            <h4 className="text-lg font-semibold text-gray-900">{region}</h4>
                                            <span className="px-2 py-1 bg-blue-100 text-blue-800 text-xs font-medium rounded-full">
                                                {stats.totalMembers} members
                                            </span>
                                        </div>

                                        <div className="space-y-3">
                                            <div className="flex justify-between items-center">
                                                <span className="text-sm text-gray-600">Registered:</span>
                                                <span className="font-medium text-green-600">{stats.registeredMembers}</span>
                                            </div>
                                            <div className="flex justify-between items-center">
                                                <span className="text-sm text-gray-600">Stage 1:</span>
                                                <span className="font-medium text-blue-600">{stats.stage1Members}</span>
                                            </div>
                                            <div className="flex justify-between items-center">
                                                <span className="text-sm text-gray-600">Attending:</span>
                                                <span className="font-medium text-purple-600">{stats.attendingMembers}</span>
                                            </div>
                                            <div className="flex justify-between items-center">
                                                <span className="text-sm text-gray-600">Special Vote:</span>
                                                <span className="font-medium text-orange-600">{stats.specialVoteMembers}</span>
                                            </div>
                                        </div>

                                        {/* Venue Preferences for this region */}
                                        {stats.venuePreferences && Object.keys(stats.venuePreferences).length > 0 && (
                                            <div className="mt-4 pt-4 border-t border-gray-200">
                                                <h5 className="text-sm font-medium text-gray-700 mb-2">Popular venue preferences:</h5>
                                                <div className="space-y-1">
                                                    {Object.entries(stats.venuePreferences)
                                                        .sort(([,a], [,b]) => (b as number) - (a as number))
                                                        .slice(0, 3)
                                                        .map(([venue, count]) => (
                                                            <div key={venue} className="flex justify-between items-center">
                                                                <span className="text-xs text-gray-600 truncate">{venue}</span>
                                                                <span className="text-xs font-medium text-gray-800">{count as number}</span>
                                                            </div>
                                                        ))}
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Email History */}
                    {emailHistory.length > 0 && (
                        <div className="bg-white shadow overflow-hidden sm:rounded-md mb-8">
                            <div className="px-4 py-5 sm:px-6">
                                <h3 className="text-lg leading-6 font-medium text-gray-900">Recent Email History</h3>
                                <p className="mt-1 max-w-2xl text-sm text-gray-500">Recent email sending records</p>
                            </div>
                            <div className="overflow-hidden">
                                <table className="min-w-full divide-y divide-gray-200">
                                    <thead className="bg-gray-50">
                                    <tr>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Time</th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Type</th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Subject</th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Sent Count</th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                                    </tr>
                                    </thead>
                                    <tbody className="bg-white divide-y divide-gray-200">
                                    {emailHistory.map((email, index) => (
                                        <tr key={index}>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                                                {new Date(email.sentAt || email.createdAt).toLocaleString()}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                                                {email.type || 'BMM Email'}
                                            </td>
                                            <td className="px-6 py-4 text-sm text-gray-900 max-w-xs truncate">
                                                {email.subject || 'BMM Notification'}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                                                {email.sentCount || email.totalSent || 0}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap">
                                                    <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                                                        email.status === 'SUCCESS' || email.status === 'success'
                                                            ? 'bg-green-100 text-green-800'
                                                            : 'bg-red-100 text-red-800'
                                                    }`}>
                                                        {email.status || 'Sent'}
                                                    </span>
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}

                    {/* Quick Actions */}
                    <div className="bg-white shadow overflow-hidden sm:rounded-md">
                        <div className="px-4 py-5 sm:px-6">
                            <h3 className="text-lg leading-6 font-medium text-gray-900">Quick Actions</h3>
                            <p className="mt-1 max-w-2xl text-sm text-gray-500">Common BMM management functions</p>
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 p-6">
                            <button
                                onClick={() => window.location.href = '/admin/bmm-emails'}
                                className="bg-blue-500 hover:bg-blue-600 text-white p-4 rounded-lg text-center transition-colors"
                            >
                                <div className="text-2xl mb-2">📧</div>
                                <div className="font-medium">BMM Email Management</div>
                                <div className="text-sm opacity-90">Stage-based email sending</div>
                            </button>
                            <button
                                onClick={() => window.location.href = '/admin/sms'}
                                className="bg-purple-500 hover:bg-purple-600 text-white p-4 rounded-lg text-center transition-colors"
                            >
                                <div className="text-2xl mb-2">📱</div>
                                <div className="font-medium">SMS Management</div>
                                <div className="text-sm opacity-90">Notifications for members without email</div>
                            </button>
                            <button
                                onClick={() => setShowTicketManager(!showTicketManager)}
                                className="bg-green-500 hover:bg-green-600 text-white p-4 rounded-lg text-center transition-colors"
                            >
                                <div className="text-2xl mb-2">🎫</div>
                                <div className="font-medium">Ticket Management</div>
                                <div className="text-sm opacity-90">Bulk ticket sending</div>
                            </button>
                            <button
                                onClick={() => window.location.href = '/admin/bmm-management'}
                                className="bg-orange-500 hover:bg-orange-600 text-white p-4 rounded-lg text-center transition-colors"
                            >
                                <div className="text-2xl mb-2">⚙️</div>
                                <div className="font-medium">Member Management</div>
                                <div className="text-sm opacity-90">Detailed data management</div>
                            </button>
                        </div>
                    </div>

                    {/* BMM Ticket Management Panel */}
                    {showTicketManager && (
                        <div className="bg-white shadow overflow-hidden sm:rounded-md">
                            <div className="px-4 py-5 sm:px-6 border-b border-gray-200">
                                <h3 className="text-lg leading-6 font-medium text-gray-900">🎫 BMM Ticket Management</h3>
                                <p className="mt-1 max-w-2xl text-sm text-gray-500">Manage ticket sending for confirmed attendees</p>
                            </div>

                            {/* Ticket Statistics */}
                            {ticketStats && (
                                <div className="px-6 py-4 bg-gray-50">
                                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                                        <div className="text-center">
                                            <div className="text-2xl font-bold text-blue-600">{ticketStats.confirmedAttendance}</div>
                                            <div className="text-sm text-gray-600">Confirmed Attendance</div>
                                        </div>
                                        <div className="text-center">
                                            <div className="text-2xl font-bold text-green-600">{ticketStats.totalTicketsSent}</div>
                                            <div className="text-sm text-gray-600">Tickets Sent</div>
                                        </div>
                                        <div className="text-center">
                                            <div className="text-2xl font-bold text-red-600">{ticketStats.pendingTickets}</div>
                                            <div className="text-sm text-gray-600">Pending</div>
                                        </div>
                                        <div className="text-center">
                                            <div className="text-2xl font-bold text-purple-600">{ticketStats.deliveryRate}</div>
                                            <div className="text-sm text-gray-600">Delivery Rate</div>
                                        </div>
                                    </div>

                                    <div className="mt-4 grid grid-cols-2 gap-4">
                                        <div className="bg-white p-3 rounded border">
                                            <div className="text-lg font-semibold text-gray-900">📧 Email Tickets: {ticketStats.ticketsEmailSent}</div>
                                            <div className="text-sm text-gray-600">Members with email receive tickets via email</div>
                                        </div>
                                        <div className="bg-white p-3 rounded border">
                                            <div className="text-lg font-semibold text-gray-900">📱 SMS Tickets: {ticketStats.ticketsSmsSent}</div>
                                            <div className="text-sm text-gray-600">Members without email receive tickets via SMS</div>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Send Options */}
                            <div className="p-6">
                                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
                                    <button
                                        onClick={() => previewTicketRecipients('all_confirmed')}
                                        className="bg-blue-500 hover:bg-blue-600 text-white p-4 rounded-lg text-center transition-colors"
                                    >
                                        <div className="text-lg font-medium">Preview All Pending</div>
                                        <div className="text-sm opacity-90">Confirmed attendance but no ticket sent</div>
                                    </button>

                                    <button
                                        onClick={() => previewTicketRecipients('missing_tickets')}
                                        className="bg-yellow-500 hover:bg-yellow-600 text-white p-4 rounded-lg text-center transition-colors"
                                    >
                                        <div className="text-lg font-medium">Preview Failed Sends</div>
                                        <div className="text-sm opacity-90">Members with failed ticket delivery</div>
                                    </button>

                                    <button
                                        onClick={() => previewTicketRecipients('email_recipients')}
                                        className="bg-green-500 hover:bg-green-600 text-white p-4 rounded-lg text-center transition-colors"
                                    >
                                        <div className="text-lg font-medium">Preview Email Tickets</div>
                                        <div className="text-sm opacity-90">Confirmed attendees with email</div>
                                    </button>

                                    <button
                                        onClick={() => previewTicketRecipients('sms_recipients')}
                                        className="bg-purple-500 hover:bg-purple-600 text-white p-4 rounded-lg text-center transition-colors"
                                    >
                                        <div className="text-lg font-medium">Preview SMS Tickets</div>
                                        <div className="text-sm opacity-90">Confirmed attendees without email</div>
                                    </button>
                                </div>

                                {/* Preview Results */}
                                {ticketPreview && (
                                    <div className="mt-6 border rounded-lg p-4 bg-gray-50">
                                        <div className="flex justify-between items-center mb-4">
                                            <h4 className="text-lg font-medium text-gray-900">
                                                Preview Results: {ticketPreview.previewType}
                                            </h4>
                                            <div className="text-sm text-gray-600">
                                                Total: {ticketPreview.totalCount} |
                                                Email: {ticketPreview.emailRecipients} |
                                                SMS: {ticketPreview.smsRecipients}
                                            </div>
                                        </div>

                                        {ticketPreview.totalCount > 0 ? (
                                            <>
                                                <div className="max-h-96 overflow-y-auto mb-4">
                                                    <table className="min-w-full divide-y divide-gray-200">
                                                        <thead className="bg-gray-100">
                                                        <tr>
                                                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Member #</th>
                                                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                                                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Region</th>
                                                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Method</th>
                                                            <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Ticket Status</th>
                                                        </tr>
                                                        </thead>
                                                        <tbody className="bg-white divide-y divide-gray-200">
                                                        {ticketPreview.members.slice(0, 20).map((member: any) => (
                                                            <tr key={member.id}>
                                                                <td className="px-4 py-2 text-sm text-gray-900">{member.membershipNumber}</td>
                                                                <td className="px-4 py-2 text-sm text-gray-900">{member.name}</td>
                                                                <td className="px-4 py-2 text-sm text-gray-900">{member.regionDesc}</td>
                                                                <td className="px-4 py-2 text-sm">
                                                                        <span className={`px-2 py-1 rounded text-xs ${
                                                                            member.deliveryMethod === 'email' ? 'bg-blue-100 text-blue-800' :
                                                                                member.deliveryMethod === 'sms' ? 'bg-purple-100 text-purple-800' :
                                                                                    'bg-gray-100 text-gray-800'
                                                                        }`}>
                                                                            {member.deliveryMethod === 'email' ? '📧 Email' :
                                                                                member.deliveryMethod === 'sms' ? '📱 SMS' : '❌ None'}
                                                                        </span>
                                                                </td>
                                                                <td className="px-4 py-2 text-sm text-gray-900">{member.ticketStatus || 'PENDING'}</td>
                                                            </tr>
                                                        ))}
                                                        </tbody>
                                                    </table>
                                                    {ticketPreview.members.length > 20 && (
                                                        <div className="text-center py-2 text-sm text-gray-500">
                                                            Showing first 20 of {ticketPreview.totalCount} records
                                                        </div>
                                                    )}
                                                </div>

                                                <div className="flex justify-center space-x-4">
                                                    <button
                                                        onClick={() => sendTicketsBatch(ticketPreview.previewType.replace('_recipients', ''))}
                                                        disabled={sendingTickets}
                                                        className="bg-green-600 hover:bg-green-700 disabled:bg-gray-400 text-white px-6 py-2 rounded-lg font-medium flex items-center"
                                                    >
                                                        {sendingTickets ? (
                                                            <>
                                                                <svg className="animate-spin -ml-1 mr-3 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                                                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                                                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                                                </svg>
                                                                Sending...
                                                            </>
                                                        ) : (
                                                            <>
                                                                🎫 Confirm Send Tickets ({ticketPreview.totalCount} people)
                                                            </>
                                                        )}
                                                    </button>

                                                    <button
                                                        onClick={() => setTicketPreview(null)}
                                                        className="bg-gray-500 hover:bg-gray-600 text-white px-6 py-2 rounded-lg font-medium"
                                                    >
                                                        Cancel
                                                    </button>
                                                </div>
                                            </>
                                        ) : (
                                            <div className="text-center py-8 text-gray-500">
                                                No eligible members found for ticket sending
                                            </div>
                                        )}
                                    </div>
                                )}
                            </div>
                        </div>
                    )}

                    {/* Recent Check-ins */}
                    <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 mt-6">
                        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                            🎫 Recent Check-ins ({checkedInMembers.length})
                        </h3>
                        {checkedInMembers.length === 0 ? (
                            <p className="text-gray-500 dark:text-gray-400">No check-ins yet</p>
                        ) : (
                            <div className="overflow-x-auto">
                                <table className="min-w-full text-sm">
                                    <thead>
                                    <tr className="border-b dark:border-gray-700">
                                        <th className="text-left py-2">Name</th>
                                        <th className="text-left py-2">Region</th>
                                        <th className="text-left py-2">Venue</th>
                                        <th className="text-left py-2">Check-in Time</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {checkedInMembers.slice(0, 10).map((member) => (
                                        <tr key={member.id} className="border-b dark:border-gray-700">
                                            <td className="py-2">
                                                <div>
                                                    <div className="font-medium">{member.name}</div>
                                                    <div className="text-xs text-gray-500">{member.membershipNumber}</div>
                                                </div>
                                            </td>
                                            <td className="py-2">{member.regionDesc}</td>
                                            <td className="py-2">{member.assignedVenue || 'N/A'}</td>
                                            <td className="py-2">
                                                {new Date(member.checkInTime).toLocaleString('en-NZ', {
                                                    month: 'short',
                                                    day: 'numeric',
                                                    hour: '2-digit',
                                                    minute: '2-digit'
                                                })}
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                                {checkedInMembers.length > 10 && (
                                    <div className="text-center py-2 text-sm text-gray-500">
                                        Showing latest 10 of {checkedInMembers.length} check-ins
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </Layout>
    );
}