import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/common/Layout';
import api from '@/services/api';
import { toast } from 'react-hot-toast';

interface RegionStats {
    totalMembers: number;
    registeredMembers: number;
    stage1Members: number;
    notRegisteredMembers: number;
    attendingMembers: number;
    specialVoteMembers: number;
    noResponseMembers: number;
    smsOnlyMembers: number;
    emailSentCount: number;
    smsSentCount: number;
    venuePreferences: { [key: string]: number };
}

interface BmmDashboardData {
    regions: { [key: string]: RegionStats };
    summary: {
        totalMembers: number;
        totalRegistered: number;
        totalAttending: number;
        totalSpecialVote: number;
        totalNoResponse: number;
        eventName: string;
        eventId: number;
    };
    lastUpdated: string;
}

interface RegionMember {
    id: number;
    membershipNumber: string;
    name: string;
    primaryEmail: string;
    telephoneMobile: string;
    regionDesc: string;
    workplace: string;
    employer: string;
    hasRegistered: boolean;
    isAttending: boolean;
    isSpecialVote: boolean;
    absenceReason: string;
    emailSent: boolean;
    smsSent: boolean;
    hasValidEmail: boolean;
    hasValidMobile: boolean;
    contactMethod: string;
    formSubmissionTime: string;
    createdAt: string;
    bmmStage?: string;
    preferredAttending?: boolean;
    preferenceSpecialVote?: boolean;
    workplaceInfo?: string;
    additionalComments?: string;
    preferenceSubmittedAt?: string;
    qrCodeEmailSent?: boolean;
    ticketStatus?: string;
    bmmPreferences?: {
        preferredVenues?: string[];
        preferredTimes?: string[];
        attendanceWillingness?: string;
        specialVoteInterest?: string;
        suggestedVenue?: string;
        workplaceInfo?: string;
        additionalComments?: string;
        preferenceSpecialVote?: boolean;
    };
}

interface FilterOptions {
    regions: string[];
    statuses: string[];
    contactMethods: string[];
    venues: string[];
    times: string[];
    workplaces: string[];
}

