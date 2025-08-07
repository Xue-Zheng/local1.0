import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/common/Layout';
import api from '@/services/api';
import { toast } from 'react-hot-toast';

interface PreferenceStats {
    totalResponses: number;
    attendingYes: number;
    attendingNo: number;
    specialVoteYes: number;
    specialVoteNo: number;
    specialVoteNotSure: number;
    venueDistribution: { [key: string]: number };
    timePreferences: { [key: string]: number };
    regionsBreakdown: {
        [key: string]: {
            total: number;
            attendingYes: number;
            attendingNo: number;
            specialVoteStats?: {
                yes: number;
                no: number;
                notSure: number;
            };
        };
    };
}

interface MemberPreference {
    id: number;
    name: string;
    membershipNumber: string;
    email: string;
    mobile: string;
    region: string;
    preferredAttending: boolean | null;
    preferenceSpecialVote: boolean | null;
    preferredVenue: string;
    preferredTimes: string[];
    workplaceInfo: string;
    suggestedVenue: string;
    additionalComments: string;
    submittedAt: string;
}

export default function BmmPreferencesOverview() {
    const router = useRouter();
    const [loading, setLoading] = useState(true);
    const [stats, setStats] = useState<PreferenceStats | null>(null);
    const [preferences, setPreferences] = useState<MemberPreference[]>([]);
    const [selectedRegion, setSelectedRegion] = useState('all');
    const [selectedTimeFilter, setSelectedTimeFilter] = useState('all');
    const [selectedAttendanceFilter, setSelectedAttendanceFilter] = useState('all');
    const [selectedSpecialVoteFilter, setSelectedSpecialVoteFilter] = useState('all');
    const [selectedContactFilter, setSelectedContactFilter] = useState('all');
    const [searchTerm, setSearchTerm] = useState('');
    const [showDetails, setShowDetails] = useState(false);
    const [selectedMember, setSelectedMember] = useState<MemberPreference | null>(null);
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);

    const fetchPreferenceStats = async () => {
        try {
            const response = await api.get('/admin/bmm/preference-statistics');
            if (response.data.status === 'success') {
                setStats(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch preference statistics:', error);
            toast.error('Failed to load preference statistics');
        }
    };

    const fetchPreferences = async (page: number = 0) => {
        try {
            const params = new URLSearchParams();
            params.append('page', page.toString());
            params.append('size', '50');

            if (selectedRegion !== 'all') params.append('region', selectedRegion);
            if (selectedTimeFilter !== 'all') params.append('timePreference', selectedTimeFilter);
            if (selectedAttendanceFilter !== 'all') params.append('attendance', selectedAttendanceFilter);
            if (selectedSpecialVoteFilter !== 'all') params.append('specialVote', selectedSpecialVoteFilter);
            if (selectedContactFilter !== 'all') params.append('contactFilter', selectedContactFilter);
            if (searchTerm) params.append('search', searchTerm);

            const response = await api.get(`/admin/bmm/preferences?${params.toString()}`);
            if (response.data.status === 'success') {
                setPreferences(response.data.data.content);
                setTotalPages(response.data.data.totalPages);
                setCurrentPage(page);
            }
        } catch (error) {
            console.error('Failed to fetch preferences:', error);
            toast.error('Failed to load preferences');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchPreferenceStats();
        fetchPreferences();
    }, []);

    useEffect(() => {
        fetchPreferences(0);
    }, [selectedRegion, selectedTimeFilter, selectedAttendanceFilter, selectedSpecialVoteFilter, selectedContactFilter, searchTerm]);

    const exportToCSV = () => {
        const headers = [
            'Name', 'Membership Number', 'Email', 'Mobile', 'Region',
            'Will Attend', 'Special Vote', 'Assigned Venue', 'Preferred Times',
            'Workplace Info', 'Suggested Venue', 'Comments', 'Submitted At'
        ];

        const csvContent = [
            headers.join(','),
            ...preferences.map(pref => [
                `"${pref.name}"`,
                pref.membershipNumber,
                pref.email || '',
                pref.mobile || '',
                pref.region,
                pref.preferredAttending === true ? 'Yes' : pref.preferredAttending === false ? 'No' : 'Not Indicated',
                pref.preferenceSpecialVote === true ? 'Yes' : pref.preferenceSpecialVote === false ? 'No' : pref.preferenceSpecialVote === null ? 'Not Sure' : 'N/A',
                `"${pref.preferredVenue || ''}"`,
                `"${pref.preferredTimes.join(', ')}"`,
                `"${pref.workplaceInfo || ''}"`,
                `"${pref.suggestedVenue || ''}"`,
                `"${pref.additionalComments || ''}"`,
                new Date(pref.submittedAt).toLocaleString()
            ].join(','))
        ].join('\n');

        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        const url = URL.createObjectURL(blob);
        link.setAttribute('href', url);
        link.setAttribute('download', `bmm-preferences-${new Date().toISOString().split('T')[0]}.csv`);
        link.style.visibility = 'hidden';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        toast.success('Preferences exported to CSV');
    };

    const navigateToEmail = (members: MemberPreference[]) => {
        const emailData = {
            targetMembers: members.map(m => ({
                id: m.id,
                name: m.name,
                email: m.email,
                membershipNumber: m.membershipNumber,
                region: m.region
            })),
            filterContext: 'bmm-preferences'
        };
        localStorage.setItem('preSelectedMembers', JSON.stringify(emailData));
        router.push('/admin/email');
    };

    const navigateToSMS = (members: MemberPreference[]) => {
        const smsData = {
            targetMembers: members
                .filter(m => m.mobile)
                .map(m => ({
                    id: m.id,
                    name: m.name,
                    mobile: m.mobile,
                    membershipNumber: m.membershipNumber,
                    region: m.region
                })),
            filterContext: 'bmm-preferences'
        };
        localStorage.setItem('preSelectedMembers', JSON.stringify(smsData));
        router.push('/admin/sms');
    };

    if (loading) {
        return (
            <Layout>
                <div className="flex items-center justify-center h-96">
                    <div className="text-center">
                        <div className="inline-block animate-spin rounded-full h-12 w-12 border-t-4 border-orange-500"></div>
                        <p className="mt-4 text-lg text-gray-700">Loading BMM Preferences...</p>
                    </div>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="py-6">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    {/* Header */}
                    <div className="bg-white rounded-lg shadow-md mb-6 p-6">
                        <div className="flex justify-between items-center">
                            <div>
                                <h1 className="text-3xl font-bold text-gray-900">📊 BMM Pre-Registration Overview</h1>
                                <p className="mt-2 text-gray-600">
                                    Complete overview of all collected BMM preferences and attendance intentions
                                </p>
                            </div>
                            <div className="flex space-x-3">
                                <button
                                    onClick={exportToCSV}
                                    className="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700 flex items-center space-x-2"
                                >
                                    <span>📥</span>
                                    <span>Export CSV</span>
                                </button>
                                <button
                                    onClick={() => router.push('/admin/bmm-management')}
                                    className="bg-orange-600 text-white px-4 py-2 rounded-lg hover:bg-orange-700"
                                >
                                    🏠 BMM Management
                                </button>
                            </div>
                        </div>
                    </div>

                    {/* Statistics Overview */}
                    {stats && (
                        <div className="mb-6">
                            {/* Main Stats */}
                            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
                                <div className="bg-white p-6 rounded-lg shadow border-l-4 border-orange-500">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-sm font-medium text-gray-500">Total Responses</p>
                                            <p className="text-3xl font-semibold text-gray-900">{stats.totalResponses}</p>
                                        </div>
                                        <div className="text-3xl">📝</div>
                                    </div>
                                </div>

                                <div className="bg-white p-6 rounded-lg shadow border-l-4 border-green-500">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-sm font-medium text-gray-500">Will Attend</p>
                                            <p className="text-3xl font-semibold text-gray-900">{stats.attendingYes}</p>
                                            <p className="text-xs text-gray-500">
                                                {Math.round((stats.attendingYes / stats.totalResponses) * 100)}%
                                            </p>
                                        </div>
                                        <div className="text-3xl">✅</div>
                                    </div>
                                </div>

                                <div className="bg-white p-6 rounded-lg shadow border-l-4 border-red-500">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-sm font-medium text-gray-500">Won't Attend</p>
                                            <p className="text-3xl font-semibold text-gray-900">{stats.attendingNo}</p>
                                            <p className="text-xs text-gray-500">
                                                {Math.round((stats.attendingNo / stats.totalResponses) * 100)}%
                                            </p>
                                        </div>
                                        <div className="text-3xl">❌</div>
                                    </div>
                                </div>

                                <div className="bg-white p-6 rounded-lg shadow border-l-4 border-amber-500">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-sm font-medium text-gray-500">Special Vote Interest</p>
                                            <p className="text-3xl font-semibold text-gray-900">{stats.specialVoteYes}</p>
                                            <p className="text-xs text-gray-500">
                                                {stats.specialVoteNotSure} not sure
                                            </p>
                                        </div>
                                        <div className="text-3xl">🗳️</div>
                                    </div>
                                </div>
                            </div>

                            {/* Regional Breakdown */}
                            <div className="bg-white rounded-lg shadow-md p-6 mb-6">
                                <h2 className="text-xl font-bold text-gray-900 mb-4">📍 Regional Breakdown</h2>
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                                    {Object.entries(stats.regionsBreakdown).map(([region, data]) => (
                                        <div key={region} className="border rounded-lg p-4">
                                            <h3 className="font-semibold text-lg text-gray-900 mb-3">{region}</h3>
                                            <div className="space-y-2">
                                                <div className="flex justify-between text-sm">
                                                    <span className="text-gray-600">Total Responses:</span>
                                                    <span className="font-medium">{data.total}</span>
                                                </div>
                                                <div className="flex justify-between text-sm">
                                                    <span className="text-gray-600">Will Attend:</span>
                                                    <span className="font-medium text-green-600">
                                                        {data.attendingYes} ({Math.round((data.attendingYes / data.total) * 100)}%)
                                                    </span>
                                                </div>
                                                <div className="flex justify-between text-sm">
                                                    <span className="text-gray-600">Won't Attend:</span>
                                                    <span className="font-medium text-red-600">
                                                        {data.attendingNo} ({Math.round((data.attendingNo / data.total) * 100)}%)
                                                    </span>
                                                </div>
                                                {data.specialVoteStats && (
                                                    <div className="pt-2 border-t">
                                                        <p className="text-xs font-medium text-gray-700 mb-1">Special Vote:</p>
                                                        <div className="flex justify-between text-xs">
                                                            <span>Yes: {data.specialVoteStats.yes}</span>
                                                            <span>No: {data.specialVoteStats.no}</span>
                                                            <span>Not Sure: {data.specialVoteStats.notSure}</span>
                                                        </div>
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            {/* Time Preferences */}
                            <div className="bg-white rounded-lg shadow-md p-6 mb-6">
                                <h2 className="text-xl font-bold text-gray-900 mb-4">🕐 Session Time Preferences</h2>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                    {Object.entries(stats.timePreferences)
                                        .sort(([,a], [,b]) => b - a)
                                        .map(([time, count]) => (
                                            <div key={time} className="flex items-center justify-between p-3 bg-orange-50 rounded-lg">
                                                <span className="font-medium text-gray-900">
                                                    {time === 'morning' ? 'Morning (9AM-12PM)' :
                                                        time === 'lunchtime' ? 'Lunchtime (12PM-2PM)' :
                                                            time === 'afternoon' ? 'Afternoon (2PM-5PM)' :
                                                                time === 'after_work' ? 'After Work (5PM-8PM)' :
                                                                    time === 'night_shift' ? 'Night Shift' : time}
                                                </span>
                                                <div className="flex items-center space-x-2">
                                                    <span className="text-2xl font-bold text-orange-600">{count}</span>
                                                    <span className="text-sm text-gray-500">
                                                        ({Math.round((count / stats.totalResponses) * 100)}%)
                                                    </span>
                                                </div>
                                            </div>
                                        ))}
                                </div>
                            </div>

                            {/* Time Preferences */}
                            <div className="bg-white rounded-lg shadow-md p-6 mb-6">
                                <h2 className="text-xl font-bold text-gray-900 mb-4">⏰ Time Preferences</h2>
                                <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
                                    {stats.timePreferences && Object.entries(stats.timePreferences).map(([time, count]) => (
                                        <div key={time} className="bg-gray-50 rounded-lg p-4 text-center">
                                            <p className="text-sm text-gray-600 capitalize">{time.replace('_', ' ')}</p>
                                            <p className="text-2xl font-bold text-gray-900">{count}</p>
                                            <p className="text-xs text-gray-500">
                                                {Math.round((count / stats.totalResponses) * 100)}%
                                            </p>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Filters */}
                    <div className="bg-white rounded-lg shadow-md p-6 mb-6">
                        <h2 className="text-xl font-bold text-gray-900 mb-4">🔍 Filter Preferences</h2>
                        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Region</label>
                                <select
                                    value={selectedRegion}
                                    onChange={(e) => setSelectedRegion(e.target.value)}
                                    className="w-full border rounded-lg px-3 py-2"
                                >
                                    <option value="all">All Regions</option>
                                    <option value="Northern Region">Northern Region</option>
                                    <option value="Central Region">Central Region</option>
                                    <option value="Southern Region">Southern Region</option>
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Attendance</label>
                                <select
                                    value={selectedAttendanceFilter}
                                    onChange={(e) => setSelectedAttendanceFilter(e.target.value)}
                                    className="w-full border rounded-lg px-3 py-2"
                                >
                                    <option value="all">All</option>
                                    <option value="yes">Will Attend</option>
                                    <option value="no">Won't Attend</option>
                                    <option value="undecided">Not Indicated</option>
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Time Preference</label>
                                <select
                                    value={selectedTimeFilter}
                                    onChange={(e) => setSelectedTimeFilter(e.target.value)}
                                    className="w-full border rounded-lg px-3 py-2"
                                >
                                    <option value="all">All Times</option>
                                    <option value="morning">Morning</option>
                                    <option value="lunchtime">Lunchtime</option>
                                    <option value="afternoon">Afternoon</option>
                                    <option value="after_work">After Work</option>
                                    <option value="night_shift">Night Shift</option>
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Search</label>
                                <input
                                    type="text"
                                    value={searchTerm}
                                    onChange={(e) => setSearchTerm(e.target.value)}
                                    placeholder="Name, workplace..."
                                    className="w-full border rounded-lg px-3 py-2"
                                />
                            </div>
                        </div>

                        {/* Special Vote Filter - Only show for Central and Southern */}
                        {(selectedRegion === 'Central Region' || selectedRegion === 'Southern Region') && (
                            <div className="mt-4">
                                <label className="block text-sm font-medium text-gray-700 mb-1">Special Vote Preference</label>
                                <select
                                    value={selectedSpecialVoteFilter}
                                    onChange={(e) => setSelectedSpecialVoteFilter(e.target.value)}
                                    className="w-full md:w-1/4 border rounded-lg px-3 py-2"
                                >
                                    <option value="all">All</option>
                                    <option value="yes">Yes - Qualify</option>
                                    <option value="no">No - Don't Qualify</option>
                                    <option value="notSure">Not Sure</option>
                                </select>
                            </div>
                        )}

                        {/* Contact Filter */}
                        <div className="mt-4">
                            <label className="block text-sm font-medium text-gray-700 mb-1">Contact Method</label>
                            <select
                                value={selectedContactFilter}
                                onChange={(e) => setSelectedContactFilter(e.target.value)}
                                className="w-full md:w-1/4 border rounded-lg px-3 py-2"
                            >
                                <option value="all">All Members</option>
                                <option value="email_only">Email Only</option>
                                <option value="phone_only">Phone Only</option>
                                <option value="both">Both Email & Phone</option>
                            </select>
                        </div>
                    </div>

                    {/* Preferences List */}
                    <div className="bg-white rounded-lg shadow-md">
                        <div className="px-6 py-4 border-b bg-gray-50 flex justify-between items-center">
                            <h2 className="text-lg font-bold text-gray-900">Individual Preferences ({preferences.length})</h2>
                            <div className="flex space-x-2">
                                <button
                                    onClick={() => navigateToEmail(preferences)}
                                    className="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-blue-700"
                                >
                                    📧 Email All
                                </button>
                                <button
                                    onClick={() => navigateToSMS(preferences)}
                                    className="bg-green-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-green-700"
                                >
                                    📱 SMS All
                                </button>
                            </div>
                        </div>

                        <div className="p-6">
                            <div className="overflow-x-auto">
                                <table className="min-w-full divide-y divide-gray-200">
                                    <thead className="bg-gray-50">
                                    <tr>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                            Member
                                        </th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                            Region
                                        </th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                            Attendance
                                        </th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                            Special Vote
                                        </th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                            Time Preferences
                                        </th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                            Actions
                                        </th>
                                    </tr>
                                    </thead>
                                    <tbody className="bg-white divide-y divide-gray-200">
                                    {preferences.map((pref) => (
                                        <tr key={pref.id} className="hover:bg-gray-50">
                                            <td className="px-6 py-4 whitespace-nowrap">
                                                <div>
                                                    <div className="text-sm font-medium text-gray-900">{pref.name}</div>
                                                    <div className="text-sm text-gray-500">{pref.membershipNumber}</div>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap">
                                                <span className="text-sm text-gray-900">{pref.region}</span>
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap">
                                                {pref.preferredAttending === true ? (
                                                    <span className="px-2 py-1 bg-green-100 text-green-800 text-xs rounded">✅ Yes</span>
                                                ) : pref.preferredAttending === false ? (
                                                    <span className="px-2 py-1 bg-red-100 text-red-800 text-xs rounded">❌ No</span>
                                                ) : (
                                                    <span className="px-2 py-1 bg-gray-100 text-gray-800 text-xs rounded">- Not Set</span>
                                                )}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap">
                                                {(pref.region === 'Central Region' || pref.region === 'Southern Region') ? (
                                                    pref.preferenceSpecialVote === true ? (
                                                        <span className="px-2 py-1 bg-amber-100 text-amber-800 text-xs rounded">Yes</span>
                                                    ) : pref.preferenceSpecialVote === false ? (
                                                        <span className="px-2 py-1 bg-gray-100 text-gray-800 text-xs rounded">No</span>
                                                    ) : pref.preferenceSpecialVote === null ? (
                                                        <span className="px-2 py-1 bg-yellow-100 text-yellow-800 text-xs rounded">Not Sure</span>
                                                    ) : (
                                                        <span className="text-xs text-gray-400">-</span>
                                                    )
                                                ) : (
                                                    <span className="text-xs text-gray-400">N/A</span>
                                                )}
                                            </td>
                                            <td className="px-6 py-4">
                                                <div className="flex flex-wrap gap-1">
                                                    {pref.preferredTimes.map(time => (
                                                        <span key={time} className="px-2 py-1 bg-orange-100 text-orange-700 text-xs rounded">
                                                                {time}
                                                            </span>
                                                    ))}
                                                </div>
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm">
                                                <button
                                                    onClick={() => {
                                                        setSelectedMember(pref);
                                                        setShowDetails(true);
                                                    }}
                                                    className="text-orange-600 hover:text-orange-800 mr-3"
                                                >
                                                    View Details
                                                </button>
                                                {pref.email && (
                                                    <button
                                                        onClick={() => navigateToEmail([pref])}
                                                        className="text-blue-600 hover:text-blue-800 mr-2"
                                                    >
                                                        📧
                                                    </button>
                                                )}
                                                {pref.mobile && (
                                                    <button
                                                        onClick={() => navigateToSMS([pref])}
                                                        className="text-green-600 hover:text-green-800"
                                                    >
                                                        📱
                                                    </button>
                                                )}
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>

                            {/* Pagination */}
                            {totalPages > 1 && (
                                <div className="flex justify-between items-center mt-4">
                                    <div className="text-sm text-gray-700">
                                        Page {currentPage + 1} of {totalPages}
                                    </div>
                                    <div className="flex space-x-2">
                                        <button
                                            onClick={() => fetchPreferences(currentPage - 1)}
                                            disabled={currentPage === 0}
                                            className="px-3 py-1 bg-gray-200 rounded disabled:opacity-50"
                                        >
                                            Previous
                                        </button>
                                        <button
                                            onClick={() => fetchPreferences(currentPage + 1)}
                                            disabled={currentPage >= totalPages - 1}
                                            className="px-3 py-1 bg-gray-200 rounded disabled:opacity-50"
                                        >
                                            Next
                                        </button>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Detail Modal */}
                    {showDetails && selectedMember && (
                        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                            <div className="bg-white rounded-lg shadow-xl max-w-3xl w-full m-4 max-h-[90vh] overflow-y-auto">
                                <div className="p-6">
                                    <div className="flex justify-between items-center mb-4">
                                        <h3 className="text-xl font-bold text-gray-900">
                                            BMM Preference Details: {selectedMember.name}
                                        </h3>
                                        <button
                                            onClick={() => setShowDetails(false)}
                                            className="text-gray-400 hover:text-gray-600 text-2xl"
                                        >
                                            ×
                                        </button>
                                    </div>

                                    <div className="space-y-4">
                                        {/* Basic Info */}
                                        <div className="grid grid-cols-2 gap-4 p-4 bg-gray-50 rounded-lg">
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Membership Number</label>
                                                <p className="text-sm text-gray-900">{selectedMember.membershipNumber}</p>
                                            </div>
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Region</label>
                                                <p className="text-sm text-gray-900">{selectedMember.region}</p>
                                            </div>
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Email</label>
                                                <p className="text-sm text-gray-900">{selectedMember.email || 'Not provided'}</p>
                                            </div>
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Mobile</label>
                                                <p className="text-sm text-gray-900">{selectedMember.mobile || 'Not provided'}</p>
                                            </div>
                                        </div>

                                        {/* Attendance & Special Vote */}
                                        <div className="grid grid-cols-2 gap-4">
                                            <div className="p-4 bg-orange-50 rounded-lg">
                                                <label className="block text-sm font-medium text-orange-800 mb-1">
                                                    Will Attend BMM?
                                                </label>
                                                <p className="text-lg font-semibold">
                                                    {selectedMember.preferredAttending === true ? '✅ Yes' :
                                                        selectedMember.preferredAttending === false ? '❌ No' :
                                                            '⏳ Not Indicated'}
                                                </p>
                                            </div>

                                            {(selectedMember.region === 'Central Region' || selectedMember.region === 'Southern Region') && (
                                                <div className="p-4 bg-amber-50 rounded-lg">
                                                    <label className="block text-sm font-medium text-amber-800 mb-1">
                                                        Qualify for Special Vote?
                                                    </label>
                                                    <p className="text-lg font-semibold">
                                                        {selectedMember.preferenceSpecialVote === true ? '✅ Yes' :
                                                            selectedMember.preferenceSpecialVote === false ? '❌ No' :
                                                                selectedMember.preferenceSpecialVote === null ? '❓ Not Sure' :
                                                                    '⏳ Not Indicated'}
                                                    </p>
                                                </div>
                                            )}
                                        </div>

                                        {/* Venue & Times */}
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">Assigned Venue</label>
                                            <p className="p-3 bg-green-50 rounded-lg text-green-900 font-medium">
                                                📍 {selectedMember.preferredVenue || 'No venue assigned'}
                                            </p>
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">Preferred Session Times</label>
                                            <div className="flex flex-wrap gap-2 mt-2">
                                                {selectedMember.preferredTimes.map(time => (
                                                    <span key={time} className="px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm">
                                                        {time === 'morning' ? '🌅 Morning (9AM-12PM)' :
                                                            time === 'lunchtime' ? '☀️ Lunchtime (12PM-2PM)' :
                                                                time === 'afternoon' ? '🌤️ Afternoon (2PM-5PM)' :
                                                                    time === 'after_work' ? '🌆 After Work (5PM-8PM)' :
                                                                        time === 'night_shift' ? '🌙 Night Shift' : time}
                                                    </span>
                                                ))}
                                            </div>
                                        </div>

                                        {/* Workplace Info */}
                                        {selectedMember.workplaceInfo && (
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 mb-1">Workplace Information</label>
                                                <p className="p-3 bg-gray-50 rounded-lg text-gray-900">
                                                    🏢 {selectedMember.workplaceInfo}
                                                </p>
                                            </div>
                                        )}

                                        {/* Suggested Venue */}
                                        {selectedMember.suggestedVenue && (
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 mb-1">Suggested Alternative Venue</label>
                                                <p className="p-3 bg-yellow-50 rounded-lg text-yellow-900">
                                                    💡 {selectedMember.suggestedVenue}
                                                </p>
                                            </div>
                                        )}

                                        {/* Additional Comments */}
                                        {selectedMember.additionalComments && (
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 mb-1">Additional Comments</label>
                                                <p className="p-3 bg-indigo-50 rounded-lg text-indigo-900 whitespace-pre-wrap">
                                                    💬 {selectedMember.additionalComments}
                                                </p>
                                            </div>
                                        )}

                                        {/* Submission Time */}
                                        <div className="text-sm text-gray-500 pt-2 border-t">
                                            Submitted: {new Date(selectedMember.submittedAt).toLocaleString()}
                                        </div>

                                        {/* Action Buttons */}
                                        <div className="flex justify-end space-x-3 pt-4 border-t">
                                            {selectedMember.email && (
                                                <button
                                                    onClick={() => {
                                                        setShowDetails(false);
                                                        navigateToEmail([selectedMember]);
                                                    }}
                                                    className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700"
                                                >
                                                    📧 Send Email
                                                </button>
                                            )}
                                            {selectedMember.mobile && (
                                                <button
                                                    onClick={() => {
                                                        setShowDetails(false);
                                                        navigateToSMS([selectedMember]);
                                                    }}
                                                    className="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700"
                                                >
                                                    📱 Send SMS
                                                </button>
                                            )}
                                            <button
                                                onClick={() => setShowDetails(false)}
                                                className="bg-gray-600 text-white px-4 py-2 rounded-lg hover:bg-gray-700"
                                            >
                                                Close
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </Layout>
    );
}