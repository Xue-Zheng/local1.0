import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/common/Layout';
import api from '@/services/api';
import { toast } from 'react-hot-toast';

interface AttendanceStats {
    totalConfirmations: number;
    attendingCount: number;
    notAttendingCount: number;
    specialVoteEligible: number;
    specialVoteRequested: number; // 已申请special vote的数量
    ticketsSent: number;
    checkedIn: number;
    venueDistribution: { [key: string]: number };
    sessionDistribution: { [key: string]: number };
    regionsBreakdown: {
        [key: string]: {
            total: number;
            attending: number;
            notAttending: number;
            specialVoteEligible?: number;
            specialVoteRequested?: number; // 已申请special vote的数量
            ticketsSent: number;
            checkedIn: number;
        };
    };
}

interface MemberConfirmation {
    id: number;
    name: string;
    membershipNumber: string;
    email: string;
    mobile: string;
    region: string;
    isAttending: boolean;
    bmmRegistrationStage: string;
    specialVoteEligible: boolean;
    specialVoteRequested?: boolean;
    bmmSpecialVoteStatus?: string;
    specialVoteApplicationReason?: string;
    assignedVenue: string;
    assignedVenueFinal?: string;
    assignedSession: string;
    assignedDateTime?: string;
    venueAddress?: string;
    preferredTimesJson?: string;
    absenceReason?: string;
    ticketEmailSent: boolean;
    checkedIn: boolean;
    checkInTime?: string;
    checkInMethod?: string;
    checkInAdminName?: string;
    checkInVenue?: string;
    confirmationDate?: string;
    declinedReason?: string;
    forumDesc?: string;
    // Financial form data from Stage 2
    financialFormId?: number;
    phoneWork?: string;
    phoneHome?: string;
    postalAddress?: string;
    payrollNumber?: string;
    siteCode?: string;
    employmentStatus?: string;
    department?: string;
    jobTitle?: string;
    location?: string;
    dateOfBirth?: string;
    employer?: string;
    // Additional fields for special vote details
    address?: string;
    workplace?: string;
    ageOfMember?: string;
    telephoneMobile?: string;
    primaryEmail?: string;
}