export default function BmmManagementPage() {
    const router = useRouter();
    const [loading, setLoading] = useState(true);
    const [dashboardData, setDashboardData] = useState<BmmDashboardData | null>(null);

    const [regionMembers, setRegionMembers] = useState<RegionMember[]>([]);
    const [memberLoading, setMemberLoading] = useState(false);
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [selectedMember, setSelectedMember] = useState<RegionMember | null>(null);
    const [showMemberDetails, setShowMemberDetails] = useState(false);
    const [venueAnalytics, setVenueAnalytics] = useState<any>(null);

    // Enhanced filtering states
    const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);
    const [filters, setFilters] = useState({
        region: '',
        status: '',
        contactMethod: '',
        venue: '',
        timePreference: '',
        workplace: '',
        industry: '',
        subIndustry: '',
        emailSent: '',
        smsSent: '',
        specialVote: '',
        searchTerm: ''
    });

    // Batch operations
    const [selectedMembers, setSelectedMembers] = useState<Set<number>>(new Set());
    const [showBatchActions, setShowBatchActions] = useState(false);
    const [batchActionType, setBatchActionType] = useState('');

    // Saved filters
    const [savedFilters, setSavedFilters] = useState<any[]>([]);
    const [showSaveFilter, setShowSaveFilter] = useState(false);
    const [filterName, setFilterName] = useState('');

    // Load dashboard data
    const fetchDashboardData = async () => {
        try {
            const response = await api.get('/admin/bmm/regional-dashboard');
            if (response.data.status === 'success') {
                setDashboardData(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch dashboard data:', error);
            toast.error('Failed to load dashboard data');
        } finally {
            setLoading(false);
        }
    };

    // Enhanced member fetching with multiple filters
    const fetchFilteredMembers = async (page: number = 0) => {
        setMemberLoading(true);
        try {
            const params = new URLSearchParams();
            params.append('page', page.toString());
            params.append('size', '50');

            // Apply all active filters
            if (filters.region) params.append('region', filters.region);
            if (filters.status) params.append('status', filters.status);
            if (filters.contactMethod) params.append('contactMethod', filters.contactMethod);
            if (filters.venue) params.append('venue', filters.venue);
            if (filters.timePreference) params.append('timePreference', filters.timePreference);
            if (filters.workplace) params.append('workplace', filters.workplace);
            if (filters.industry) params.append('industry', filters.industry);
            if (filters.subIndustry) params.append('subIndustry', filters.subIndustry);
            if (filters.emailSent) params.append('emailSent', filters.emailSent);
            if (filters.smsSent) params.append('smsSent', filters.smsSent);
            if (filters.searchTerm) params.append('search', filters.searchTerm);

            let url = '/admin/bmm/members/filtered?' + params.toString();

            const response = await api.get(url);
            if (response.data.status === 'success') {
                setRegionMembers(response.data.data.members);
                setTotalPages(response.data.data.totalPages);
                setTotalElements(response.data.data.totalElements || 0);
                setCurrentPage(page);
            }
        } catch (error) {
            console.error('Failed to fetch members:', error);
            toast.error('Failed to load members');
        } finally {
            setMemberLoading(false);
        }
    };

    // Load venue analytics
    const fetchVenueAnalytics = async () => {
        try {
            const response = await api.get('/admin/bmm/venue-analytics');
            if (response.data.status === 'success') {
                setVenueAnalytics(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch venue analytics:', error);
        }
    };

    // Get member preferences
    const fetchMemberPreferences = async (membershipNumber: string) => {
        try {
            const response = await api.get(`/admin/bmm/member/${membershipNumber}/preferences`);
            if (response.data.status === 'success') {
                setSelectedMember(response.data.data);
                setShowMemberDetails(true);
            }
        } catch (error) {
            console.error('Failed to fetch member preferences:', error);
            toast.error('Failed to load member details');
        }
    };

    // Batch member selection
    const toggleMemberSelection = (memberId: number) => {
        const newSelected = new Set(selectedMembers);
        if (newSelected.has(memberId)) {
            newSelected.delete(memberId);
        } else {
            newSelected.add(memberId);
        }
        setSelectedMembers(newSelected);
    };

    const selectAllMembers = () => {
        if (selectedMembers.size === regionMembers.length) {
            setSelectedMembers(new Set());
        } else {
            setSelectedMembers(new Set(regionMembers.map(m => m.id)));
        }
    };

    // Batch actions
    const executeBatchAction = async (action: string) => {
        if (selectedMembers.size === 0) {
            toast.error('Please select members first');
            return;
        }

        const selectedMemberIds = Array.from(selectedMembers);
        const selectedMemberData = regionMembers.filter(m => selectedMembers.has(m.id));

        switch (action) {
            case 'email':
                // Navigate to email page with pre-selected members
                const emailData = {
                    targetMembers: selectedMemberData.map(m => ({
                        id: m.id,
                        name: m.name,
                        email: m.primaryEmail,
                        membershipNumber: m.membershipNumber,
                        region: m.regionDesc
                    })),
                    filterContext: 'bmm-management'
                };
                localStorage.setItem('preSelectedMembers', JSON.stringify(emailData));
                router.push('/admin/bmm-emails');
                break;

            case 'sms':
                // Navigate to SMS page with pre-selected members
                const smsData = {
                    targetMembers: selectedMemberData
                        .filter(m => m.hasValidMobile)
                        .map(m => ({
                            id: m.id,
                            name: m.name,
                            mobile: m.telephoneMobile,
                            membershipNumber: m.membershipNumber,
                            region: m.regionDesc
                        })),
                    filterContext: 'bmm-management'
                };
                localStorage.setItem('preSelectedMembers', JSON.stringify(smsData));
                router.push('/admin/sms');
                break;

            case 'export':
                exportSelectedMembers(selectedMemberData);
                break;

            default:
                toast.error('Unknown action');
        }
    };

    // Export functionality
    const exportSelectedMembers = (members: RegionMember[]) => {
        const headers = [
            'Name', 'Membership Number', 'Email', 'Mobile', 'Region', 'Workplace', 'Employer',
            'Registration Status', 'Attendance', 'Special Vote', 'Contact Method', 'Email Sent', 'SMS Sent'
        ];

        const csvContent = [
            headers.join(','),
            ...members.map(member => [
                `"${member.name}"`,
                member.membershipNumber,
                member.primaryEmail || '',
                member.telephoneMobile || '',
                `"${member.regionDesc || ''}"`,
                `"${member.workplace || ''}"`,
                `"${member.employer || ''}"`,
                member.hasRegistered ? 'Registered' : 'Not Registered',
                member.isAttending ? 'Yes' : (member.isAttending === false ? 'No' : 'Unknown'),
                member.isSpecialVote ? 'Yes' : 'No',
                member.contactMethod,
                member.emailSent ? 'Yes' : 'No',
                member.smsSent ? 'Yes' : 'No'
            ].join(','))
        ].join('\n');

        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        const url = URL.createObjectURL(blob);
        link.setAttribute('href', url);
        link.setAttribute('download', `bmm-members-${new Date().toISOString().split('T')[0]}.csv`);
        link.style.visibility = 'hidden';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        toast.success(`Exported ${members.length} members to CSV`);
    };

    // Save filter
    const saveCurrentFilter = () => {
        if (!filterName.trim()) {
            toast.error('Please enter a filter name');
            return;
        }

        const newFilter = {
            id: Date.now(),
            name: filterName,
            filters: { ...filters },
            createdAt: new Date().toISOString()
        };

        const updated = [...savedFilters, newFilter];
        setSavedFilters(updated);
        localStorage.setItem('bmmSavedFilters', JSON.stringify(updated));
        setFilterName('');
        setShowSaveFilter(false);
        toast.success('Filter saved successfully');
    };

    // Apply saved filter
    const applySavedFilter = (savedFilter: any) => {
        setFilters(savedFilter.filters);
        toast.success(`Applied filter: ${savedFilter.name}`);
    };

    // Clear all filters
    const clearAllFilters = () => {
        setFilters({
            region: '',
            status: '',
            contactMethod: '',
            venue: '',
            timePreference: '',
            workplace: '',
            industry: '',
            subIndustry: '',
            emailSent: '',
            smsSent: '',
            specialVote: '',
            searchTerm: ''
        });
        setSelectedMembers(new Set());
    };

    useEffect(() => {
        fetchDashboardData();
        fetchVenueAnalytics();

        // Load saved filters
        const saved = localStorage.getItem('bmmSavedFilters');
        if (saved) {
            setSavedFilters(JSON.parse(saved));
        }

        // Initial load with no filters to show all members
        fetchFilteredMembers(0);
    }, []);

    useEffect(() => {
        const hasActiveFilters = Object.values(filters).some(value => value !== '');
        if (hasActiveFilters) {
            fetchFilteredMembers(0);
            // Real-time refresh dashboard data to ensure accurate statistics
            fetchDashboardData();
        }
    }, [filters]);

    // Manual refresh functionality
    const refreshAllData = async () => {
        setLoading(true);
        try {
            await Promise.all([
                fetchDashboardData(),
                fetchFilteredMembers(currentPage),
                fetchVenueAnalytics()
            ]);
            toast.success('Data refreshed successfully');
        } catch (error) {
            toast.error('Failed to refresh data');
        } finally {
            setLoading(false);
        }
    };

    const regions = ['Northern Region', 'Central Region', 'Southern Region'];

    const statusOptions = [
        { value: '', label: 'All Members' },
        { value: 'registered', label: 'Fully Registered (Stage 2)' },
        { value: 'stage1', label: 'Stage 1 (Preferences Only)' },
        { value: 'not_registered', label: 'Not Registered' },
        { value: 'attending', label: 'Attending' },
        { value: 'not_attending', label: 'Not Attending' },
        { value: 'special_vote', label: 'Special Vote Applied' },
        { value: 'sms_only', label: 'SMS Only (No Email)' }
    ];

    const contactMethodOptions = [
        { value: '', label: 'All Contact Methods' },
        { value: 'both', label: 'Email + SMS' },
        { value: 'email_only', label: 'Email Only' },
        { value: 'sms_only', label: 'SMS Only' },
        { value: 'no_contact', label: 'No Contact Method' }
    ];

    const getContactBadge = (member: RegionMember) => {
        switch (member.contactMethod) {
            case 'both':
                return <span className="px-2 py-1 bg-green-100 text-green-800 text-xs rounded">📧📱 Both</span>;
            case 'email_only':
                return <span className="px-2 py-1 bg-blue-100 text-blue-800 text-xs rounded">📧 Email Only</span>;
            case 'sms_only':
                return <span className="px-2 py-1 bg-orange-100 text-orange-800 text-xs rounded">📱 SMS Only</span>;
            default:
                return <span className="px-2 py-1 bg-red-100 text-red-800 text-xs rounded">❌ No Contact</span>;
        }
    };

    const getStatusBadge = (member: RegionMember) => {
        // Stage 1: Submitted preferences but not fully registered
        if (!member.hasRegistered && member.formSubmissionTime) {
            return <span className="px-2 py-1 bg-blue-100 text-blue-800 text-xs rounded">📝 Stage 1 (Preferences)</span>;
        }
        // Not registered at all
        if (!member.hasRegistered) {
            return <span className="px-2 py-1 bg-gray-100 text-gray-800 text-xs rounded">⏳ Not Registered</span>;
        }
        // Fully registered and attending
        if (member.isAttending) {
            return <span className="px-2 py-1 bg-green-100 text-green-800 text-xs rounded">✅ Attending</span>;
        }
        // Fully registered but not attending
        if (member.isAttending === false) {
            if (member.isSpecialVote) {
                return <span className="px-2 py-1 bg-purple-100 text-purple-800 text-xs rounded">🗳️ Special Vote</span>;
            }
            return <span className="px-2 py-1 bg-red-100 text-red-800 text-xs rounded">❌ Not Attending</span>;
        }
        // Fully registered but attendance status not selected
        return <span className="px-2 py-1 bg-yellow-100 text-yellow-800 text-xs rounded">🔄 Registered</span>;
    };

    if (loading) {
        return (
            <Layout>
                <div className="flex items-center justify-center h-96">
                    <div className="text-center">
                        <div className="inline-block animate-spin rounded-full h-12 w-12 border-t-4 border-blue-500"></div>
                        <p className="mt-4 text-lg text-gray-700">Loading BMM Management Dashboard...</p>
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
                                <h1 className="text-3xl font-bold text-gray-900">🗳️ BMM Management</h1>
                                <p className="mt-2 text-gray-600">
                                    Advanced filtering and bulk operations for BMM member management
                                </p>
                                {dashboardData && (
                                    <p className="text-sm text-gray-500 mt-1">
                                        Event: {dashboardData.summary.eventName} | Last Updated: {new Date(dashboardData.lastUpdated).toLocaleString()}
                                    </p>
                                )}
                            </div>
                            <div className="flex space-x-3">
                                <button
                                    onClick={refreshAllData}
                                    className="bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700 flex items-center space-x-2"
                                    disabled={loading}
                                >
                                    <span>🔄</span>
                                    <span>{loading ? 'Refreshing...' : 'Refresh Data'}</span>
                                </button>
                                <button
                                    onClick={() => router.push('/admin/bmm-preferences-overview')}
                                    className="bg-orange-600 text-white px-4 py-2 rounded-lg hover:bg-orange-700"
                                >
                                    📊 Preferences Overview
                                </button>
                                <button
                                    onClick={() => router.push(`/admin/bmm-attendance-overview?eventId=${dashboardData?.summary.eventId || ''}`)}
                                    className="bg-purple-600 text-white px-4 py-2 rounded-lg hover:bg-purple-700"
                                >
                                    ✅ Attendance Overview
                                </button>
                                <button
                                    onClick={() => router.push('/admin/bmm-emails')}
                                    className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700"
                                >
                                    📧 Email Management
                                </button>
                                <button
                                    onClick={() => router.push('/admin/sms')}
                                    className="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700"
                                >
                                    📱 SMS Management
                                </button>
                                <button
                                    onClick={() => router.push('/admin/bmm-venue-assignment')}
                                    className="bg-purple-600 text-white px-4 py-2 rounded-lg hover:bg-purple-700"
                                >
                                    🎯 Venue Assignment
                                </button>
                                <button
                                    onClick={() => router.push('/admin/dashboard')}
                                    className="bg-gray-600 text-white px-4 py-2 rounded-lg hover:bg-gray-700"
                                >
                                    🏠 Main Dashboard
                                </button>
                            </div>
                        </div>
                    </div>

                    {/* Advanced Filters Section */}
                    <div className="bg-white rounded-lg shadow-md mb-6 p-6">
                        <div className="flex justify-between items-center mb-4">
                            <h2 className="text-xl font-bold text-gray-900">🔍 Advanced Member Filtering</h2>
                            <div className="flex space-x-2">
                                <button
                                    onClick={() => setShowAdvancedFilters(!showAdvancedFilters)}
                                    className="bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700"
                                >
                                    {showAdvancedFilters ? 'Hide' : 'Show'} Advanced Filters
                                </button>
                                <button
                                    onClick={clearAllFilters}
                                    className="bg-gray-600 text-white px-4 py-2 rounded-lg hover:bg-gray-700"
                                >
                                    Clear All
                                </button>
                            </div>
                        </div>

                        {/* Basic Filters Row */}
                        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Region</label>
                                <select
                                    value={filters.region}
                                    onChange={(e) => setFilters(prev => ({ ...prev, region: e.target.value }))}
                                    className="w-full border rounded-lg px-3 py-2"
                                >
                                    <option value="">All Regions</option>
                                    {regions.map(region => (
                                        <option key={region} value={region}>{region}</option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
                                <select
                                    value={filters.status}
                                    onChange={(e) => setFilters(prev => ({ ...prev, status: e.target.value }))}
                                    className="w-full border rounded-lg px-3 py-2"
                                >
                                    {statusOptions.map(option => (
                                        <option key={option.value} value={option.value}>{option.label}</option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Contact Method</label>
                                <select
                                    value={filters.contactMethod}
                                    onChange={(e) => setFilters(prev => ({ ...prev, contactMethod: e.target.value }))}
                                    className="w-full border rounded-lg px-3 py-2"
                                >
                                    {contactMethodOptions.map(option => (
                                        <option key={option.value} value={option.value}>{option.label}</option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Search</label>
                                <input
                                    type="text"
                                    value={filters.searchTerm}
                                    onChange={(e) => setFilters(prev => ({ ...prev, searchTerm: e.target.value }))}
                                    placeholder="Name, membership number..."
                                    className="w-full border rounded-lg px-3 py-2"
                                />
                            </div>
                        </div>

                        {/* Advanced Filters */}
                        {showAdvancedFilters && (
                            <div className="border-t pt-4">
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-1">Email Sent</label>
                                        <select
                                            value={filters.emailSent}
                                            onChange={(e) => setFilters(prev => ({ ...prev, emailSent: e.target.value }))}
                                            className="w-full border rounded-lg px-3 py-2"
                                        >
                                            <option value="">All</option>
                                            <option value="true">Email Sent</option>
                                            <option value="false">Email Not Sent</option>
                                        </select>
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-1">SMS Sent</label>
                                        <select
                                            value={filters.smsSent}
                                            onChange={(e) => setFilters(prev => ({ ...prev, smsSent: e.target.value }))}
                                            className="w-full border rounded-lg px-3 py-2"
                                        >
                                            <option value="">All</option>
                                            <option value="true">SMS Sent</option>
                                            <option value="false">SMS Not Sent</option>
                                        </select>
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-1">Workplace</label>
                                        <input
                                            type="text"
                                            value={filters.workplace}
                                            onChange={(e) => setFilters(prev => ({ ...prev, workplace: e.target.value }))}
                                            placeholder="Filter by workplace..."
                                            className="w-full border rounded-lg px-3 py-2"
                                        />
                                    </div>
                                </div>

                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-1">Industry</label>
                                        <input
                                            type="text"
                                            value={filters.industry}
                                            onChange={(e) => setFilters(prev => ({ ...prev, industry: e.target.value }))}
                                            placeholder="e.g. Manufacturing Food"
                                            className="w-full border rounded-lg px-3 py-2"
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-1">Sub-Industry</label>
                                        <input
                                            type="text"
                                            value={filters.subIndustry}
                                            onChange={(e) => setFilters(prev => ({ ...prev, subIndustry: e.target.value }))}
                                            placeholder="e.g. Dairy Processing"
                                            className="w-full border rounded-lg px-3 py-2"
                                        />
                                    </div>
                                </div>

                                {/* Saved Filters */}
                                <div className="flex items-center space-x-4">
                                    <button
                                        onClick={() => setShowSaveFilter(true)}
                                        className="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700"
                                    >
                                        💾 Save Current Filter
                                    </button>

                                    {savedFilters.length > 0 && (
                                        <div className="flex items-center space-x-2">
                                            <span className="text-sm text-gray-600">Saved Filters:</span>
                                            {savedFilters.map(filter => (
                                                <button
                                                    key={filter.id}
                                                    onClick={() => applySavedFilter(filter)}
                                                    className="bg-gray-200 text-gray-700 px-3 py-1 rounded text-sm hover:bg-gray-300"
                                                >
                                                    {filter.name}
                                                </button>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            </div>
                        )}
                    </div>

                    {/* Filtered Members List */}
                    {regionMembers.length > 0 && (
                        <div className="bg-white rounded-lg shadow-md mb-6">
                            {/* Instructions */}
                            <div className="px-6 py-3 bg-blue-50 border-b border-blue-200">
                                <p className="text-sm text-blue-800">
                                    💡 <strong>How to use:</strong> Select members using checkboxes, then use batch actions to send emails or SMS.
                                    Selected members will be pre-loaded when you navigate to the email/SMS pages.
                                </p>
                            </div>

                            {/* Batch Actions Bar */}
                            <div className="px-6 py-4 border-b bg-gray-50">
                                <div className="flex justify-between items-center">
                                    <div className="flex items-center space-x-4">
                                        <div className="flex items-center space-x-3">
                                            <div className="flex items-center space-x-2">
                                                <input
                                                    type="checkbox"
                                                    checked={selectedMembers.size === regionMembers.length && regionMembers.length > 0}
                                                    onChange={selectAllMembers}
                                                    className="rounded h-4 w-4 text-blue-600 focus:ring-blue-500"
                                                />
                                                <span className="text-sm font-medium text-gray-700">
                                                    Select All on This Page
                                                </span>
                                            </div>
                                            <div className="flex items-center space-x-2">
                                                <span className="bg-blue-100 text-blue-800 px-3 py-1 rounded-full text-sm font-medium">
                                                    {selectedMembers.size} Selected
                                                </span>
                                                <span className="text-sm text-gray-500">
                                                    of {totalElements} Total Members
                                                </span>
                                            </div>
                                        </div>

                                        {selectedMembers.size > 0 && (
                                            <div className="flex items-center space-x-3">
                                                <span className="text-sm font-medium text-gray-700">Batch Actions:</span>
                                                <div className="flex items-center space-x-2">
                                                    <button
                                                        onClick={() => executeBatchAction('email')}
                                                        className="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-blue-700 flex items-center space-x-2 shadow-sm"
                                                    >
                                                        <span>📧</span>
                                                        <span>Send Email</span>
                                                        <span className="bg-blue-500 text-white px-2 py-1 rounded text-xs">
                                                            {selectedMembers.size}
                                                        </span>
                                                    </button>
                                                    <button
                                                        onClick={() => executeBatchAction('sms')}
                                                        className="bg-green-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-green-700 flex items-center space-x-2 shadow-sm"
                                                    >
                                                        <span>📱</span>
                                                        <span>Send SMS</span>
                                                        <span className="bg-green-500 text-white px-2 py-1 rounded text-xs">
                                                            {regionMembers.filter(m => selectedMembers.has(m.id) && m.hasValidMobile).length}
                                                        </span>
                                                    </button>
                                                    <button
                                                        onClick={() => executeBatchAction('export')}
                                                        className="bg-gray-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-gray-700 flex items-center space-x-2 shadow-sm"
                                                    >
                                                        <span>📊</span>
                                                        <span>Export CSV</span>
                                                    </button>
                                                </div>
                                            </div>
                                        )}
                                    </div>

                                    <div className="text-sm text-gray-600">
                                        Showing {regionMembers.length} of {totalElements} members
                                    </div>
                                </div>
                            </div>

                            {/* Enhanced Members Table */}
                            <div className="p-6">
                                {memberLoading ? (
                                    <div className="text-center py-8">
                                        <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-blue-500"></div>
                                        <p className="mt-2 text-gray-600">Loading members...</p>
                                    </div>
                                ) : (
                                    <>
                                        <div className="overflow-x-auto">
                                            <table className="min-w-full divide-y divide-gray-200">
                                                <thead className="bg-gray-50">
                                                <tr>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                        <input
                                                            type="checkbox"
                                                            checked={selectedMembers.size === regionMembers.length && regionMembers.length > 0}
                                                            onChange={selectAllMembers}
                                                            className="rounded"
                                                        />
                                                    </th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                        Member
                                                    </th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                        Contact
                                                    </th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                        Status
                                                    </th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                        Region & Workplace
                                                    </th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                        Communication
                                                    </th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                                        Actions
                                                    </th>
                                                </tr>
                                                </thead>
                                                <tbody className="bg-white divide-y divide-gray-200">
                                                {regionMembers.map((member) => (
                                                    <tr key={member.id} className={`hover:bg-gray-50 ${selectedMembers.has(member.id) ? 'bg-blue-50' : ''}`}>
                                                        <td className="px-6 py-4 whitespace-nowrap">
                                                            <input
                                                                type="checkbox"
                                                                checked={selectedMembers.has(member.id)}
                                                                onChange={() => toggleMemberSelection(member.id)}
                                                                className="rounded"
                                                            />
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap">
                                                            <div>
                                                                <div className="text-sm font-medium text-gray-900">
                                                                    {member.name}
                                                                </div>
                                                                <div className="text-sm text-gray-500">
                                                                    {member.membershipNumber}
                                                                </div>
                                                            </div>
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap">
                                                            <div className="space-y-1">
                                                                {getContactBadge(member)}
                                                                <div className="text-xs text-gray-500">
                                                                    {member.primaryEmail && (
                                                                        <div>📧 {member.primaryEmail}</div>
                                                                    )}
                                                                    {member.telephoneMobile && (
                                                                        <div>📱 {member.telephoneMobile}</div>
                                                                    )}
                                                                </div>
                                                            </div>
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap">
                                                            <div className="space-y-1">
                                                                {getStatusBadge(member)}
                                                                {member.absenceReason && (
                                                                    <div className="text-xs text-gray-500">
                                                                        Reason: {member.absenceReason}
                                                                    </div>
                                                                )}
                                                                {/* Show preference indicators */}
                                                                {member.bmmStage === 'PREFERENCE_SUBMITTED' || member.bmmStage === 'VENUE_ASSIGNED' ? (
                                                                    <div className="flex gap-1 mt-1">
                                                                        {member.preferredAttending === true && (
                                                                            <span className="text-xs bg-green-100 text-green-700 px-1 rounded">📋 Yes</span>
                                                                        )}
                                                                        {member.preferredAttending === false && (
                                                                            <span className="text-xs bg-red-100 text-red-700 px-1 rounded">📋 No</span>
                                                                        )}
                                                                        {member.preferenceSpecialVote === true && (
                                                                            <span className="text-xs bg-amber-100 text-amber-700 px-1 rounded">🗳️</span>
                                                                        )}
                                                                    </div>
                                                                ) : null}
                                                            </div>
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap">
                                                            <div className="text-sm text-gray-900">{member.regionDesc}</div>
                                                            <div className="text-sm text-gray-500">{member.workplace}</div>
                                                            <div className="text-xs text-gray-400">{member.employer}</div>
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap">
                                                            <div className="space-y-1">
                                                                <div className="flex space-x-1">
                                                                        <span className={`px-2 py-1 text-xs rounded ${member.emailSent ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                                                                            {member.emailSent ? '📧 Sent' : '📧 Not Sent'}
                                                                        </span>
                                                                    <span className={`px-2 py-1 text-xs rounded ${member.smsSent ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                                                                            {member.smsSent ? '📱 Sent' : '📱 Not Sent'}
                                                                        </span>
                                                                </div>
                                                            </div>
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap text-sm">
                                                            <div className="flex space-x-2">
                                                                <button
                                                                    onClick={() => fetchMemberPreferences(member.membershipNumber)}
                                                                    className="text-blue-600 hover:text-blue-800"
                                                                >
                                                                    View Details
                                                                </button>
                                                                {member.hasValidEmail && (
                                                                    <button
                                                                        onClick={() => {
                                                                            setSelectedMembers(new Set([member.id]));
                                                                            executeBatchAction('email');
                                                                        }}
                                                                        className="text-green-600 hover:text-green-800"
                                                                    >
                                                                        📧
                                                                    </button>
                                                                )}
                                                                {member.hasValidMobile && (
                                                                    <button
                                                                        onClick={() => {
                                                                            setSelectedMembers(new Set([member.id]));
                                                                            executeBatchAction('sms');
                                                                        }}
                                                                        className="text-orange-600 hover:text-orange-800"
                                                                    >
                                                                        📱
                                                                    </button>
                                                                )}
                                                            </div>
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
                                                    Page {currentPage + 1} of {totalPages} (Total: {totalElements} members)
                                                </div>
                                                <div className="flex space-x-2">
                                                    <button
                                                        onClick={() => fetchFilteredMembers(currentPage - 1)}
                                                        disabled={currentPage === 0}
                                                        className="px-3 py-1 bg-gray-200 rounded disabled:opacity-50"
                                                    >
                                                        Previous
                                                    </button>
                                                    <button
                                                        onClick={() => fetchFilteredMembers(currentPage + 1)}
                                                        disabled={currentPage >= totalPages - 1}
                                                        className="px-3 py-1 bg-gray-200 rounded disabled:opacity-50"
                                                    >
                                                        Next
                                                    </button>
                                                </div>
                                            </div>
                                        )}
                                    </>
                                )}
                            </div>
                        </div>
                    )}

                    {/* Quick Filter Shortcuts */}
                    <div className="bg-white rounded-lg shadow-md mb-6 p-6">
                        <h2 className="text-xl font-bold text-gray-900 mb-4">🚀 Quick Filter Shortcuts</h2>
                        <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
                            <button
                                onClick={() => setFilters(prev => ({ ...prev, status: 'stage1', region: '', contactMethod: '' }))}
                                className="bg-blue-100 text-blue-800 p-4 rounded-lg hover:bg-blue-200 text-left"
                            >
                                <div className="font-semibold">📝 Stage 1 Only</div>
                                <div className="text-sm">Preferences submitted</div>
                            </button>

                            <button
                                onClick={() => setFilters(prev => ({ ...prev, status: 'not_registered', region: '', contactMethod: '' }))}
                                className="bg-red-100 text-red-800 p-4 rounded-lg hover:bg-red-200 text-left"
                            >
                                <div className="font-semibold">⚠️ Not Registered</div>
                                <div className="text-sm">Need follow-up</div>
                            </button>

                            <button
                                onClick={() => setFilters(prev => ({ ...prev, contactMethod: 'sms_only', status: '', region: '' }))}
                                className="bg-orange-100 text-orange-800 p-4 rounded-lg hover:bg-orange-200 text-left"
                            >
                                <div className="font-semibold">📱 SMS Only</div>
                                <div className="text-sm">No email address</div>
                            </button>

                            <button
                                onClick={() => setFilters(prev => ({ ...prev, status: 'special_vote', region: '', contactMethod: '' }))}
                                className="bg-purple-100 text-purple-800 p-4 rounded-lg hover:bg-purple-200 text-left"
                            >
                                <div className="font-semibold">🗳️ Special Vote</div>
                                <div className="text-sm">Applications</div>
                            </button>

                            <button
                                onClick={() => setFilters(prev => ({ ...prev, emailSent: 'false', status: '', region: '', contactMethod: '' }))}
                                className="bg-teal-100 text-teal-800 p-4 rounded-lg hover:bg-teal-200 text-left"
                            >
                                <div className="font-semibold">📧 No Email Sent</div>
                                <div className="text-sm">Ready to contact</div>
                            </button>
                        </div>
                    </div>

                    {/* Overall Summary Cards */}
                    {dashboardData && (
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 mb-6">
                            <div className="bg-white p-4 rounded-lg shadow border-l-4 border-blue-500">
                                <div className="flex items-center">
                                    <div className="flex-shrink-0">
                                        <div className="text-2xl">👥</div>
                                    </div>
                                    <div className="ml-3">
                                        <p className="text-sm font-medium text-gray-500">Total Members</p>
                                        <p className="text-2xl font-semibold text-gray-900">{dashboardData.summary.totalMembers}</p>
                                    </div>
                                </div>
                            </div>

                            <div className="bg-white p-4 rounded-lg shadow border-l-4 border-green-500">
                                <div className="flex items-center">
                                    <div className="flex-shrink-0">
                                        <div className="text-2xl">✅</div>
                                    </div>
                                    <div className="ml-3">
                                        <p className="text-sm font-medium text-gray-500">Registered</p>
                                        <p className="text-2xl font-semibold text-gray-900">{dashboardData.summary.totalRegistered}</p>
                                        <p className="text-xs text-gray-500">
                                            {dashboardData.summary.totalMembers > 0
                                                ? Math.round((dashboardData.summary.totalRegistered / dashboardData.summary.totalMembers) * 100)
                                                : 0}%
                                        </p>
                                    </div>
                                </div>
                            </div>

                            <div className="bg-white p-4 rounded-lg shadow border-l-4 border-purple-500">
                                <div className="flex items-center">
                                    <div className="flex-shrink-0">
                                        <div className="text-2xl">🎯</div>
                                    </div>
                                    <div className="ml-3">
                                        <p className="text-sm font-medium text-gray-500">Attending</p>
                                        <p className="text-2xl font-semibold text-gray-900">{dashboardData.summary.totalAttending}</p>
                                    </div>
                                </div>
                            </div>

                            <div className="bg-white p-4 rounded-lg shadow border-l-4 border-orange-500">
                                <div className="flex items-center">
                                    <div className="flex-shrink-0">
                                        <div className="text-2xl">🗳️</div>
                                    </div>
                                    <div className="ml-3">
                                        <p className="text-sm font-medium text-gray-500">Special Vote</p>
                                        <p className="text-2xl font-semibold text-gray-900">{dashboardData.summary.totalSpecialVote}</p>
                                    </div>
                                </div>
                            </div>

                            <div className="bg-white p-4 rounded-lg shadow border-l-4 border-red-500">
                                <div className="flex items-center">
                                    <div className="flex-shrink-0">
                                        <div className="text-2xl">⚠️</div>
                                    </div>
                                    <div className="ml-3">
                                        <p className="text-sm font-medium text-gray-500">No Response</p>
                                        <p className="text-2xl font-semibold text-gray-900">{dashboardData.summary.totalNoResponse}</p>
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Regional Analysis Cards */}
                    {dashboardData && (
                        <div className="bg-white rounded-lg shadow-md mb-6 p-6">
                            <h2 className="text-xl font-bold text-gray-900 mb-4">📍 Regional Analysis</h2>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                                {regions.map(region => {
                                    const regionData = dashboardData.regions[region];
                                    if (!regionData) return null;

                                    const registrationRate = regionData.totalMembers > 0
                                        ? Math.round((regionData.registeredMembers / regionData.totalMembers) * 100)
                                        : 0;

                                    return (
                                        <div key={region} className="border rounded-lg p-4 hover:shadow-md transition-shadow">
                                            <div className="flex items-center justify-between mb-3">
                                                <h3 className="font-semibold text-gray-900">{region}</h3>
                                                <button
                                                    onClick={() => {
                                                        setFilters(prev => ({ ...prev, region: region }));
                                                    }}
                                                    className="text-blue-600 hover:text-blue-800 text-sm font-medium"
                                                >
                                                    View Details →
                                                </button>
                                            </div>

                                            <div className="space-y-2 text-sm">
                                                <div className="flex justify-between">
                                                    <span className="text-gray-600">Total Members:</span>
                                                    <span className="font-medium">{regionData.totalMembers}</span>
                                                </div>
                                                <div className="flex justify-between">
                                                    <span className="text-gray-600">Fully Registered:</span>
                                                    <span className="font-medium text-green-600">{regionData.registeredMembers} ({registrationRate}%)</span>
                                                </div>
                                                <div className="flex justify-between">
                                                    <span className="text-gray-600">Stage 1 (Preferences):</span>
                                                    <span className="font-medium text-blue-600">
                                                        {regionData.stage1Members || 0}
                                                        <button
                                                            onClick={() => {
                                                                setFilters(prev => ({ ...prev, region: region, status: 'stage1' }));
                                                            }}
                                                            className="ml-1 text-xs text-blue-500 hover:text-blue-700"
                                                        >
                                                            🎯
                                                        </button>
                                                    </span>
                                                </div>
                                                <div className="flex justify-between">
                                                    <span className="text-gray-600">Not Registered:</span>
                                                    <span className="font-medium text-red-600">
                                                        {regionData.notRegisteredMembers || regionData.noResponseMembers || 0}
                                                        <button
                                                            onClick={() => {
                                                                setFilters(prev => ({ ...prev, region: region, status: 'not_registered' }));
                                                            }}
                                                            className="ml-1 text-xs text-red-500 hover:text-red-700"
                                                        >
                                                            🎯
                                                        </button>
                                                    </span>
                                                </div>
                                                <div className="flex justify-between">
                                                    <span className="text-gray-600">Attending:</span>
                                                    <span className="font-medium text-purple-600">{regionData.attendingMembers}</span>
                                                </div>
                                                <div className="flex justify-between">
                                                    <span className="text-gray-600">Special Vote:</span>
                                                    <span className="font-medium text-orange-600">{regionData.specialVoteMembers}</span>
                                                </div>
                                                <div className="flex justify-between">
                                                    <span className="text-gray-600">SMS Only:</span>
                                                    <span className="font-medium text-yellow-600">{regionData.smsOnlyMembers}</span>
                                                </div>
                                            </div>

                                            {/* Top venues for this region */}
                                            {regionData.venuePreferences && Object.keys(regionData.venuePreferences).length > 0 && (
                                                <div className="mt-3 pt-3 border-t">
                                                    <p className="text-xs text-gray-500 mb-2">Top Venue Preferences:</p>
                                                    <div className="space-y-1">
                                                        {Object.entries(regionData.venuePreferences)
                                                            .sort(([,a], [,b]) => b - a)
                                                            .slice(0, 3)
                                                            .map(([venue, count]) => (
                                                                <div key={venue} className="flex justify-between text-xs">
                                                                    <span className="text-gray-600 truncate">{venue}</span>
                                                                    <span className="text-gray-900 font-medium">{count}</span>
                                                                </div>
                                                            ))}
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    )}



                    {/* Member Details Modal */}
                    {showMemberDetails && selectedMember && (
                        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                            <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full m-4 max-h-[90vh] overflow-y-auto">
                                <div className="p-6">
                                    <div className="flex justify-between items-center mb-4">
                                        <h3 className="text-lg font-bold text-gray-900">
                                            Member Details: {selectedMember.name}
                                        </h3>
                                        <button
                                            onClick={() => setShowMemberDetails(false)}
                                            className="text-gray-400 hover:text-gray-600"
                                        >
                                            ✕
                                        </button>
                                    </div>

                                    <div className="space-y-4">
                                        <div className="grid grid-cols-2 gap-4">
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Membership Number</label>
                                                <p className="text-sm text-gray-900">{selectedMember.membershipNumber}</p>
                                            </div>
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Region</label>
                                                <p className="text-sm text-gray-900">{selectedMember.regionDesc}</p>
                                            </div>
                                        </div>

                                        <div className="grid grid-cols-2 gap-4">
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Email</label>
                                                <p className="text-sm text-gray-900">{selectedMember.primaryEmail || 'Not provided'}</p>
                                            </div>
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Mobile</label>
                                                <p className="text-sm text-gray-900">{selectedMember.telephoneMobile || 'Not provided'}</p>
                                            </div>
                                        </div>

                                        <div className="grid grid-cols-2 gap-4">
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Workplace</label>
                                                <p className="text-sm text-gray-900">{selectedMember.workplace || 'Not provided'}</p>
                                            </div>
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Employer</label>
                                                <p className="text-sm text-gray-900">{selectedMember.employer || 'Not provided'}</p>
                                            </div>
                                        </div>

                                        <div className="flex space-x-4">
                                            {getStatusBadge(selectedMember)}
                                            {getContactBadge(selectedMember)}
                                        </div>

                                        {selectedMember.absenceReason && (
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700">Absence Reason</label>
                                                <p className="text-sm text-gray-900">{selectedMember.absenceReason}</p>
                                            </div>
                                        )}

                                        {/* Stage 2 Confirmation Data - Display if member has completed Stage 2 */}
                                        {selectedMember.hasRegistered && (
                                            <div className="border-t pt-4">
                                                <h4 className="font-medium text-gray-900 mb-3 flex items-center">
                                                    <span className="mr-2">✅</span> Stage 2: Attendance Confirmation
                                                </h4>
                                                <div className="space-y-3">
                                                    {/* Attendance Status */}
                                                    <div className={`p-3 rounded-lg ${
                                                        selectedMember.isAttending
                                                            ? 'bg-green-50 border border-green-200'
                                                            : 'bg-red-50 border border-red-200'
                                                    }`}>
                                                        <span className="font-medium">Attendance Status:</span>
                                                        <span className={`ml-2 font-semibold ${
                                                            selectedMember.isAttending ? 'text-green-800' : 'text-red-800'
                                                        }`}>
                                                            {selectedMember.isAttending ? '✅ Will Attend' : '❌ Cannot Attend'}
                                                        </span>
                                                    </div>

                                                    {/* Absence Reason (if not attending) */}
                                                    {!selectedMember.isAttending && selectedMember.absenceReason && (
                                                        <div className="bg-yellow-50 p-3 rounded-lg border border-yellow-200">
                                                            <span className="font-medium text-yellow-800">Absence Reason:</span>
                                                            <p className="mt-1 text-yellow-900">
                                                                {selectedMember.absenceReason === 'sick' ? '🤒 I am sick' :
                                                                    selectedMember.absenceReason === 'distance' ? '📍 I live outside a 32-km radius from the meeting place' :
                                                                        selectedMember.absenceReason === 'work' ? '💼 My employer requires me to work at the time of the meeting' :
                                                                            selectedMember.absenceReason}
                                                            </p>
                                                        </div>
                                                    )}

                                                    {/* Special Vote Status (for Central/Southern regions only) */}
                                                    {!selectedMember.isAttending &&
                                                        (selectedMember.regionDesc?.toLowerCase().includes('central') ||
                                                            selectedMember.regionDesc?.toLowerCase().includes('southern')) && (
                                                            <div className={`p-3 rounded-lg ${
                                                                selectedMember.isSpecialVote
                                                                    ? 'bg-purple-50 border border-purple-200'
                                                                    : 'bg-gray-50 border border-gray-200'
                                                            }`}>
                                                                <span className="font-medium">Special Vote Application:</span>
                                                                <span className={`ml-2 font-semibold ${
                                                                    selectedMember.isSpecialVote ? 'text-purple-800' : 'text-gray-600'
                                                                }`}>
                                                                {selectedMember.isSpecialVote ? '🗳️ Applied for Special Vote' : '❌ No Special Vote Request'}
                                                            </span>
                                                            </div>
                                                        )}

                                                    {/* Ticket Status (if attending) */}
                                                    {selectedMember.isAttending && (
                                                        <div className="bg-blue-50 p-3 rounded-lg border border-blue-200">
                                                            <span className="font-medium text-blue-800">Ticket Status:</span>
                                                            <span className="ml-2 font-semibold text-blue-900">
                                                                {selectedMember.qrCodeEmailSent ? '🎫 Ticket Email Sent' : '⏳ Ticket Pending'}
                                                            </span>
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        )}

                                        {/* BMM Preferences - Enhanced Display */}
                                        {(selectedMember.bmmPreferences || selectedMember.bmmStage) && (
                                            <div className="border-t pt-4">
                                                <h4 className="font-medium text-gray-900 mb-3 flex items-center">
                                                    <span className="mr-2">📋</span> Stage 1: Pre-Registration Preferences
                                                </h4>

                                                {/* BMM Stage Badge */}
                                                <div className="mb-3">
                                                    <span className="text-sm font-medium text-gray-700">BMM Stage: </span>
                                                    <span className={`px-2 py-1 text-xs rounded ${
                                                        selectedMember.bmmStage === 'PREFERENCE_SUBMITTED' ? 'bg-blue-100 text-blue-800' :
                                                            selectedMember.bmmStage === 'VENUE_ASSIGNED' ? 'bg-green-100 text-green-800' :
                                                                selectedMember.bmmStage === 'ATTENDANCE_CONFIRMED' ? 'bg-purple-100 text-purple-800' :
                                                                    'bg-gray-100 text-gray-800'
                                                    }`}>
                                                        {selectedMember.bmmStage || 'Not Started'}
                                                    </span>
                                                </div>

                                                <div className="space-y-3 text-sm">
                                                    {/* Attendance Intention */}
                                                    <div className="bg-orange-50 p-3 rounded-lg">
                                                        <span className="font-medium text-orange-800">Would like to attend BMM:</span>
                                                        <span className="ml-2 font-semibold">
                                                            {selectedMember.preferredAttending === true ? '✅ Yes' :
                                                                selectedMember.preferredAttending === false ? '❌ No' :
                                                                    '⏳ Not indicated'}
                                                        </span>
                                                    </div>

                                                    {/* Special Vote (for Central/Southern) */}
                                                    {(selectedMember.regionDesc === 'Central Region' || selectedMember.regionDesc === 'Southern Region') && (
                                                        <div className="bg-amber-50 p-3 rounded-lg">
                                                            <span className="font-medium text-amber-800">Believes they qualify for special vote:</span>
                                                            <span className="ml-2 font-semibold">
                                                                {selectedMember.preferenceSpecialVote === true ? '✅ Yes' :
                                                                    selectedMember.preferenceSpecialVote === false ? '❌ No' :
                                                                        selectedMember.bmmPreferences?.preferenceSpecialVote === null ? '❓ Not sure' :
                                                                            '⏳ Not indicated'}
                                                            </span>
                                                        </div>
                                                    )}

                                                    {/* Assigned Venue */}
                                                    {selectedMember.bmmPreferences?.preferredVenues && selectedMember.bmmPreferences.preferredVenues.length > 0 && (
                                                        <div className="bg-green-50 p-3 rounded-lg">
                                                            <span className="font-medium text-green-800">📍 Assigned Venue:</span>
                                                            <div className="mt-1 font-semibold text-green-900">
                                                                {selectedMember.bmmPreferences.preferredVenues[0]}
                                                            </div>
                                                        </div>
                                                    )}

                                                    {/* Preferred Times */}
                                                    {selectedMember.bmmPreferences?.preferredTimes && selectedMember.bmmPreferences.preferredTimes.length > 0 && (
                                                        <div className="bg-blue-50 p-3 rounded-lg">
                                                            <span className="font-medium text-blue-800">🕐 Preferred Session Times:</span>
                                                            <div className="mt-1 flex flex-wrap gap-2">
                                                                {selectedMember.bmmPreferences.preferredTimes.map(time => (
                                                                    <span key={time} className="px-2 py-1 bg-blue-100 text-blue-700 rounded text-xs">
                                                                        {time === 'morning' ? 'Morning (9AM-12PM)' :
                                                                            time === 'lunchtime' ? 'Lunchtime (12PM-2PM)' :
                                                                                time === 'afternoon' ? 'Afternoon (2PM-5PM)' :
                                                                                    time === 'after_work' ? 'After Work (5PM-8PM)' :
                                                                                        time === 'night_shift' ? 'Night Shift' : time}
                                                                    </span>
                                                                ))}
                                                            </div>
                                                        </div>
                                                    )}

                                                    {/* Workplace Information */}
                                                    {(selectedMember.workplaceInfo || selectedMember.bmmPreferences?.workplaceInfo) && (
                                                        <div className="bg-gray-50 p-3 rounded-lg">
                                                            <span className="font-medium text-gray-700">🏢 Workplace Information:</span>
                                                            <div className="mt-1 text-gray-900">
                                                                {selectedMember.workplaceInfo || selectedMember.bmmPreferences?.workplaceInfo}
                                                            </div>
                                                        </div>
                                                    )}

                                                    {/* Suggested Alternative Venue */}
                                                    {selectedMember.bmmPreferences?.suggestedVenue && (
                                                        <div className="bg-yellow-50 p-3 rounded-lg">
                                                            <span className="font-medium text-yellow-800">💡 Suggested Alternative Venue:</span>
                                                            <div className="mt-1 text-yellow-900">
                                                                {selectedMember.bmmPreferences.suggestedVenue}
                                                            </div>
                                                        </div>
                                                    )}

                                                    {/* Additional Comments */}
                                                    {(selectedMember.additionalComments || selectedMember.bmmPreferences?.additionalComments) && (
                                                        <div className="bg-indigo-50 p-3 rounded-lg">
                                                            <span className="font-medium text-indigo-800">💬 Additional Comments:</span>
                                                            <div className="mt-1 text-indigo-900 whitespace-pre-wrap">
                                                                {selectedMember.additionalComments || selectedMember.bmmPreferences?.additionalComments}
                                                            </div>
                                                        </div>
                                                    )}

                                                    {/* Submission Time */}
                                                    {selectedMember.preferenceSubmittedAt && (
                                                        <div className="text-xs text-gray-500 mt-2">
                                                            Preferences submitted: {new Date(selectedMember.preferenceSubmittedAt).toLocaleString()}
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        )}

                                        <div className="flex justify-between text-xs text-gray-500 border-t pt-2">
                                            <span>Created: {new Date(selectedMember.createdAt).toLocaleString()}</span>
                                            {selectedMember.formSubmissionTime && (
                                                <span>Submitted: {new Date(selectedMember.formSubmissionTime).toLocaleString()}</span>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Save Filter Modal */}
                    {showSaveFilter && (
                        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                            <div className="bg-white rounded-lg shadow-xl max-w-md w-full m-4">
                                <div className="p-6">
                                    <h3 className="text-lg font-bold text-gray-900 mb-4">Save Current Filter</h3>
                                    <div className="space-y-4">
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">Filter Name</label>
                                            <input
                                                type="text"
                                                value={filterName}
                                                onChange={(e) => setFilterName(e.target.value)}
                                                placeholder="e.g., 'Unregistered Northern Region'"
                                                className="w-full border rounded-lg px-3 py-2"
                                            />
                                        </div>
                                        <div className="text-sm text-gray-600">
                                            Current filters:
                                            <ul className="list-disc list-inside mt-1 text-xs">
                                                {filters.region && <li>Region: {filters.region}</li>}
                                                {filters.status && <li>Status: {filters.status}</li>}
                                                {filters.contactMethod && <li>Contact: {filters.contactMethod}</li>}
                                                {filters.searchTerm && <li>Search: {filters.searchTerm}</li>}
                                                {filters.emailSent && <li>Email Sent: {filters.emailSent}</li>}
                                                {filters.smsSent && <li>SMS Sent: {filters.smsSent}</li>}
                                                {filters.workplace && <li>Workplace: {filters.workplace}</li>}
                                            </ul>
                                        </div>
                                    </div>
                                    <div className="flex justify-end space-x-3 mt-6">
                                        <button
                                            onClick={() => setShowSaveFilter(false)}
                                            className="px-4 py-2 text-gray-600 hover:text-gray-800"
                                        >
                                            Cancel
                                        </button>
                                        <button
                                            onClick={saveCurrentFilter}
                                            className="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700"
                                        >
                                            Save Filter
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Venue Analytics */}
                    {venueAnalytics && (
                        <div className="bg-white rounded-lg shadow-md mb-6 p-6">
                            <h2 className="text-xl font-bold text-gray-900 mb-4">🏢 Venue Preferences Analytics</h2>

                            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                                {/* Overall Top Venues */}
                                <div>
                                    <h3 className="font-semibold text-gray-900 mb-3">Overall Top Venues</h3>
                                    <div className="space-y-2">
                                        {Object.entries(venueAnalytics.overallVenues || {})
                                            .sort(([,a], [,b]) => (b as number) - (a as number))
                                            .slice(0, 10)
                                            .map(([venue, count]) => (
                                                <div key={venue} className="flex justify-between items-center p-2 bg-gray-50 rounded">
                                                    <span className="text-sm text-gray-900">{venue}</span>
                                                    <span className="text-sm font-medium text-blue-600">{count as number}</span>
                                                </div>
                                            ))}
                                    </div>
                                </div>

                                {/* Time Preferences */}
                                <div>
                                    <h3 className="font-semibold text-gray-900 mb-3">Meeting Time Preferences</h3>
                                    <div className="space-y-2">
                                        {Object.entries(venueAnalytics.timePreferences || {})
                                            .sort(([,a], [,b]) => (b as number) - (a as number))
                                            .map(([time, count]) => (
                                                <div key={time} className="flex justify-between items-center p-2 bg-gray-50 rounded">
                                                    <span className="text-sm text-gray-900 capitalize">{time.replace('-', ' ')}</span>
                                                    <span className="text-sm font-medium text-green-600">{count as number}</span>
                                                </div>
                                            ))}
                                    </div>
                                </div>
                            </div>

                            <div className="mt-6 text-sm text-gray-500">
                                Total Responses: {venueAnalytics.totalResponses}
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </Layout>
    );
}