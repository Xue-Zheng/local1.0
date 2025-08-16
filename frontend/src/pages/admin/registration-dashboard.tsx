import React, { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/common/Layout';
import api from '@/services/api';

// Types
interface Event {
    id: number;
    name: string;
    eventCode: string;
    eventType: string;
}

interface RegistrationMember {
    id: number;
    membershipNumber: string;
    name: string;
    primaryEmail: string;
    telephoneMobile: string;
    registrationTime: string;
    hasRegistered: boolean;
    isAttending: boolean;
    isSpecialVote: boolean;
    specialVoteRequested: boolean;
    absenceReason: string;
    regionDesc: string;
    workplace: string;
    employer: string;
    siteIndustryDesc: string;
    siteSubIndustryDesc: string;
    emailSent: boolean;
    smsSent: boolean;
    hasEmail: boolean;
    hasMobile: boolean;
    address?: string;
    ageOfMember?: string;
    specialVoteApplicationReason?: string;
}

interface RegistrationAnalytics {
    totalInvited: number;
    registered: number;
    notRegistered: number;
    attending: number;
    notAttending: number;
    specialVote: number;
    incompleteRegistrations: number;
    registrationRate: number;
    attendanceRate: number;
    regionAnalysis: { [key: string]: { [key: string]: number } };
    industryAnalysis: { [key: string]: number };
    recent24hCount: number;
    recent24hRegistrations: Array<{
        name: string;
        membershipNumber: string;
        registrationTime: string;
        isAttending: boolean;
    }>;
}

interface FilterOptions {
    regions: string[];
    industries: string[];
    workplaces: string[];
    employers: string[];
    registrationStatuses: string[];
}

export default function RegistrationDashboardPage() {
    const router = useRouter();
    const [loading, setLoading] = useState(true);
    const [activeTab, setActiveTab] = useState<'analytics' | 'recent' | 'filtered'>('analytics');

    // Data states
    const [analytics, setAnalytics] = useState<RegistrationAnalytics | null>(null);
    const [recentRegistrations, setRecentRegistrations] = useState<RegistrationMember[]>([]);
    const [filteredMembers, setFilteredMembers] = useState<RegistrationMember[]>([]);
    const [filterOptions, setFilterOptions] = useState<FilterOptions | null>(null);
    const [events, setEvents] = useState<Event[]>([]);
    const [selectedEvent, setSelectedEvent] = useState<Event | null>(null);

    // Filter states
    const [filters, setFilters] = useState({
        registrationStatus: '',
        region: '',
        industry: '',
        workplace: '',
        employer: '',
        emailSent: '',
        smsSent: '',
        hoursBack: 24
    });

    // Fetch events
    const fetchEvents = async () => {
        try {
            const response = await api.get('/admin/events');
            if (response.data.status === 'success') {
                setEvents(response.data.data);
                // 自动选择第一个BMM事件
                const bmmEvent = response.data.data.find((event: Event) => event.eventType === 'BMM_VOTING');
                if (bmmEvent) {
                    setSelectedEvent(bmmEvent);
                } else if (response.data.data.length > 0) {
                    setSelectedEvent(response.data.data[0]);
                }
            }
        } catch (error) {
            console.error('Error fetching events:', error);
        }
    };

    // Fetch analytics data
    const fetchAnalytics = async () => {
        try {
            let url = '/admin/registration/registration-analytics';
            if (selectedEvent) {
                url += `?eventId=${selectedEvent.id}`;
            }
            const response = await api.get(url);
            if (response.data.status === 'success') {
                setAnalytics(response.data.data);
            }
        } catch (error) {
            console.error('Error fetching analytics:', error);
        }
    };

    // Fetch recent registrations
    const fetchRecentRegistrations = useCallback(async () => {
        try {
            const response = await api.get(`/admin/registration/recent-registrations?hoursBack=${filters.hoursBack}`);
            if (response.data.status === 'success') {
                setRecentRegistrations(response.data.data.registrations || []);
            }
        } catch (error) {
            console.error('Error fetching recent registrations:', error);
        }
    }, [filters.hoursBack]);

    // Fetch filter options
    const fetchFilterOptions = async () => {
        try {
            const response = await api.get('/admin/registration/filter-options');
            if (response.data.status === 'success') {
                const data = response.data.data;
                setFilterOptions({
                    regions: data.regions || [],
                    industries: data.industries || [],
                    workplaces: data.workplaces || [],
                    employers: data.employers || [],
                    registrationStatuses: ['registered', 'not_registered', 'attending', 'not_attending', 'special_vote', 'incomplete']
                });
                console.log('Filter options loaded:', {
                    regions: data.regions?.length || 0,
                    industries: data.industries?.length || 0,
                    workplaces: data.workplaces?.length || 0,
                    employers: data.employers?.length || 0,
                    subIndustries: data.subIndustries?.length || 0,
                    ages: data.ages?.length || 0,
                    bargainingGroups: data.bargainingGroups?.length || 0,
                    genders: data.genders?.length || 0,
                    ethnicRegions: data.ethnicRegions?.length || 0,
                    membershipTypes: data.membershipTypes?.length || 0,
                    branches: data.branches?.length || 0,
                    forums: data.forums?.length || 0
                });
            } else {
                console.error('Failed to load filter options:', response.data);
            }
        } catch (error) {
            console.error('Error fetching filter options:', error);
            setFilterOptions({
                regions: [],
                industries: [],
                workplaces: [],
                employers: [],
                registrationStatuses: ['registered', 'not_registered', 'attending', 'not_attending', 'special_vote', 'incomplete']
            });
        }
    };

    // Fetch filtered members
    const fetchFilteredMembers = async () => {
        try {
            const queryParams = new URLSearchParams();
            // 设置大的size值以获取所有数据
            queryParams.append('size', '50000');
            queryParams.append('page', '0');

            Object.entries(filters).forEach(([key, value]) => {
                if (value && key !== 'hoursBack') {
                    queryParams.append(key, value.toString());
                }
            });

            const response = await api.post('/admin/registration/members-by-criteria', {
                registrationStatus: filters.registrationStatus || null,
                region: filters.region || null,
                siteIndustryDesc: filters.industry || null,
                workplace: filters.workplace || null,
                employer: filters.employer || null,
                emailSent: filters.emailSent || null,
                smsSent: filters.smsSent || null
            });

            if (response.data.status === 'success') {
                const members = response.data.data.members || [];
                setFilteredMembers(members);
                console.log('Filtered members loaded:', {
                    total: members.length,
                    withEmail: members.filter((m: any) => m.hasEmail).length,
                    withMobile: members.filter((m: any) => m.hasMobile).length,
                    registered: members.filter((m: any) => m.hasRegistered).length
                });
            }
        } catch (error) {
            console.error('Error fetching filtered members:', error);
        }
    };

    // Export to CSV function
    const exportToCSV = (data: RegistrationMember[], filename: string) => {
        const headers = [
            'Name', 'Membership Number', 'Email', 'Mobile Phone', 'Region', 'Industry', 'Sub Industry',
            'Registration Status', 'Attending', 'Special Vote', 'Email Sent', 'SMS Sent'
        ];

        const csvContent = [
            headers.join(','),
            ...data.map(member => [
                `"${member.name}"`,
                member.membershipNumber,
                member.primaryEmail,
                member.telephoneMobile || '',
                `"${member.regionDesc || ''}"`,
                `"${member.siteIndustryDesc || ''}"`,
                `"${member.siteSubIndustryDesc || ''}"`,
                member.hasRegistered ? 'Registered' : 'Not Registered',
                member.isAttending ? 'Yes' : 'No',
                member.isSpecialVote ? 'Yes' : 'No',
                member.emailSent ? 'Yes' : 'No',
                member.smsSent ? 'Yes' : 'No'
            ].join(','))
        ].join('\n');

        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        const url = URL.createObjectURL(blob);
        link.setAttribute('href', url);
        link.setAttribute('download', `${filename}-${new Date().toISOString().split('T')[0]}.csv`);
        link.style.visibility = 'hidden';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    // Load initial data
    useEffect(() => {
        const loadData = async () => {
            setLoading(true);
            await Promise.all([
                fetchEvents(),
                fetchAnalytics(),
                fetchRecentRegistrations(),
                fetchFilterOptions()
            ]);
            setLoading(false);
        };

        loadData();
    }, [fetchRecentRegistrations]);

    // Status color helper
    const getStatusColor = (status: string) => {
        const colors = {
            registered: 'text-green-600 bg-green-100',
            attending: 'text-blue-600 bg-blue-100',
            not_attending: 'text-red-600 bg-red-100',
            special_vote: 'text-purple-600 bg-purple-100',
            incomplete: 'text-orange-600 bg-orange-100'
        };
        return colors[status as keyof typeof colors] || 'text-gray-600 bg-gray-100';
    };

    if (loading) {
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
                                        📊 Registration Management Dashboard
                                    </h1>
                                    <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
                                        Real-time registration tracking, analytics, and member management
                                    </p>
                                </div>
                                <button
                                    onClick={() => router.push('/admin/dashboard')}
                                    className="inline-flex items-center px-4 py-2 border border-gray-300 dark:border-gray-600 shadow-sm text-sm font-medium rounded-md text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700"
                                >
                                    Back to Dashboard
                                </button>
                            </div>

                            {/* BMM Registration Dashboard - No Event Selection Needed */}
                            <div className="mb-6">
                                <div className="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg p-4">
                                    <h2 className="text-lg font-medium text-blue-800 dark:text-blue-200 mb-2">
                                        🎯 BMM Registration Dashboard
                                    </h2>
                                    <p className="text-blue-700 dark:text-blue-300 text-sm">
                                        Comprehensive view of all BMM registration data and analytics
                                    </p>
                                </div>
                            </div>

                            {/* Tab Navigation */}
                            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md mb-6">
                                <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
                                    <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                                        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Registration Dashboard</h1>

                                        {/* Event Selector */}
                                        <div className="flex items-center gap-4">
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                                                    Select Event
                                                </label>
                                                <select
                                                    value={selectedEvent?.id || ''}
                                                    onChange={(e) => {
                                                        const eventId = e.target.value;
                                                        if (eventId) {
                                                            const event = events.find(ev => ev.id === parseInt(eventId));
                                                            setSelectedEvent(event || null);
                                                        } else {
                                                            setSelectedEvent(null);
                                                        }
                                                    }}
                                                    className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md dark:bg-gray-700 dark:text-white min-w-[200px]"
                                                >
                                                    <option value="">All Events</option>
                                                    {events.map(event => (
                                                        <option key={event.id} value={event.id}>
                                                            {event.name} ({event.eventType})
                                                        </option>
                                                    ))}
                                                </select>
                                            </div>

                                            <button
                                                onClick={() => {
                                                    fetchAnalytics();
                                                    fetchRecentRegistrations();
                                                    fetchFilteredMembers();
                                                }}
                                                className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-md"
                                            >
                                                🔄 Refresh
                                            </button>
                                        </div>
                                    </div>
                                </div>

                                <div className="px-6 py-2">
                                    <div className="flex space-x-1">
                                        {['analytics', 'recent', 'filtered'].map((tab) => (
                                            <button
                                                key={tab}
                                                onClick={() => setActiveTab(tab as any)}
                                                className={`px-4 py-2 text-sm font-medium rounded-t-lg transition-colors ${
                                                    activeTab === tab
                                                        ? 'bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-300 border-b-2 border-blue-500'
                                                        : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
                                                }`}
                                            >
                                                {tab === 'analytics' && '📊 Analytics'}
                                                {tab === 'recent' && '🕒 Recent Registrations'}
                                                {tab === 'filtered' && '🔍 Filtered Members'}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            </div>

                            {/* Analytics Tab */}
                            {activeTab === 'analytics' && analytics && (
                                <div className="space-y-6">
                                    {/* Key Metrics */}
                                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                                        {[
                                            { title: 'Total Invited', value: analytics.totalInvited, color: 'blue', icon: '👥' },
                                            { title: 'Registered', value: analytics.registered, color: 'green', icon: '✅', percentage: analytics.registrationRate },
                                            { title: 'Attending', value: analytics.attending, color: 'purple', icon: '🎯', percentage: analytics.attendanceRate },
                                            { title: 'Incomplete', value: analytics.incompleteRegistrations, color: 'orange', icon: '⚠️' }
                                        ].map((metric, index) => (
                                            <div key={index} className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow border border-gray-200 dark:border-gray-700">
                                                <div className="flex items-center justify-between">
                                                    <div>
                                                        <p className="text-sm font-medium text-gray-600 dark:text-gray-400">{metric.icon} {metric.title}</p>
                                                        <p className="text-2xl font-bold text-gray-900 dark:text-white">{metric.value.toLocaleString()}</p>
                                                        {metric.percentage && (
                                                            <p className="text-sm text-gray-500 dark:text-gray-400">{metric.percentage.toFixed(1)}%</p>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        ))}
                                    </div>

                                    {/* Recent Activity */}
                                    <div className="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg p-6">
                                        <h3 className="text-lg font-medium text-blue-800 dark:text-blue-200 mb-4">
                                            🕐 Last 24 Hours Activity
                                        </h3>
                                        <div className="flex items-center justify-between mb-4">
                                            <p className="text-blue-700 dark:text-blue-300">
                                                <strong>{analytics.recent24hCount}</strong> new registrations in the last 24 hours
                                            </p>
                                            <button
                                                onClick={() => setActiveTab('recent')}
                                                className="text-blue-600 hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-200 text-sm font-medium"
                                            >
                                                View All →
                                            </button>
                                        </div>
                                        {analytics.recent24hRegistrations.length > 0 && (
                                            <div className="space-y-2">
                                                {analytics.recent24hRegistrations.slice(0, 5).map((reg, index) => (
                                                    <div key={index} className="flex items-center justify-between text-sm">
                                                        <span className="text-blue-800 dark:text-blue-200">{reg.name} ({reg.membershipNumber})</span>
                                                        <span className="text-blue-600 dark:text-blue-400">
                                                            {reg.isAttending ? '✅ Attending' : '❌ Not Attending'}
                                                        </span>
                                                    </div>
                                                ))}
                                            </div>
                                        )}
                                    </div>

                                    {/* Industry Analysis */}
                                    {Object.keys(analytics.industryAnalysis).length > 0 && (
                                        <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow border border-gray-200 dark:border-gray-700">
                                            <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-4">🏭 Industry Breakdown</h3>
                                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                                {Object.entries(analytics.industryAnalysis)
                                                    .sort(([,a], [,b]) => b - a)
                                                    .slice(0, 6)
                                                    .map(([industry, count]) => (
                                                        <div key={industry} className="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-700 rounded">
                                                            <span className="text-sm text-gray-700 dark:text-gray-300 truncate">{industry}</span>
                                                            <span className="text-sm font-medium text-gray-900 dark:text-white">{count}</span>
                                                        </div>
                                                    ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* Recent Registrations Tab */}
                            {activeTab === 'recent' && (
                                <div className="space-y-4">
                                    <div className="flex items-center justify-between">
                                        <div className="flex items-center space-x-4">
                                            <h3 className="text-lg font-medium text-gray-900 dark:text-white">
                                                🕐 Recent Registrations
                                            </h3>
                                            <select
                                                value={filters.hoursBack}
                                                onChange={(e) => {
                                                    setFilters(prev => ({ ...prev, hoursBack: Number(e.target.value) }));
                                                    fetchRecentRegistrations();
                                                }}
                                                className="px-3 py-1 text-sm border border-gray-300 dark:border-gray-600 rounded-md dark:bg-gray-700 dark:text-white"
                                            >
                                                <option value={1}>Last 1 hour</option>
                                                <option value={6}>Last 6 hours</option>
                                                <option value={24}>Last 24 hours</option>
                                                <option value={72}>Last 3 days</option>
                                                <option value={168}>Last week</option>
                                            </select>
                                        </div>
                                        <button
                                            onClick={() => exportToCSV(recentRegistrations, 'recent-registrations')}
                                            className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 text-sm"
                                        >
                                            📊 Export CSV
                                        </button>
                                    </div>

                                    <div className="overflow-x-auto">
                                        <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                                            <thead className="bg-gray-50 dark:bg-gray-700">
                                            <tr>
                                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Member</th>
                                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Registration Time</th>
                                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Status</th>
                                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Region</th>
                                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Contact</th>
                                            </tr>
                                            </thead>
                                            <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                                            {recentRegistrations.map((member) => (
                                                <tr key={member.id} className="hover:bg-gray-50 dark:hover:bg-gray-700">
                                                    <td className="px-6 py-4 whitespace-nowrap">
                                                        <div>
                                                            <div className="text-sm font-medium text-gray-900 dark:text-white">{member.name}</div>
                                                            <div className="text-sm text-gray-500 dark:text-gray-400">{member.membershipNumber}</div>
                                                        </div>
                                                    </td>
                                                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-white">
                                                        {new Date(member.registrationTime).toLocaleString()}
                                                    </td>
                                                    <td className="px-6 py-4 whitespace-nowrap">
                                                        <div className="flex flex-col space-y-1">
                                                                <span className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                                                                    member.isAttending ? 'bg-green-100 text-green-800' :
                                                                        member.isSpecialVote ? 'bg-purple-100 text-purple-800' : 'bg-red-100 text-red-800'
                                                                }`}>
                                                                    {member.isAttending ? '✅ Attending' :
                                                                        member.isSpecialVote ? '🗳️ Special Vote' : '❌ Not Attending'}
                                                                </span>
                                                        </div>
                                                    </td>
                                                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-white">
                                                        {member.regionDesc}
                                                    </td>
                                                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-white">
                                                        <div className="flex space-x-2">
                                                            {member.emailSent && <span className="text-green-600">📧</span>}
                                                            {member.smsSent && <span className="text-blue-600">📱</span>}
                                                        </div>
                                                    </td>
                                                </tr>
                                            ))}
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            )}

                            {/* Advanced Search Tab */}
                            {activeTab === 'filtered' && filterOptions && (
                                <div className="space-y-6">
                                    <h3 className="text-lg font-medium text-gray-900 dark:text-white">
                                        🔍 Advanced Member Search & Filtering
                                    </h3>

                                    {/* Filter Controls */}
                                    <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-4 gap-4 p-4 bg-gray-50 dark:bg-gray-700 rounded-lg">
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Registration Status</label>
                                            <select
                                                value={filters.registrationStatus}
                                                onChange={(e) => setFilters(prev => ({ ...prev, registrationStatus: e.target.value }))}
                                                className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-md dark:bg-gray-800 dark:text-white"
                                            >
                                                <option value="">All Statuses</option>
                                                <option value="registered">✅ Registered</option>
                                                <option value="not_registered">❌ Not Registered</option>
                                                <option value="attending">🎯 Attending</option>
                                                <option value="not_attending">⛔ Not Attending</option>
                                                <option value="special_vote">🗳️ Special Vote</option>
                                                <option value="incomplete">⚠️ Incomplete</option>
                                            </select>
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Region</label>
                                            <select
                                                value={filters.region}
                                                onChange={(e) => setFilters(prev => ({ ...prev, region: e.target.value }))}
                                                className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-md dark:bg-gray-800 dark:text-white"
                                            >
                                                <option value="">All Regions</option>
                                                {filterOptions.regions.map(region => (
                                                    <option key={region} value={region}>{region}</option>
                                                ))}
                                            </select>
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Industry</label>
                                            <select
                                                value={filters.industry}
                                                onChange={(e) => setFilters(prev => ({ ...prev, industry: e.target.value }))}
                                                className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-md dark:bg-gray-800 dark:text-white"
                                            >
                                                <option value="">All Industries</option>
                                                {filterOptions.industries && filterOptions.industries.length > 0 ?
                                                    filterOptions.industries.map(industry => (
                                                        <option key={industry} value={industry}>{industry}</option>
                                                    )) :
                                                    <option disabled>No industries available</option>
                                                }
                                            </select>
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Email Status</label>
                                            <select
                                                value={filters.emailSent}
                                                onChange={(e) => setFilters(prev => ({ ...prev, emailSent: e.target.value }))}
                                                className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-md dark:bg-gray-800 dark:text-white"
                                            >
                                                <option value="">All Email Status</option>
                                                <option value="true">📧 Email Sent</option>
                                                <option value="false">📭 Email Not Sent</option>
                                            </select>
                                        </div>
                                    </div>

                                    <div className="flex space-x-4">
                                        <button
                                            onClick={fetchFilteredMembers}
                                            className="px-6 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 font-medium"
                                        >
                                            🔍 Apply Filters
                                        </button>
                                        <button
                                            onClick={() => {
                                                setFilters({
                                                    registrationStatus: '',
                                                    region: '',
                                                    industry: '',
                                                    workplace: '',
                                                    employer: '',
                                                    emailSent: '',
                                                    smsSent: '',
                                                    hoursBack: 24
                                                });
                                                setFilteredMembers([]);
                                            }}
                                            className="px-6 py-2 border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 rounded-md hover:bg-gray-50 dark:hover:bg-gray-700"
                                        >
                                            🔄 Clear Filters
                                        </button>
                                        {filteredMembers.length > 0 && (
                                            <button
                                                onClick={() => exportToCSV(filteredMembers, 'filtered-members')}
                                                className="px-6 py-2 bg-green-600 text-white rounded-md hover:bg-green-700"
                                            >
                                                📊 Export Results ({filteredMembers.length})
                                            </button>
                                        )}
                                    </div>

                                    {/* Filtered Results */}
                                    {filteredMembers.length > 0 && (
                                        <div className="overflow-x-auto">
                                            <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                                                <thead className="bg-gray-50 dark:bg-gray-700">
                                                <tr>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Member</th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Contact</th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Status</th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Details</th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Notifications</th>
                                                </tr>
                                                </thead>
                                                <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                                                {filteredMembers.map((member) => (
                                                    <tr key={member.id} className="hover:bg-gray-50 dark:hover:bg-gray-700">
                                                        <td className="px-6 py-4 whitespace-nowrap">
                                                            <div>
                                                                <div className="text-sm font-medium text-gray-900 dark:text-white">{member.name}</div>
                                                                <div className="text-sm text-gray-500 dark:text-gray-400">{member.membershipNumber}</div>
                                                            </div>
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap">
                                                            <div className="text-sm text-gray-900 dark:text-white">{member.primaryEmail}</div>
                                                            <div className="text-sm text-gray-500 dark:text-gray-400">{member.telephoneMobile}</div>
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap">
                                                            <div className="flex flex-col space-y-1">
                                                                    <span className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                                                                        member.isAttending ? 'bg-green-100 text-green-800' :
                                                                            member.specialVoteRequested ? 'bg-purple-100 text-purple-800' : 'bg-red-100 text-red-800'
                                                                    }`}>
                                                                        {member.isAttending ? '✅ Attending' :
                                                                            member.specialVoteRequested ? '🗳️ Special Vote' : '❌ Not Attending'}
                                                                    </span>
                                                            </div>
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-white">
                                                            <div>{member.regionDesc}</div>
                                                            <div className="text-gray-500 dark:text-gray-400">{member.siteSubIndustryDesc}</div>
                                                            {member.specialVoteRequested && (
                                                                <div className="mt-2 p-2 bg-purple-50 dark:bg-purple-900/20 rounded text-xs">
                                                                    <div className="font-semibold text-purple-800 dark:text-purple-200 mb-1">Special Vote Details:</div>
                                                                    {member.address && <div><strong>Address:</strong> {member.address}</div>}
                                                                    {member.workplace && <div><strong>Worksite:</strong> {member.workplace}</div>}
                                                                    {member.ageOfMember && <div><strong>Age:</strong> {member.ageOfMember}</div>}
                                                                    {member.absenceReason && <div><strong>Absence Reason:</strong> {member.absenceReason}</div>}
                                                                    {member.specialVoteApplicationReason && <div><strong>Application Reason:</strong> {member.specialVoteApplicationReason}</div>}
                                                                </div>
                                                            )}
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap text-sm">
                                                            <div className="flex space-x-2">
                                                                                                        <span className={`px-2 py-1 rounded text-xs ${member.emailSent ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}`}>
                                        {member.emailSent ? '📧 Sent' : '📭 Not Sent'}
                                                                    </span>
                                                                <span className={`px-2 py-1 rounded text-xs ${member.smsSent ? 'bg-blue-100 text-blue-800' : 'bg-gray-100 text-gray-800'}`}>
                                                                        {member.smsSent ? '📱 Sent' : '📵 Not Sent'}
                                                                    </span>
                                                            </div>
                                                        </td>
                                                    </tr>
                                                ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </Layout>
    );
}