export default function BmmAttendanceOverview() {
    const router = useRouter();
    const [loading, setLoading] = useState(true);
    const [event, setEvent] = useState<any>(null);
    const [stats, setStats] = useState<AttendanceStats | null>(null);
    const [confirmations, setConfirmations] = useState<MemberConfirmation[]>([]);
    const [selectedRegion, setSelectedRegion] = useState('all');
    const [selectedVenue, setSelectedVenue] = useState('all');
    const [selectedStatus, setSelectedStatus] = useState('all');
    const [selectedSpecialVote, setSelectedSpecialVote] = useState('all');
    const [searchTerm, setSearchTerm] = useState('');
    const [showDetails, setShowDetails] = useState(false);
    const [selectedMember, setSelectedMember] = useState<MemberConfirmation | null>(null);
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);

    const fetchEvent = async () => {
        try {
            const { data } = await api.get(`/admin/events/1`);
            if (data.status === 'success') {
                setEvent(data.data);
            }
        } catch (error) {
            console.error('Failed to fetch event:', error);
            toast.error('Failed to load event details');
        }
    };


    const fetchAttendanceStats = async () => {
        try {
            const response = await api.get(`/admin/bmm/attendance-statistics?eventId=1`);
            if (response.data.status === 'success') {
                setStats(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch attendance statistics:', error);
            toast.error('Failed to load attendance statistics');
        }
    };

    const fetchConfirmations = async (page: number = 0) => {
        try {
            const params = new URLSearchParams();
            params.append('eventId', '1');
            params.append('page', page.toString());
            params.append('size', '50');
            params.append('stage', 'ATTENDANCE_CONFIRMED,ATTENDANCE_DECLINED');

            if (selectedRegion !== 'all') params.append('region', selectedRegion);
            if (selectedVenue !== 'all') params.append('venue', selectedVenue);
            if (selectedStatus !== 'all') params.append('attendanceStatus', selectedStatus);
            if (selectedSpecialVote !== 'all') params.append('specialVote', selectedSpecialVote);
            if (searchTerm) params.append('search', searchTerm);

            const response = await api.get(`/admin/bmm/confirmations?${params.toString()}`);
            if (response.data.status === 'success') {
                setConfirmations(response.data.data.content || response.data.data);
                if (response.data.data.totalPages) {
                    setTotalPages(response.data.data.totalPages);
                }
                setCurrentPage(page);
            }
        } catch (error) {
            console.error('Failed to fetch confirmations:', error);
            toast.error('Failed to load confirmations');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchEvent();
        fetchAttendanceStats();
        fetchConfirmations();
    }, []);

    useEffect(() => {
        fetchConfirmations(0);
    }, [selectedRegion, selectedVenue, selectedStatus, selectedSpecialVote, searchTerm]);

    const exportToCSV = () => {
        const headers = [
            'Name', 'Membership Number', 'Email', 'Mobile', 'Region', 'Forum',
            'Attending', 'Special Vote Eligible', 'Assigned Venue', 'Session',
            'Ticket Sent', 'Checked In', 'Check-in Time', 'Confirmation Date'
        ];

        const csvContent = [
            headers.join(','),
            ...confirmations.map(conf => [
                `"${conf.name}"`,
                conf.membershipNumber,
                conf.email || '',
                conf.mobile || '',
                conf.region,
                conf.forumDesc || '',
                conf.isAttending ? 'Yes' : 'No',
                conf.specialVoteEligible ? 'Yes' : 'No',
                `"${conf.assignedVenue || ''}"`,
                `"${conf.assignedSession || ''}"`,
                conf.ticketEmailSent ? 'Yes' : 'No',
                conf.checkedIn ? 'Yes' : 'No',
                conf.checkInTime || '',
                conf.confirmationDate || ''
            ].join(','))
        ].join('\n');

        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        const url = URL.createObjectURL(blob);
        link.setAttribute('href', url);
        link.setAttribute('download', `bmm-confirmations-${new Date().toISOString().split('T')[0]}.csv`);
        link.style.visibility = 'hidden';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        toast.success('Confirmations exported to CSV');
    };

    const getRegionBadgeColor = (region: string) => {
        switch (region) {
            case 'Northern':
                return 'bg-blue-100 text-blue-800';
            case 'Central':
                return 'bg-green-100 text-green-800';
            case 'Southern':
                return 'bg-purple-100 text-purple-800';
            default:
                return 'bg-gray-100 text-gray-800';
        }
    };

    if (loading) {
        return (
            <Layout>
                <div className="flex items-center justify-center h-96">
                    <div className="text-center">
                        <div className="inline-block animate-spin rounded-full h-12 w-12 border-t-4 border-orange-500"></div>
                        <p className="mt-4 text-lg text-gray-700">Loading BMM Attendance Confirmations...</p>
                    </div>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="py-6">
                <div className="max-w-7xl mx-auto">
                    {/* Header */}
                    <div className="bg-white rounded-lg shadow-md mb-6 p-6">
                        <div className="flex justify-between items-center">
                            <div>
                                <h1 className="text-3xl font-bold text-gray-900">📊 BMM Attendance Confirmation Overview</h1>
                                <p className="mt-2 text-gray-600">
                                    {event ? `${event.eventName} - ` : ''}Stage 2: Attendance confirmations and venue assignments
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
                            <div className="grid grid-cols-1 md:grid-cols-6 gap-4 mb-6">
                                <div className="bg-white p-6 rounded-lg shadow border-l-4 border-orange-500">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-sm font-medium text-gray-500">Total Confirmations</p>
                                            <p className="text-3xl font-semibold text-gray-900">{stats.totalConfirmations}</p>
                                        </div>
                                        <div className="text-3xl">📋</div>
                                    </div>
                                </div>

                                <div className="bg-white p-6 rounded-lg shadow border-l-4 border-green-500">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-sm font-medium text-gray-500">Attending</p>
                                            <p className="text-3xl font-semibold text-gray-900">{stats.attendingCount}</p>
                                            <p className="text-xs text-gray-500">
                                                {stats.totalConfirmations > 0 ? Math.round((stats.attendingCount / stats.totalConfirmations) * 100) : 0}%
                                            </p>
                                        </div>
                                        <div className="text-3xl">✅</div>
                                    </div>
                                </div>

                                <div className="bg-white p-6 rounded-lg shadow border-l-4 border-red-500">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-sm font-medium text-gray-500">Not Attending</p>
                                            <p className="text-3xl font-semibold text-gray-900">{stats.notAttendingCount}</p>
                                            <p className="text-xs text-gray-500">
                                                {stats.totalConfirmations > 0 ? Math.round((stats.notAttendingCount / stats.totalConfirmations) * 100) : 0}%
                                            </p>
                                        </div>
                                        <div className="text-3xl">❌</div>
                                    </div>
                                </div>

                                <div className="bg-white p-6 rounded-lg shadow border-l-4 border-amber-500">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-sm font-medium text-gray-500">Special Vote Applied</p>
                                            <p className="text-3xl font-semibold text-gray-900">{stats.specialVoteRequested || 0}</p>
                                            <p className="text-xs text-gray-500">of {stats.specialVoteEligible || 0} eligible</p>
                                        </div>
                                        <div className="text-3xl">🗳️</div>
                                    </div>
                                </div>

                                <div className="bg-white p-6 rounded-lg shadow border-l-4 border-blue-500">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-sm font-medium text-gray-500">Tickets Sent</p>
                                            <p className="text-3xl font-semibold text-gray-900">{stats.ticketsSent}</p>
                                            <p className="text-xs text-gray-500">
                                                {stats.attendingCount > 0 ? Math.round((stats.ticketsSent / stats.attendingCount) * 100) : 0}%
                                            </p>
                                        </div>
                                        <div className="text-3xl">🎫</div>
                                    </div>
                                </div>

                                <div className="bg-white p-6 rounded-lg shadow border-l-4 border-purple-500">
                                    <div className="flex items-center justify-between">
                                        <div>
                                            <p className="text-sm font-medium text-gray-500">Checked In</p>
                                            <p className="text-3xl font-semibold text-gray-900">{stats.checkedIn}</p>
                                            <p className="text-xs text-gray-500">
                                                {stats.attendingCount > 0 ? Math.round((stats.checkedIn / stats.attendingCount) * 100) : 0}%
                                            </p>
                                        </div>
                                        <div className="text-3xl">✔️</div>
                                    </div>
                                </div>
                            </div>

                            {/* Regional Breakdown */}
                            <div className="bg-white rounded-lg shadow-md p-6 mb-6">
                                <h2 className="text-xl font-bold text-gray-900 mb-4">📍 Regional Attendance Breakdown</h2>
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                                    {Object.entries(stats.regionsBreakdown).map(([region, data]) => (
                                        <div key={region} className="border rounded-lg p-4">
                                            <h3 className="font-semibold text-lg text-gray-900 mb-3">{region} Region</h3>
                                            <div className="space-y-2">
                                                <div className="flex justify-between text-sm">
                                                    <span className="text-gray-600">Total Confirmations:</span>
                                                    <span className="font-medium">{data.total}</span>
                                                </div>
                                                <div className="flex justify-between text-sm">
                                                    <span className="text-gray-600">Attending:</span>
                                                    <span className="font-medium text-green-600">
                                                        {data.attending} ({data.total > 0 ? Math.round((data.attending / data.total) * 100) : 0}%)
                                                    </span>
                                                </div>
                                                <div className="flex justify-between text-sm">
                                                    <span className="text-gray-600">Not Attending:</span>
                                                    <span className="font-medium text-red-600">
                                                        {data.notAttending} ({data.total > 0 ? Math.round((data.notAttending / data.total) * 100) : 0}%)
                                                    </span>
                                                </div>
                                                {(region === 'Central' || region === 'Southern') && (
                                                    <>
                                                        {data.specialVoteEligible !== undefined && (
                                                            <div className="flex justify-between text-sm pt-2 border-t">
                                                                <span className="text-gray-600">Special Vote Eligible:</span>
                                                                <span className="font-medium text-gray-600">{data.specialVoteEligible}</span>
                                                            </div>
                                                        )}
                                                        {data.specialVoteRequested !== undefined && (
                                                            <div className="flex justify-between text-sm">
                                                                <span className="text-gray-600">Special Vote Applied:</span>
                                                                <span className="font-medium text-amber-600">{data.specialVoteRequested}</span>
                                                            </div>
                                                        )}
                                                    </>
                                                )}
                                                <div className="flex justify-between text-sm">
                                                    <span className="text-gray-600">Tickets Sent:</span>
                                                    <span className="font-medium text-blue-600">{data.ticketsSent}</span>
                                                </div>
                                                <div className="flex justify-between text-sm">
                                                    <span className="text-gray-600">Checked In:</span>
                                                    <span className="font-medium text-purple-600">{data.checkedIn}</span>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>


                            {/* Session Distribution */}
                            {stats.sessionDistribution && Object.keys(stats.sessionDistribution).length > 0 && (
                                <div className="bg-white rounded-lg shadow-md p-6 mb-6">
                                    <h2 className="text-xl font-bold text-gray-900 mb-4">🕐 Session Time Distribution</h2>
                                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                        {Object.entries(stats.sessionDistribution).map(([session, count]) => (
                                            <div key={session} className="bg-orange-50 rounded-lg p-4 text-center">
                                                <p className="text-lg font-medium text-gray-900">{session}</p>
                                                <p className="text-3xl font-bold text-orange-600">{count}</p>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    )}

                    {/* Filters */}
                    <div className="bg-white rounded-lg shadow-md p-6 mb-6">
                        <h2 className="text-xl font-bold text-gray-900 mb-4">🔍 Filter Confirmations</h2>
                        <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Region</label>
                                <select
                                    value={selectedRegion}
                                    onChange={(e) => setSelectedRegion(e.target.value)}
                                    className="w-full border rounded-lg px-3 py-2"
                                >
                                    <option value="all">All Regions</option>
                                    <option value="Northern">Northern Region</option>
                                    <option value="Central">Central Region</option>
                                    <option value="Southern">Southern Region</option>
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Attendance Status</label>
                                <select
                                    value={selectedStatus}
                                    onChange={(e) => setSelectedStatus(e.target.value)}
                                    className="w-full border rounded-lg px-3 py-2"
                                >
                                    <option value="all">All Status</option>
                                    <option value="confirmed">Confirmed Attending</option>
                                    <option value="declined">Declined</option>
                                </select>
                            </div>


                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Special Vote</label>
                                <select
                                    value={selectedSpecialVote}
                                    onChange={(e) => setSelectedSpecialVote(e.target.value)}
                                    className="w-full border rounded-lg px-3 py-2"
                                >
                                    <option value="all">All</option>
                                    <option value="requested">Requested</option>
                                    <option value="not_requested">Not Requested</option>
                                    <option value="eligible">Eligible</option>
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Search</label>
                                <input
                                    type="text"
                                    value={searchTerm}
                                    onChange={(e) => setSearchTerm(e.target.value)}
                                    placeholder="Name, number..."
                                    className="w-full border rounded-lg px-3 py-2"
                                />
                            </div>
                        </div>
                    </div>

                    {/* Confirmations List */}
                    <div className="bg-white rounded-lg shadow-md">
                        <div className="px-6 py-4 border-b bg-gray-50">
                            <h2 className="text-lg font-bold text-gray-900">Individual Confirmations ({confirmations.length})</h2>
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
                                            Status
                                        </th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                            Special Vote
                                        </th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                            Forum
                                        </th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                            Ticket / Check-in
                                        </th>
                                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                            Actions
                                        </th>
                                    </tr>
                                    </thead>
                                    <tbody className="bg-white divide-y divide-gray-200">
                                    {confirmations.map((conf) => (
                                        <tr key={conf.id} className="hover:bg-gray-50">
                                            <td className="px-6 py-4 whitespace-nowrap">
                                                <div>
                                                    <div className="text-sm font-medium text-gray-900">{conf.name}</div>
                                                    <div className="text-sm text-gray-500">{conf.membershipNumber}</div>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap">
                                                <span className={`inline-flex px-2 py-1 text-xs rounded ${getRegionBadgeColor(conf.region)}`}>
                                                    {conf.region} Region
                                                </span>
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap">
                                                {conf.bmmRegistrationStage === 'ATTENDANCE_CONFIRMED' ? (
                                                    <span className="px-2 py-1 bg-green-100 text-green-800 text-xs rounded">✅ Confirmed</span>
                                                ) : conf.bmmRegistrationStage === 'ATTENDANCE_DECLINED' ? (
                                                    <span className="px-2 py-1 bg-red-100 text-red-800 text-xs rounded">❌ Declined</span>
                                                ) : (
                                                    <span className="px-2 py-1 bg-gray-100 text-gray-800 text-xs rounded">⏳ Pending</span>
                                                )}
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap">
                                                {(conf.region === 'Central' || conf.region === 'Southern' ||
                                                    conf.region === 'Central Region' || conf.region === 'Southern Region' ||
                                                    conf.forumDesc === 'Greymouth' || conf.forumDesc === 'Hokitika' ||
                                                    conf.forumDesc === 'Reefton') ? (
                                                    <div className="space-y-1">
                                                        {/* Show if they have requested special vote */}
                                                        {conf.specialVoteRequested ? (
                                                            conf.bmmSpecialVoteStatus === 'APPROVED' ? (
                                                                <span className="px-2 py-1 bg-green-100 text-green-800 text-xs rounded block">✅ Approved</span>
                                                            ) : (
                                                                <span className="px-2 py-1 bg-blue-100 text-blue-800 text-xs rounded block">📝 Requested</span>
                                                            )
                                                        ) : conf.specialVoteEligible ? (
                                                            <span className="px-2 py-1 bg-amber-100 text-amber-800 text-xs rounded block">✓ Eligible</span>
                                                        ) : null}
                                                    </div>
                                                ) : (
                                                    <span className="text-xs text-gray-400">N/A</span>
                                                )}
                                            </td>
                                            <td className="px-6 py-4">
                                                <div>
                                                    {conf.forumDesc ? (
                                                        <div className="text-sm text-gray-900">
                                                            {conf.forumDesc}
                                                        </div>
                                                    ) : (
                                                        <span className="text-xs text-gray-400">No forum</span>
                                                    )}
                                                    {conf.bmmRegistrationStage === 'ATTENDANCE_DECLINED' && conf.declinedReason && (
                                                        <div className="mt-1">
                                                            <div className="text-xs text-gray-600">Decline reason:</div>
                                                            <div className="text-xs text-red-600 truncate max-w-xs" title={conf.declinedReason}>
                                                                {conf.declinedReason}
                                                            </div>
                                                        </div>
                                                    )}
                                                </div>
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap">
                                                <div className="flex items-center space-x-2">
                                                    {conf.ticketEmailSent && (
                                                        <span className="text-green-600" title="Ticket Sent">🎫</span>
                                                    )}
                                                    {conf.checkedIn && (
                                                        <span className="text-purple-600" title="Checked In">✔️</span>
                                                    )}
                                                </div>
                                            </td>
                                            <td className="px-6 py-4 whitespace-nowrap text-sm">
                                                <button
                                                    onClick={() => {
                                                        setSelectedMember(conf);
                                                        setShowDetails(true);
                                                    }}
                                                    className="text-orange-600 hover:text-orange-800"
                                                >
                                                    View Details
                                                </button>
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
                                            onClick={() => fetchConfirmations(currentPage - 1)}
                                            disabled={currentPage === 0}
                                            className="px-3 py-1 bg-gray-200 rounded disabled:opacity-50"
                                        >
                                            Previous
                                        </button>
                                        <button
                                            onClick={() => fetchConfirmations(currentPage + 1)}
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
                                            Attendance Confirmation Details: {selectedMember.name}
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
                                                <p className="text-sm text-gray-900">{selectedMember.region} Region</p>
                                            </div>
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Forum</label>
                                                <p className="text-sm text-gray-900">{selectedMember.forumDesc || 'N/A'}</p>
                                            </div>
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Registration Stage</label>
                                                <p className="text-sm text-gray-900">{selectedMember.bmmRegistrationStage}</p>
                                            </div>
                                        </div>

                                        {/* Attendance Status */}
                                        <div className="p-4 bg-orange-50 rounded-lg">
                                            <label className="block text-sm font-medium text-orange-800 mb-1">
                                                Attendance Status
                                            </label>
                                            <p className="text-lg font-semibold">
                                                {selectedMember.isAttending ? '✅ Attending' : '❌ Not Attending'}
                                            </p>
                                            {!selectedMember.isAttending && selectedMember.declinedReason && (
                                                <p className="text-sm text-gray-700 mt-2">
                                                    Reason: {selectedMember.declinedReason}
                                                </p>
                                            )}
                                        </div>

                                        {/* Special Vote Status - Only for Central and Southern */}
                                        {(selectedMember.region === 'Central' || selectedMember.region === 'Southern' ||
                                            selectedMember.region === 'Central Region' || selectedMember.region === 'Southern Region' ||
                                            selectedMember.forumDesc === 'Greymouth') && (
                                            <div className="p-4 bg-amber-50 rounded-lg">
                                                <label className="block text-sm font-medium text-amber-800 mb-1">
                                                    Special Vote Status
                                                </label>
                                                <div className="space-y-2">
                                                    <p className="text-lg font-semibold">
                                                        {selectedMember.specialVoteEligible ? '✅ Eligible' : '❌ Not Eligible'}
                                                    </p>
                                                    {selectedMember.specialVoteRequested && (
                                                        <div>
                                                            <p className="text-sm font-medium text-amber-700">
                                                                📝 Special Vote Requested
                                                            </p>
                                                            {selectedMember.bmmSpecialVoteStatus && (
                                                                <p className="text-sm text-amber-600">
                                                                    Status: <span className="font-semibold">{selectedMember.bmmSpecialVoteStatus}</span>
                                                                </p>
                                                            )}
                                                            {selectedMember.specialVoteApplicationReason && (
                                                                <p className="text-sm text-amber-600">
                                                                    Reason: {selectedMember.specialVoteApplicationReason}
                                                                </p>
                                                            )}
                                                            {selectedMember.absenceReason && (
                                                                <p className="text-sm text-amber-600">
                                                                    Absence Reason: {selectedMember.absenceReason}
                                                                </p>
                                                            )}
                                                            
                                                            {/* Additional Special Vote Details */}
                                                            <div className="mt-3 p-3 bg-purple-50 rounded-lg border border-purple-200">
                                                                <h4 className="font-semibold text-purple-800 mb-2">Member Details:</h4>
                                                                <div className="space-y-1 text-sm">
                                                                    {selectedMember.membershipNumber && (
                                                                        <div><strong>Membership #:</strong> {selectedMember.membershipNumber}</div>
                                                                    )}
                                                                    {selectedMember.primaryEmail && (
                                                                        <div><strong>Email:</strong> {selectedMember.primaryEmail}</div>
                                                                    )}
                                                                    {selectedMember.telephoneMobile && (
                                                                        <div><strong>Mobile:</strong> {selectedMember.telephoneMobile}</div>
                                                                    )}
                                                                    {selectedMember.address && (
                                                                        <div><strong>Address:</strong> {selectedMember.address}</div>
                                                                    )}
                                                                    {selectedMember.workplace && (
                                                                        <div><strong>Worksite:</strong> {selectedMember.workplace}</div>
                                                                    )}
                                                                    {selectedMember.ageOfMember && (
                                                                        <div><strong>Age:</strong> {selectedMember.ageOfMember}</div>
                                                                    )}
                                                                </div>
                                                            </div>
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        )}

                                        {/* Venue Assignment */}
                                        {selectedMember.isAttending && (
                                            <>
                                                <div>
                                                    <label className="block text-sm font-medium text-gray-700 mb-1">Assigned Venue</label>
                                                    <p className="p-3 bg-green-50 rounded-lg text-green-900 font-medium">
                                                        📍 {selectedMember.assignedVenue || 'No venue assigned'}
                                                    </p>
                                                </div>

                                                {selectedMember.assignedSession && (
                                                    <div>
                                                        <label className="block text-sm font-medium text-gray-700 mb-1">Session Time</label>
                                                        <p className="p-3 bg-blue-50 rounded-lg text-blue-900 font-medium">
                                                            🕐 {selectedMember.assignedSession}
                                                        </p>
                                                    </div>
                                                )}
                                            </>
                                        )}

                                        {/* Ticket and Check-in Status */}
                                        <div className="grid grid-cols-2 gap-4">
                                            <div className="p-4 bg-purple-50 rounded-lg">
                                                <label className="block text-sm font-medium text-purple-800 mb-1">
                                                    Ticket Status
                                                </label>
                                                <p className="text-lg font-semibold">
                                                    {selectedMember.ticketEmailSent ? '🎫 Sent' : '⏳ Not Sent'}
                                                </p>
                                            </div>

                                            <div className="p-4 bg-indigo-50 rounded-lg">
                                                <label className="block text-sm font-medium text-indigo-800 mb-1">
                                                    Check-in Status
                                                </label>
                                                <p className="text-lg font-semibold">
                                                    {selectedMember.checkedIn ? '✔️ Checked In' : '⏳ Not Checked In'}
                                                </p>
                                                {selectedMember.checkedIn && (
                                                    <div className="mt-2 space-y-1">
                                                        {selectedMember.checkInTime && (
                                                            <p className="text-xs text-gray-600">
                                                                <span className="font-medium">Time:</span> {new Date(selectedMember.checkInTime).toLocaleString()}
                                                            </p>
                                                        )}
                                                        {selectedMember.checkInMethod && (
                                                            <p className="text-xs text-gray-600">
                                                                <span className="font-medium">Method:</span> {selectedMember.checkInMethod === 'QR_SCAN' ? '📱 QR Scan' : selectedMember.checkInMethod === 'MANUAL' ? '✍️ Manual' : selectedMember.checkInMethod === 'BULK' ? '📋 Bulk Upload' : selectedMember.checkInMethod}
                                                            </p>
                                                        )}
                                                        {selectedMember.checkInAdminName && (
                                                            <p className="text-xs text-gray-600">
                                                                <span className="font-medium">By:</span> {selectedMember.checkInAdminName}
                                                            </p>
                                                        )}
                                                        {selectedMember.checkInVenue && (
                                                            <p className="text-xs text-gray-600">
                                                                <span className="font-medium">Venue:</span> {selectedMember.checkInVenue}
                                                            </p>
                                                        )}
                                                    </div>
                                                )}
                                            </div>
                                        </div>

                                        {/* Contact Information */}
                                        <div className="grid grid-cols-2 gap-4 p-4 bg-gray-50 rounded-lg">
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Email</label>
                                                <p className="text-sm text-gray-900">{selectedMember.email || 'Not provided'}</p>
                                            </div>
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Mobile</label>
                                                <p className="text-sm text-gray-900">{selectedMember.mobile || 'Not provided'}</p>
                                            </div>
                                        </div>

                                        {/* Stage 2 Financial Form Information */}
                                        {selectedMember.financialFormId && (
                                            <div className="p-4 bg-blue-50 rounded-lg">
                                                <h4 className="text-sm font-semibold text-blue-800 mb-3">📋 Stage 2: Updated Information</h4>

                                                {/* Personal Information */}
                                                <div className="mb-4">
                                                    <h5 className="text-xs font-semibold text-gray-700 mb-2">Personal Details</h5>
                                                    <div className="grid grid-cols-2 gap-3">
                                                        {selectedMember.postalAddress && (
                                                            <div className="col-span-2">
                                                                <label className="block text-xs text-gray-600">Home Address</label>
                                                                <p className="text-sm text-gray-900">{selectedMember.postalAddress}</p>
                                                            </div>
                                                        )}
                                                        {selectedMember.dateOfBirth && (
                                                            <div>
                                                                <label className="block text-xs text-gray-600">Date of Birth</label>
                                                                <p className="text-sm text-gray-900">
                                                                    {new Date(selectedMember.dateOfBirth).toLocaleDateString()}
                                                                </p>
                                                            </div>
                                                        )}
                                                        {selectedMember.phoneHome && (
                                                            <div>
                                                                <label className="block text-xs text-gray-600">Home Phone</label>
                                                                <p className="text-sm text-gray-900">{selectedMember.phoneHome}</p>
                                                            </div>
                                                        )}
                                                        {selectedMember.phoneWork && (
                                                            <div>
                                                                <label className="block text-xs text-gray-600">Work Phone</label>
                                                                <p className="text-sm text-gray-900">{selectedMember.phoneWork}</p>
                                                            </div>
                                                        )}
                                                    </div>
                                                </div>

                                                {/* Employment Information */}
                                                <div>
                                                    <h5 className="text-xs font-semibold text-gray-700 mb-2">Employment Details</h5>
                                                    <div className="grid grid-cols-2 gap-3">
                                                        {selectedMember.jobTitle && (
                                                            <div>
                                                                <label className="block text-xs text-gray-600">Job Title</label>
                                                                <p className="text-sm text-gray-900">{selectedMember.jobTitle}</p>
                                                            </div>
                                                        )}
                                                        {selectedMember.department && (
                                                            <div>
                                                                <label className="block text-xs text-gray-600">Department</label>
                                                                <p className="text-sm text-gray-900">{selectedMember.department}</p>
                                                            </div>
                                                        )}
                                                        {selectedMember.location && (
                                                            <div>
                                                                <label className="block text-xs text-gray-600">Location</label>
                                                                <p className="text-sm text-gray-900">{selectedMember.location}</p>
                                                            </div>
                                                        )}
                                                        {selectedMember.employmentStatus && (
                                                            <div>
                                                                <label className="block text-xs text-gray-600">Employment Status</label>
                                                                <p className="text-sm text-gray-900">{selectedMember.employmentStatus}</p>
                                                            </div>
                                                        )}
                                                        {selectedMember.payrollNumber && (
                                                            <div>
                                                                <label className="block text-xs text-gray-600">Payroll Number</label>
                                                                <p className="text-sm text-gray-900">{selectedMember.payrollNumber}</p>
                                                            </div>
                                                        )}
                                                        {selectedMember.siteCode && (
                                                            <div>
                                                                <label className="block text-xs text-gray-600">Site Code</label>
                                                                <p className="text-sm text-gray-900">{selectedMember.siteCode}</p>
                                                            </div>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        )}

                                        {/* Confirmation Date */}
                                        {selectedMember.confirmationDate && (
                                            <div className="text-sm text-gray-500 pt-2 border-t">
                                                Confirmed: {new Date(selectedMember.confirmationDate).toLocaleString()}
                                            </div>
                                        )}

                                        {/* Action Buttons */}
                                        <div className="flex justify-end space-x-3 pt-4 border-t">
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