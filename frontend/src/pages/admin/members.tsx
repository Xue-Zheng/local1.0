'use client';
import { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import Link from 'next/link';
import api from '@/services/api';

export default function MembersPage() {
    const router = useRouter();
    const [isAuthorized, setIsAuthorized] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [members, setMembers] = useState<any[]>([]);
    const [search, setSearch] = useState('');
    const [events, setEvents] = useState<any[]>([]);
    const [selectedEvent, setSelectedEvent] = useState<any>(null);
    const [showEmailForm, setShowEmailForm] = useState(false);
    const [selectedMember, setSelectedMember] = useState<any>(null);
    const [showMemberDetails, setShowMemberDetails] = useState(false);

    // Simplified filtering states
    const [filters, setFilters] = useState({
        registrationStatus: '',
        contactInfo: '',
        region: '',
        industry: '',
        employer: ''
    });

    const [filterOptions, setFilterOptions] = useState({
        regions: [],
        industries: [],
        employers: []
    });

    const [showFilters, setShowFilters] = useState(false);
    const [categoryStats, setCategoryStats] = useState<any>(null);

    const [emailSubject, setEmailSubject] = useState('');
    const [emailContent, setEmailContent] = useState('');
    const [sendingEmail, setSendingEmail] = useState(false);

    const [templates, setTemplates] = useState([
        { id: 'registration', name: 'Registration Reminder', subject: 'Pre-registration Now Open: E tu Special Conference', content: 'Kia ora {{name}},\n\nPre-registration is now open for the upcoming E t&#363 Special Conference, which will be held online on Thursday, 26 June 2025, at 7:00 PM.\n\nYour Member Information:\nMembership Number: {{membershipNumber}}\nVerification Code: {{verificationCode}}\n\n[!!!CLICK HERE TO REGISTER NOW!!!]({{registrationLink}})\n\nNg&#257 mihi,\nRachel Mackintosh\nNational Secretary\nE t&#363 Union' },
        { id: 'reminder', name: 'Official Reminder', subject: 'OFFICIAL REMINDER: E tu Special Conference – 26 June 2025', content: 'Kia ora {{name}},\n\nThis is a reminder that our E t&#363 Special Conference is fast approaching.\n\nYour membership number: {{membershipNumber}}\n\nPlease take time to review these documents before the meeting, as they will be central to our discussion and decision-making.\n\nNg&#257 mihi nui,\nRachel Mackintosh, National Secretary\nTogether Stronger.' },
        { id: 'confirmation', name: 'Registration Confirmation', subject: 'E tu Invitation to Pre-Register', content: 'Dear {{name}},\nThank you for completing your pre-registration for the upcoming E t&#363 meeting.\n\nYour membership number: {{membershipNumber}}\n\nWe look forward to seeing you at the meeting.\n\nThank you,\nE t&#363 Team' }
    ]);

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }
        setIsAuthorized(true);
        fetchMembers();
        fetchEvents();

        // 从URL参数中获取eventId
        if (router.isReady) {
            const { eventId } = router.query;
            if (eventId && events.length > 0) {
                const event = events.find(e => e.id === parseInt(eventId as string));
                if (event) {
                    setSelectedEvent(event);
                }
            }
        }
    }, [router, events]);

    useEffect(() => {
        fetchMembers();
        fetchFilterOptions();
    }, [selectedEvent]);

    useEffect(() => {
        fetchMembers();
    }, [filters]);

    const fetchMembers = async () => {
        try {
            setIsLoading(true);

            // Build filter criteria
            const criteria: any = {};
            if (filters.registrationStatus) {
                criteria.registrationStatus = filters.registrationStatus;
            }
            if (filters.region) {
                criteria.region = filters.region;
            }
            if (filters.industry) {
                criteria.siteIndustryDesc = filters.industry;
            }
            if (filters.employer) {
                criteria.employer = filters.employer;
            }

            let response;
            if (selectedEvent) {
                // Event-specific: use EventMember table
                response = await api.post(`/admin/registration/events/${selectedEvent.id}/members-by-criteria?size=1000`, criteria);
            } else {
                // For BMM management, we should use EventMember table from BMM event
                // First try to get current BMM event
                const eventsResponse = await api.get('/admin/events');
                if (eventsResponse.data.status === 'success') {
                    const bmmEvent = eventsResponse.data.data.find((e: any) => e.eventType === 'BMM_VOTING');
                    if (bmmEvent) {
                        response = await api.post(`/admin/registration/events/${bmmEvent.id}/members-by-criteria?size=1000`, criteria);
                    } else {
                        // If no BMM event, use empty result
                        response = { data: { status: 'success', data: { members: [] } } };
                    }
                } else {
                    // If events call fails, use empty result
                    response = { data: { status: 'success', data: { members: [] } } };
                }
            }

            if (response.data.status === 'success') {
                const members: any[] = response.data.data.members || [];
                setMembers(members);
                calculateCategoryStats(members);
            }
        } catch (error) {
            console.error('Failed to fetch members:', error);
            toast.error('Failed to fetch member list');
        } finally {
            setIsLoading(false);
        }
    };

    const fetchEvents = async () => {
        try {
            const response = await api.get('/admin/events');
            if (response.data.status === 'success') {
                setEvents(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch events:', error);
        }
    };

    const fetchFilterOptions = async () => {
        try {
            let response;
            if (selectedEvent) {
                response = await api.get(`/admin/registration/events/${selectedEvent.id}/filter-options`);
            } else {
                // For BMM management, get filter options from BMM event
                const eventsResponse = await api.get('/admin/events');
                if (eventsResponse.data.status === 'success') {
                    const bmmEvent = eventsResponse.data.data.find((e: any) => e.eventType === 'BMM_VOTING');
                    if (bmmEvent) {
                        response = await api.get(`/admin/registration/events/${bmmEvent.id}/filter-options`);
                    } else {
                        // If no BMM event, use empty filter options
                        response = { data: { status: 'success', data: { regions: [], industries: [], employers: [] } } };
                    }
                } else {
                    // If events call fails, use empty filter options
                    response = { data: { status: 'success', data: { regions: [], industries: [], employers: [] } } };
                }
            }

            if (response.data.status === 'success') {
                setFilterOptions(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch filter options:', error);
        }
    };

    const calculateCategoryStats = (membersList: any[]) => {
        const stats = {
            total: membersList.length,
            registered: membersList.filter(m => m.hasRegistered).length,
            notRegistered: membersList.filter(m => !m.hasRegistered).length,
            attending: membersList.filter(m => m.isAttending).length,
            withEmail: membersList.filter(m => m.hasEmail).length,
            withMobile: membersList.filter(m => m.hasMobile).length,
            bothEmailAndMobile: membersList.filter(m => m.hasEmail && m.hasMobile).length,
            emailOnly: membersList.filter(m => m.hasEmail && !m.hasMobile).length,
            mobileOnly: membersList.filter(m => !m.hasEmail && m.hasMobile).length,
            noContact: membersList.filter(m => !m.hasEmail && !m.hasMobile).length
        };
        setCategoryStats(stats);
    };

    const filteredMembers = members.filter(member => {
        const matchesSearch = member.name?.toLowerCase().includes(search.toLowerCase()) ||
            member.primaryEmail?.toLowerCase().includes(search.toLowerCase()) ||
            member.membershipNumber?.toLowerCase().includes(search.toLowerCase());
        return matchesSearch;
    });

    const openEmailForm = (member: any) => {
        setSelectedMember(member);
        setEmailSubject('');
        setEmailContent('');
        setShowEmailForm(true);
    };

    const selectTemplate = (templateId: string) => {
        const template = templates.find(t => t.id === templateId);
        if (template) {
            setEmailSubject(template.subject);

            let content = template.content;
            if (selectedMember) {
                content = content.replace(/{{name}}/g, selectedMember.name || '');
                content = content.replace(/{{membershipNumber}}/g, selectedMember.membershipNumber || '');
                content = content.replace(/{{verificationCode}}/g, selectedMember.verificationCode || '');
                content = content.replace(/{{registrationLink}}/g,
                    `https://events.etu.nz/?token=${selectedMember.token}${selectedEvent ? `&event=${selectedEvent.id}` : ''}` || '');
                content = content.replace(/{{regionDesc}}/g, selectedMember.regionDesc || '');
                content = content.replace(/{{workplaceDesc}}/g, selectedMember.workplaceDesc || '');
                content = content.replace(/{{employerName}}/g, selectedMember.employerName || '');
            }
            setEmailContent(content);
        }
    };

    const sendEmail = async () => {
        if (!emailSubject || !emailContent) {
            toast.error('Please fill in the subject and content');
            return;
        }

        setSendingEmail(true);
        try {
            const response = await api.post('/admin/email/send-single', {
                email: selectedMember.primaryEmail,
                name: selectedMember.name,
                subject: emailSubject,
                content: emailContent
            });

            if (response.data.status === 'success') {
                toast.success('Email sent successfully');
                setShowEmailForm(false);
            } else {
                throw new Error(response.data.message);
            }
        } catch (error) {
            console.error('Failed to send email:', error);
            toast.error('Failed to send email');
        } finally {
            setSendingEmail(false);
        }
    };

    const viewMemberDetails = (member: any) => {
        setSelectedMember(member);
        setShowMemberDetails(true);
    };

    const clearAllFilters = () => {
        setFilters({
            registrationStatus: '',
            contactInfo: '',
            region: '',
            industry: '',
            employer: ''
        });
    };

    if (!isAuthorized) {
        return <div>Loading...</div>;
    }

    return (
        <Layout>
            <div className="container mx-auto px-4 py-8">
                <div className="flex justify-between items-center mb-6">
                    <h1 className="text-3xl font-bold text-gray-900 dark:text-white">Member Management</h1>
                </div>

                {/* Controls */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6 mb-6">
                    <div className="flex flex-col md:flex-row gap-4 items-center justify-between">
                        <input
                            type="text"
                            placeholder="Search by name, email, or membership number..."
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            className="flex-1 px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700 dark:text-white"
                        />
                        <select
                            value={selectedEvent?.id || ''}
                            onChange={(e) => {
                                const eventId = e.target.value;
                                if (eventId) {
                                    const event = events.find(ev => ev.id === parseInt(eventId));
                                    setSelectedEvent(event);
                                } else {
                                    setSelectedEvent(null);
                                }
                            }}
                            className="px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700 dark:text-white"
                        >
                            <option value="">All Members ({members.length})</option>
                            {events.map(event => (
                                <option key={event.id} value={event.id}>
                                    {event.name}
                                </option>
                            ))}
                        </select>
                        <button
                            onClick={() => setShowFilters(!showFilters)}
                            className="bg-purple-500 hover:bg-purple-600 text-white px-4 py-2 rounded"
                        >
                            🔍 {showFilters ? 'Hide' : 'Show'} Filters
                        </button>
                        <button
                            onClick={fetchMembers}
                            className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded"
                        >
                            🔄 Refresh
                        </button>
                    </div>
                </div>

                {/* Category Statistics */}
                {categoryStats && (
                    <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4 mb-6">
                        <div className="bg-blue-50 dark:bg-blue-900/30 p-4 rounded-lg text-center">
                            <div className="text-2xl font-bold text-blue-600 dark:text-blue-400">{categoryStats.total}</div>
                            <div className="text-sm text-gray-600 dark:text-gray-400">Total Members</div>
                        </div>
                        <div className="bg-green-50 dark:bg-green-900/30 p-4 rounded-lg text-center">
                            <div className="text-2xl font-bold text-green-600 dark:text-green-400">{categoryStats.registered}</div>
                            <div className="text-sm text-gray-600 dark:text-gray-400">✅ Registered</div>
                        </div>
                        <div className="bg-red-50 dark:bg-red-900/30 p-4 rounded-lg text-center">
                            <div className="text-2xl font-bold text-red-600 dark:text-red-400">{categoryStats.notRegistered}</div>
                            <div className="text-sm text-gray-600 dark:text-gray-400">❌ Not Registered</div>
                        </div>
                        <div className="bg-purple-50 dark:bg-purple-900/30 p-4 rounded-lg text-center">
                            <div className="text-2xl font-bold text-purple-600 dark:text-purple-400">{categoryStats.attending}</div>
                            <div className="text-sm text-gray-600 dark:text-gray-400">🎯 Attending</div>
                        </div>
                        <div className="bg-teal-50 dark:bg-teal-900/30 p-4 rounded-lg text-center">
                            <div className="text-2xl font-bold text-teal-600 dark:text-teal-400">{categoryStats.withEmail}</div>
                            <div className="text-sm text-gray-600 dark:text-gray-400">📧 With Email</div>
                        </div>
                        <div className="bg-indigo-50 dark:bg-indigo-900/30 p-4 rounded-lg text-center">
                            <div className="text-2xl font-bold text-indigo-600 dark:text-indigo-400">{categoryStats.withMobile}</div>
                            <div className="text-sm text-gray-600 dark:text-gray-400">📱 With Mobile</div>
                        </div>
                    </div>
                )}

                {/* Filters Panel */}
                {showFilters && (
                    <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6 mb-6">
                        <div className="flex justify-between items-center mb-4">
                            <h3 className="text-lg font-semibold text-gray-900 dark:text-white">🔍 Filters</h3>
                            <button
                                onClick={clearAllFilters}
                                className="text-sm text-red-600 hover:text-red-800 dark:text-red-400"
                            >
                                Clear All Filters
                            </button>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                            {/* Registration Status */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Registration Status</label>
                                <select
                                    value={filters.registrationStatus}
                                    onChange={(e) => setFilters(prev => ({ ...prev, registrationStatus: e.target.value }))}
                                    className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-md dark:bg-gray-700 dark:text-white"
                                >
                                    <option value="">All</option>
                                    <option value="registered">✅ Registered</option>
                                    <option value="not_registered">❌ Not Registered</option>
                                    <option value="attending">🎯 Attending</option>
                                    <option value="not_attending">⛔ Not Attending</option>
                                </select>
                            </div>

                            {/* Contact Information */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Contact Info</label>
                                <select
                                    value={filters.contactInfo}
                                    onChange={(e) => setFilters(prev => ({ ...prev, contactInfo: e.target.value }))}
                                    className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-md dark:bg-gray-700 dark:text-white"
                                >
                                    <option value="">All</option>
                                    <option value="hasBoth">📧📱 Both Email & Mobile</option>
                                    <option value="emailOnly">📧 Email Only</option>
                                    <option value="mobileOnly">📱 Mobile Only</option>
                                    <option value="hasNone">❌ No Contact Info</option>
                                </select>
                            </div>

                            {/* Region */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Region</label>
                                <select
                                    value={filters.region}
                                    onChange={(e) => setFilters(prev => ({ ...prev, region: e.target.value }))}
                                    className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-md dark:bg-gray-700 dark:text-white"
                                >
                                    <option value="">All Regions</option>
                                    {filterOptions.regions?.map((region: string) => (
                                        <option key={region} value={region}>{region}</option>
                                    ))}
                                </select>
                            </div>

                            {/* Industry */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Industry</label>
                                <select
                                    value={filters.industry}
                                    onChange={(e) => setFilters(prev => ({ ...prev, industry: e.target.value }))}
                                    className="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-md dark:bg-gray-700 dark:text-white"
                                >
                                    <option value="">All Industries</option>
                                    {filterOptions.industries?.map((industry: string) => (
                                        <option key={industry} value={industry}>{industry}</option>
                                    ))}
                                </select>
                            </div>
                        </div>

                        {/* Quick Filter Buttons */}
                        <div className="mt-4 pt-4 border-t border-gray-200 dark:border-gray-600">
                            <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Quick Filters:</h4>
                            <div className="flex flex-wrap gap-2">
                                <button
                                    onClick={() => setFilters(prev => ({ ...prev, registrationStatus: 'not_registered' }))}
                                    className="px-3 py-1 text-xs bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300 rounded-full hover:bg-red-200 dark:hover:bg-red-900/50"
                                >
                                    ❌ Unregistered ({categoryStats?.notRegistered || 0})
                                </button>
                                <button
                                    onClick={() => setFilters(prev => ({ ...prev, registrationStatus: 'attending' }))}
                                    className="px-3 py-1 text-xs bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300 rounded-full hover:bg-green-200 dark:hover:bg-green-900/50"
                                >
                                    🎯 Attending ({categoryStats?.attending || 0})
                                </button>
                                <button
                                    onClick={() => setFilters(prev => ({ ...prev, contactInfo: 'hasBoth' }))}
                                    className="px-3 py-1 text-xs bg-teal-100 text-teal-800 dark:bg-teal-900/30 dark:text-teal-300 rounded-full hover:bg-teal-200 dark:hover:bg-teal-900/50"
                                >
                                    📧📱 Both Contacts ({categoryStats?.bothEmailAndMobile || 0})
                                </button>
                                <button
                                    onClick={() => setFilters(prev => ({ ...prev, contactInfo: 'emailOnly' }))}
                                    className="px-3 py-1 text-xs bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300 rounded-full hover:bg-purple-200 dark:hover:bg-purple-900/50"
                                >
                                    📧 Email Only ({categoryStats?.emailOnly || 0})
                                </button>
                                <button
                                    onClick={() => setFilters(prev => ({ ...prev, contactInfo: 'hasNone' }))}
                                    className="px-3 py-1 text-xs bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300 rounded-full hover:bg-gray-200 dark:hover:bg-gray-600"
                                >
                                    ❌ No Contact ({categoryStats?.noContact || 0})
                                </button>
                            </div>
                        </div>
                    </div>
                )}

                {/* Members Table */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                            <thead className="bg-gray-50 dark:bg-gray-700">
                            <tr>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                                    Member Info
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                                    Contact
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                                    Work Info
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                                    Status
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                                    Location
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                                    Actions
                                </th>
                            </tr>
                            </thead>
                            <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                            {isLoading ? (
                                <tr>
                                    <td colSpan={6} className="px-6 py-4 text-center text-gray-500 dark:text-gray-400">
                                        Loading members...
                                    </td>
                                </tr>
                            ) : filteredMembers.length === 0 ? (
                                <tr>
                                    <td colSpan={6} className="px-6 py-4 text-center text-gray-500 dark:text-gray-400">
                                        No members found
                                    </td>
                                </tr>
                            ) : (
                                filteredMembers.map((member) => (
                                    <tr key={member.id} className="hover:bg-gray-50 dark:hover:bg-gray-700">
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div className="flex items-center">
                                                <div>
                                                    <div className="text-sm font-medium text-gray-900 dark:text-white">
                                                        {member.name}
                                                    </div>
                                                    <div className="text-sm text-gray-500 dark:text-gray-400">
                                                        #{member.membershipNumber}
                                                    </div>
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div className="text-sm text-gray-900 dark:text-white">
                                                {member.primaryEmail && (
                                                    <div className="flex items-center">
                                                        <span className="mr-1">📧</span>
                                                        {member.primaryEmail}
                                                    </div>
                                                )}
                                                {member.telephoneMobile && (
                                                    <div className="flex items-center mt-1">
                                                        <span className="mr-1">📱</span>
                                                        {member.telephoneMobile}
                                                    </div>
                                                )}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div className="text-sm text-gray-900 dark:text-white">
                                                {member.payrollNumber && (
                                                    <div className="flex items-center">
                                                        <span className="mr-1">💼</span>
                                                        {member.payrollNumber}
                                                    </div>
                                                )}
                                                {member.siteNumber && (
                                                    <div className="flex items-center mt-1">
                                                        <span className="mr-1">📋</span>
                                                        {member.siteNumber}
                                                    </div>
                                                )}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div className="flex flex-col space-y-1">
                                                {member.hasRegistered ? (
                                                    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300">
                                                            ✅ Registered
                                                        </span>
                                                ) : (
                                                    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300">
                                                            ❌ Not Registered
                                                        </span>
                                                )}
                                                {member.isAttending && (
                                                    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300">
                                                            🎯 Attending
                                                        </span>
                                                )}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 dark:text-gray-400">
                                            <div>{member.region}</div>
                                            <div>{member.workplace}</div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                                            {member.primaryEmail && (
                                                <button
                                                    onClick={() => {
                                                        // Store member data for email page
                                                        localStorage.setItem('preselectedMembers', JSON.stringify([{
                                                            id: member.id,
                                                            name: member.name,
                                                            primaryEmail: member.primaryEmail,
                                                            membershipNumber: member.membershipNumber
                                                        }]));
                                                        router.push('/admin/email?from=members&preselected=true');
                                                    }}
                                                    className="text-blue-600 hover:text-blue-900 dark:text-blue-400 dark:hover:text-blue-300 mr-3"
                                                >
                                                    📧 Email
                                                </button>
                                            )}
                                            {member.telephoneMobile && (
                                                <button
                                                    onClick={() => {
                                                        // Store member data for SMS page
                                                        localStorage.setItem('preselectedMembers', JSON.stringify([{
                                                            id: member.id,
                                                            name: member.name,
                                                            mobilePhone: member.telephoneMobile,
                                                            membershipNumber: member.membershipNumber
                                                        }]));
                                                        router.push('/admin/sms?from=members&preselected=true');
                                                    }}
                                                    className="text-green-600 hover:text-green-900 dark:text-green-400 dark:hover:text-green-300"
                                                >
                                                    📱 SMS
                                                </button>
                                            )}
                                        </td>
                                    </tr>
                                ))
                            )}
                            </tbody>
                        </table>
                    </div>
                </div>

                {/* Summary */}
                <div className="mt-4 text-sm text-gray-600 dark:text-gray-400 text-center">
                    Showing {filteredMembers.length} of {members.length} members
                    {selectedEvent ? ` from ${selectedEvent.name}` : ' (all members)'}
                </div>
            </div>
        </Layout>
    );
}