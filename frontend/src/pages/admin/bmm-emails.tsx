'use client';
import React, { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import api from '@/services/api';

interface FilterOptions {
    regions: string[];
    industries: string[];
    subIndustries: string[];
}

interface EmailStage {
    id: string;
    name: string;
    icon: string;
    description: string;
    targetRegions: string[];
    template: {
        subject: string;
        content: string;
    };
}

interface Variable {
    key: string;
    description: string;
    icon: string;
}

export default function BmmEmailsPage() {
    const router = useRouter();
    const [loading, setLoading] = useState(false);
    const [currentStage, setCurrentStage] = useState('pre-registration');

    // Filter states
    const [filterOptions, setFilterOptions] = useState<FilterOptions>({
        regions: [],
        industries: [],
        subIndustries: []
    });

    const [filters, setFilters] = useState({
        region: '',
        industry: '',
        subIndustry: '',
        registrationStatus: '',
        hasEmail: true,
        hasMobile: false,
        // BMM specific filters
        bmmStage: '',
        preferenceStatus: '',
        attendanceConfirmed: '',
        specialVoteStatus: '',
        hasForumDesc: '',
        hasAssignedVenue: '',
        // Search filters
        searchName: '',
        searchEmail: '',
        searchMembershipNumber: ''
    });

    // Email content
    const [subject, setSubject] = useState('');
    const [content, setContent] = useState('');
    const contentRef = useRef<HTMLTextAreaElement>(null);
    const [showVariables, setShowVariables] = useState(false);
    const [emailProvider, setEmailProvider] = useState<'STRATUM' | 'MAILJET'>('STRATUM');

    // Recipients
    const [recipients, setRecipients] = useState<any[]>([]);
    const [selectedRecipients, setSelectedRecipients] = useState<Set<string>>(new Set());
    const [previewMode, setPreviewMode] = useState(false);
    const [sending, setSending] = useState(false);
    const [sendingProgress, setSendingProgress] = useState<{
        sent: number;
        total: number;
        percentage: number;
        status: string;
    } | null>(null);
    const [bmmEvent, setBmmEvent] = useState<any>(null);

    // BMM Email Stages Definition
    const emailStages: EmailStage[] = [
        {
            id: 'pre-registration',
            name: 'Pre-Registration',
            icon: '📝',
            description: 'Invite members to register their interest',
            targetRegions: ['All'],
            template: {
                subject: 'Register your interest for BMM 2025 - E tū',
                content: `Kia ora {{firstName}},

We invite you to register your interest and preferences for the 2025 E tū Biennial Membership Meeting (BMM).

This year's BMM will be held across multiple venues to ensure all members can participate. Please let us know your preferences by clicking the link below:

🔗 {{bmmLink}}

Your Details:
• Membership Number: {{membershipNumber}}
• Region: {{region}}

If you have any questions, please contact us at support@etu.nz

Ngā mihi,
E tū Events Team`
            }
        },
        {
            id: 'confirmation',
            name: 'Confirmation',
            icon: '✅',
            description: 'Ask members to confirm attendance (Stage 2)',
            targetRegions: ['All'],
            template: {
                subject: 'BMM 2025 - Action Required: Confirm Your Attendance',
                content: `Kia ora {{firstName}},

Thank you for submitting your preferences for BMM 2025. Based on your preferences and availability, we have assigned you to the following meeting:

📍 Venue: {{assignedVenue}}
📅 Date & Time: {{assignedDateTime}}

⚠️ IMPORTANT: You must now confirm whether you will attend.

Please click the link below to confirm your attendance or indicate if you cannot attend:

🔗 {{confirmationLink}}

This is Stage 2 of the registration process. In this stage you will:
• Review your pre-registration information
• Update your contact and financial information if needed
• Confirm whether you will attend the BMM
• If you cannot attend (Southern region), apply for special voting rights

If you cannot attend:
• All members: Please provide a reason for non-attendance
• Southern Region members: You may apply for special voting rights if eligible

The deadline to confirm is [DATE]. Please act promptly.

If you have any questions, please contact us at support@etu.nz

Ngā mihi,
E tū Events Team`
            }
        },
        {
            id: 'special-vote',
            name: 'Special Vote',
            icon: '🗳️',
            description: 'Special voting application',
            targetRegions: ['Central', 'Southern'],
            template: {
                subject: 'BMM 2025 - Special Voting Application',
                content: `Kia ora {{firstName}},

As you are unable to attend the BMM in person, you may be eligible to apply for special voting rights.

To apply, please click the link below:

🔗 https://events.etu.nz/register/special-vote?token={{memberToken}}

Note: Special voting is only available to members in the Central and Southern regions who meet specific criteria.

Ngā mihi,
E tū Events Team`
            }
        }
    ];

    // Available variables
    const variables: Variable[] = [
        { key: 'firstName', description: 'First name', icon: '👤' },
        { key: 'name', description: 'Full name', icon: '👥' },
        { key: 'membershipNumber', description: 'Membership #', icon: '🆔' },
        { key: 'region', description: 'Region', icon: '📍' },
        { key: 'memberToken', description: 'Member token', icon: '🎫' },
        { key: 'bmmLink', description: 'BMM main link', icon: '🔗' },
        { key: 'confirmationLink', description: 'Confirmation link', icon: '✅' },
        { key: 'specialVoteLink', description: 'Special vote link', icon: '🗳️' },
        { key: 'assignedVenue', description: 'Venue name', icon: '🏢' },
        { key: 'assignedDateTime', description: 'Date & time', icon: '📅' },
        { key: 'verificationCode', description: 'Verification code', icon: '🔑' },
        { key: 'forumDesc', description: 'Forum description', icon: '🏛️' },
        { key: 'preferredTimes', description: 'Preferred times', icon: '⏰' },
        { key: 'workplaceInfo', description: 'Workplace info', icon: '🏭' },
        { key: 'ticketUrl', description: 'Ticket URL', icon: '🎟️' },
        { key: 'additionalComments', description: 'Comments', icon: '💭' }
    ];

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }

        fetchBmmEvent();
        fetchFilterOptions();

        // Load template for default stage
        const stage = emailStages.find(s => s.id === currentStage);
        if (stage) {
            setSubject(stage.template.subject);
            setContent(stage.template.content);
        }
    }, [router]);

    const fetchBmmEvent = async () => {
        try {
            const response = await api.get('/admin/events');
            if (response.data.status === 'success') {
                const events = response.data.data || [];
                const bmmEvent = events.find((e: any) => e.eventType === 'BMM_VOTING');
                setBmmEvent(bmmEvent);
            }
        } catch (error) {
            console.error('Failed to fetch BMM event:', error);
        }
    };

    const fetchFilterOptions = async () => {
        try {
            const response = await api.get('/admin/registration/filter-options');
            if (response.data.status === 'success') {
                setFilterOptions({
                    regions: response.data.data.regions || [],
                    industries: response.data.data.industries || [],
                    subIndustries: response.data.data.subIndustries || []
                });
            }
        } catch (error) {
            console.error('Failed to load filter options:', error);
        }
    };

    const handleStageChange = (stageId: string) => {
        setCurrentStage(stageId);
        const stage = emailStages.find(s => s.id === stageId);
        if (stage) {
            setSubject(stage.template.subject);
            setContent(stage.template.content);

            // Reset region filter if stage has specific regions
            if (stage.targetRegions.includes('Central') || stage.targetRegions.includes('Southern')) {
                setFilters({ ...filters, region: '' });
            }
        }
    };

    const insertVariable = (variable: string) => {
        if (!contentRef.current) return;

        const textarea = contentRef.current;
        const start = textarea.selectionStart;
        const end = textarea.selectionEnd;
        const text = textarea.value;
        const varText = `{{${variable}}}`;

        const newText = text.substring(0, start) + varText + text.substring(end);
        setContent(newText);

        // Set cursor position after inserted variable
        setTimeout(() => {
            textarea.selectionStart = start + varText.length;
            textarea.selectionEnd = start + varText.length;
            textarea.focus();
        }, 0);
    };

    const getFilteredSubIndustries = () => {
        // If an industry is selected, you could filter sub-industries here
        return filterOptions.subIndustries;
    };

    const handlePreview = async () => {
        if (!bmmEvent) {
            toast.error('BMM event not found');
            return;
        }

        setPreviewMode(true);
        setLoading(true);

        try {
            const criteria: any = {
                eventId: bmmEvent.id
            };

            // Apply filters
            if (filters.region) criteria.region = filters.region;
            if (filters.industry) criteria.siteIndustryDesc = filters.industry;
            if (filters.subIndustry) criteria.siteSubIndustryDesc = filters.subIndustry;
            if (filters.registrationStatus) criteria.registrationStatus = filters.registrationStatus;

            // Apply search filters
            if (filters.searchName) criteria.searchName = filters.searchName;
            if (filters.searchEmail) criteria.searchEmail = filters.searchEmail;
            if (filters.searchMembershipNumber) criteria.searchMembershipNumber = filters.searchMembershipNumber;

            // BMM specific filters
            if (filters.bmmStage) criteria.bmmStage = filters.bmmStage;
            if (filters.preferenceStatus) criteria.preferenceStatus = filters.preferenceStatus;
            if (filters.attendanceConfirmed) criteria.attendanceConfirmed = filters.attendanceConfirmed;
            if (filters.specialVoteStatus) criteria.specialVoteStatus = filters.specialVoteStatus;
            if (filters.hasForumDesc) criteria.hasForumDesc = filters.hasForumDesc;
            if (filters.hasAssignedVenue) criteria.hasAssignedVenue = filters.hasAssignedVenue;

            // Contact method filter
            if (filters.hasEmail && !filters.hasMobile) {
                criteria.contactInfo = 'emailOnly';
            } else if (!filters.hasEmail && filters.hasMobile) {
                criteria.contactInfo = 'mobileOnly';
            } else if (filters.hasEmail && filters.hasMobile) {
                criteria.contactInfo = 'both';
            }

            const response = await api.post('/admin/email/preview-advanced', { criteria });

            if (response.data.status === 'success') {
                const members = response.data.data.members || [];
                setRecipients(members);

                // Auto-select all recipients
                const allIds = new Set<string>(members.map((m: any) =>
                    (m.id?.toString() || m.email || m.membershipNumber) as string
                ));
                setSelectedRecipients(allIds);

                toast.success(`Found ${members.length} recipients`);
            }
        } catch (error) {
            toast.error('Failed to load recipients');
            setPreviewMode(false);
        } finally {
            setLoading(false);
        }
    };

    const handleSend = async () => {
        if (!subject || !content) {
            toast.error('Please enter subject and content');
            return;
        }

        if (selectedRecipients.size === 0) {
            toast.error('Please select at least one recipient');
            return;
        }

        setSending(true);
        const totalRecipients = selectedRecipients.size;

        // Initialize progress
        setSendingProgress({
            sent: 0,
            total: totalRecipients,
            percentage: 0,
            status: 'Preparing to send emails...'
        });

        try {
            const selectedMemberIds = Array.from(selectedRecipients);

            // Update progress - emails queued
            setSendingProgress({
                sent: 0,
                total: totalRecipients,
                percentage: 10,
                status: 'Queueing emails for delivery...'
            });

            const response = await api.post('/admin/email/send-advanced', {
                eventId: bmmEvent?.id,
                subject,
                content,
                criteria: {
                    memberIds: selectedMemberIds
                },
                emailType: `BMM_${currentStage.toUpperCase().replace('-', '_')}`,
                provider: emailProvider
            });

            if (response.data.status === 'success') {
                // Simulate progress updates
                setSendingProgress({
                    sent: Math.round(totalRecipients * 0.5),
                    total: totalRecipients,
                    percentage: 50,
                    status: 'Emails being processed...'
                });

                // Wait a bit then show completion
                setTimeout(() => {
                    setSendingProgress({
                        sent: totalRecipients,
                        total: totalRecipients,
                        percentage: 100,
                        status: 'All emails queued successfully!'
                    });

                    setTimeout(() => {
                        toast.success(`Emails queued for ${selectedMemberIds.length} recipients`);
                        setPreviewMode(false);
                        setRecipients([]);
                        setSelectedRecipients(new Set());
                        setSendingProgress(null);
                        setSending(false);
                    }, 1500);
                }, 1000);
            } else {
                throw new Error(response.data.message || 'Failed to send emails');
            }
        } catch (error: any) {
            setSendingProgress({
                sent: 0,
                total: totalRecipients,
                percentage: 0,
                status: 'Error occurred while sending emails'
            });
            toast.error(error.message || 'Failed to send emails');
            setSendingProgress(null);
            setSending(false);
        }
    };

    const toggleRecipient = (id: string) => {
        const newSelected = new Set(selectedRecipients);
        if (newSelected.has(id)) {
            newSelected.delete(id);
        } else {
            newSelected.add(id);
        }
        setSelectedRecipients(newSelected);
    };

    const selectAll = () => {
        const allIds = new Set<string>(recipients.map(r =>
            r.id?.toString() || r.email || r.membershipNumber
        ));
        setSelectedRecipients(allIds);
    };

    const selectNone = () => {
        setSelectedRecipients(new Set());
    };

    const currentStageData = emailStages.find(s => s.id === currentStage);

    return (
        <Layout>
            <div className="container mx-auto px-4 py-8">
                <div className="mb-8">
                    <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
                        BMM Email Campaign
                    </h1>
                    <p className="text-gray-600 dark:text-gray-400 mt-2">
                        Manage three-stage email communications for BMM 2025
                    </p>
                </div>

                {/* Email Stage Selection */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow mb-6 p-6">
                    <h2 className="text-lg font-semibold mb-4">Select Email Stage</h2>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        {emailStages.map(stage => (
                            <button
                                key={stage.id}
                                onClick={() => handleStageChange(stage.id)}
                                className={`p-4 rounded-lg border-2 transition-all ${
                                    currentStage === stage.id
                                        ? 'border-blue-500 bg-blue-50 dark:bg-blue-900'
                                        : 'border-gray-200 hover:border-gray-300'
                                }`}
                            >
                                <div className="text-3xl mb-2">{stage.icon}</div>
                                <div className="font-semibold">{stage.name}</div>
                                <div className="text-sm text-gray-600 dark:text-gray-400">
                                    {stage.description}
                                </div>
                                <div className="mt-2 text-xs text-gray-500">
                                    Target: {stage.targetRegions.join(', ')}
                                </div>
                            </button>
                        ))}
                    </div>
                </div>

                {/* Recipients Filter */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow mb-6 p-6">
                    <h2 className="text-lg font-semibold mb-4">Filter Recipients</h2>

                    {/* Search Section */}
                    <div className="mb-6 p-4 bg-blue-50 dark:bg-blue-900 rounded-lg">
                        <h3 className="text-md font-medium mb-3 text-blue-800 dark:text-blue-200">🔍 Search Specific Members</h3>
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                            <div>
                                <label className="block text-sm font-medium mb-2">Search by Name</label>
                                <input
                                    type="text"
                                    value={filters.searchName}
                                    onChange={(e) => setFilters({...filters, searchName: e.target.value})}
                                    placeholder="Enter name..."
                                    className="w-full border rounded px-3 py-2 text-sm"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium mb-2">Search by Email</label>
                                <input
                                    type="text"
                                    value={filters.searchEmail}
                                    onChange={(e) => setFilters({...filters, searchEmail: e.target.value})}
                                    placeholder="Enter email..."
                                    className="w-full border rounded px-3 py-2 text-sm"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium mb-2">Search by Membership #</label>
                                <input
                                    type="text"
                                    value={filters.searchMembershipNumber}
                                    onChange={(e) => setFilters({...filters, searchMembershipNumber: e.target.value})}
                                    placeholder="Enter membership number..."
                                    className="w-full border rounded px-3 py-2 text-sm"
                                />
                            </div>
                        </div>
                        <p className="text-xs text-blue-600 dark:text-blue-300 mt-2">
                            💡 Tip: Use search to find specific members for testing email delivery
                        </p>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        {/* Region Filter */}
                        <div>
                            <label className="block text-sm font-medium mb-2">Region</label>
                            <select
                                value={filters.region}
                                onChange={(e) => setFilters({...filters, region: e.target.value})}
                                className="w-full border rounded px-3 py-2"
                                disabled={currentStageData?.targetRegions.includes('Central') &&
                                    currentStageData?.targetRegions.includes('Southern') &&
                                    !currentStageData?.targetRegions.includes('All')}
                            >
                                <option value="">All Regions</option>
                                {filterOptions.regions.map(region => (
                                    <option key={region} value={region}>{region}</option>
                                ))}
                            </select>
                            {currentStage === 'special-vote' && (
                                <p className="text-xs text-orange-600 mt-1">
                                    ⚠️ Special vote is only for Central & Southern regions
                                </p>
                            )}
                        </div>

                        {/* Industry Filter */}
                        <div>
                            <label className="block text-sm font-medium mb-2">Industry</label>
                            <select
                                value={filters.industry}
                                onChange={(e) => setFilters({...filters, industry: e.target.value})}
                                className="w-full border rounded px-3 py-2"
                            >
                                <option value="">All Industries</option>
                                {filterOptions.industries.map(industry => (
                                    <option key={industry} value={industry}>{industry}</option>
                                ))}
                            </select>
                        </div>

                        {/* Sub-Industry Filter */}
                        <div>
                            <label className="block text-sm font-medium mb-2">Sub-Industry</label>
                            <select
                                value={filters.subIndustry}
                                onChange={(e) => setFilters({...filters, subIndustry: e.target.value})}
                                className="w-full border rounded px-3 py-2"
                            >
                                <option value="">All Sub-Industries</option>
                                {getFilteredSubIndustries().map(subIndustry => (
                                    <option key={subIndustry} value={subIndustry}>{subIndustry}</option>
                                ))}
                            </select>
                        </div>

                        {/* Registration Status */}
                        <div>
                            <label className="block text-sm font-medium mb-2">Registration Status</label>
                            <select
                                value={filters.registrationStatus}
                                onChange={(e) => setFilters({...filters, registrationStatus: e.target.value})}
                                className="w-full border rounded px-3 py-2"
                            >
                                <option value="">All Status</option>
                                <option value="registered">Registered</option>
                                <option value="not_registered">Not Registered</option>
                                <option value="attending">Attending</option>
                                <option value="not_attending">Not Attending</option>
                            </select>
                        </div>

                        {/* Contact Method */}
                        <div>
                            <label className="block text-sm font-medium mb-2">Contact Method</label>
                            <div className="space-y-2">
                                <label className="flex items-center">
                                    <input
                                        type="checkbox"
                                        checked={filters.hasEmail}
                                        onChange={(e) => setFilters({...filters, hasEmail: e.target.checked})}
                                        className="mr-2"
                                    />
                                    <span>Has Email</span>
                                </label>
                                <label className="flex items-center">
                                    <input
                                        type="checkbox"
                                        checked={filters.hasMobile}
                                        onChange={(e) => setFilters({...filters, hasMobile: e.target.checked})}
                                        className="mr-2"
                                    />
                                    <span>Has Mobile</span>
                                </label>
                            </div>
                        </div>
                    </div>

                    {/* BMM Specific Filters - Second Row */}
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mt-4">
                        {/* BMM Stage Filter */}
                        <div>
                            <label className="block text-sm font-medium mb-2">BMM Stage</label>
                            <select
                                value={filters.bmmStage}
                                onChange={(e) => setFilters({...filters, bmmStage: e.target.value})}
                                className="w-full border rounded px-3 py-2"
                            >
                                <option value="">All Stages</option>
                                <option value="INVITED">Invited</option>
                                <option value="PREFERENCE_SUBMITTED">Preference Submitted</option>
                                <option value="VENUE_ASSIGNED">Venue Assigned</option>
                                <option value="ATTENDANCE_CONFIRMED">Attendance Confirmed</option>
                                <option value="NOT_ATTENDING">Not Attending</option>
                                <option value="TICKET_ISSUED">Ticket Issued</option>
                                <option value="CHECKED_IN">Checked In</option>
                            </select>
                        </div>

                        {/* Attendance Confirmed Filter */}
                        <div>
                            <label className="block text-sm font-medium mb-2">Attendance Status</label>
                            <select
                                value={filters.attendanceConfirmed}
                                onChange={(e) => setFilters({...filters, attendanceConfirmed: e.target.value})}
                                className="w-full border rounded px-3 py-2"
                            >
                                <option value="">All</option>
                                <option value="confirmed">Confirmed</option>
                                <option value="not_confirmed">Not Confirmed</option>
                                <option value="declined">Declined</option>
                            </select>
                        </div>

                        {/* Special Vote Status */}
                        <div>
                            <label className="block text-sm font-medium mb-2">Special Vote Status</label>
                            <select
                                value={filters.specialVoteStatus}
                                onChange={(e) => setFilters({...filters, specialVoteStatus: e.target.value})}
                                className="w-full border rounded px-3 py-2"
                            >
                                <option value="">All</option>
                                <option value="eligible">Eligible (Central/Southern only)</option>
                                <option value="requested">Applied for Special Vote</option>
                                <option value="not_attending_no_special">Not Attending, No Special Vote</option>
                                <option value="pending">Pending</option>
                                <option value="approved">Approved</option>
                                <option value="declined">Declined</option>
                            </select>
                        </div>

                        {/* Forum Assignment */}
                        <div>
                            <label className="block text-sm font-medium mb-2">Forum Assignment</label>
                            <select
                                value={filters.hasForumDesc}
                                onChange={(e) => setFilters({...filters, hasForumDesc: e.target.value})}
                                className="w-full border rounded px-3 py-2"
                            >
                                <option value="">All</option>
                                <option value="has_forum">Has Forum</option>
                                <option value="no_forum">No Forum</option>
                            </select>
                        </div>

                        {/* Venue Assignment */}
                        <div>
                            <label className="block text-sm font-medium mb-2">Venue Assignment</label>
                            <select
                                value={filters.hasAssignedVenue}
                                onChange={(e) => setFilters({...filters, hasAssignedVenue: e.target.value})}
                                className="w-full border rounded px-3 py-2"
                            >
                                <option value="">All</option>
                                <option value="has_venue">Has Venue</option>
                                <option value="no_venue">No Venue</option>
                            </select>
                        </div>

                        {/* Preference Status */}
                        <div>
                            <label className="block text-sm font-medium mb-2">Preference Status</label>
                            <select
                                value={filters.preferenceStatus}
                                onChange={(e) => setFilters({...filters, preferenceStatus: e.target.value})}
                                className="w-full border rounded px-3 py-2"
                            >
                                <option value="">All</option>
                                <option value="submitted">Submitted</option>
                                <option value="not_submitted">Not Submitted</option>
                            </select>
                        </div>
                    </div>

                    <div className="mt-4 flex justify-end">
                        <button
                            onClick={handlePreview}
                            disabled={loading}
                            className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 disabled:bg-gray-400"
                        >
                            {loading ? 'Loading...' : 'Preview Recipients'}
                        </button>
                    </div>
                </div>

                {/* Email Content */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow mb-6 p-6">
                    <div className="flex justify-between items-center mb-4">
                        <h2 className="text-lg font-semibold">Email Content</h2>
                        <button
                            onClick={() => setShowVariables(!showVariables)}
                            className="text-blue-600 hover:text-blue-800 text-sm"
                        >
                            {showVariables ? 'Hide Variables' : 'Show Variables'}
                        </button>
                    </div>

                    {/* Variables Panel */}
                    {showVariables && (
                        <div className="mb-4 p-4 bg-blue-50 dark:bg-blue-900 rounded-lg">
                            <h3 className="font-medium mb-2">Click to insert variables:</h3>
                            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-2">
                                {variables.map(v => (
                                    <button
                                        key={v.key}
                                        onClick={() => insertVariable(v.key)}
                                        className="text-left p-2 bg-white dark:bg-gray-800 rounded hover:bg-gray-100 dark:hover:bg-gray-700"
                                    >
                                        <div className="flex items-center">
                                            <span className="mr-2">{v.icon}</span>
                                            <div>
                                                <div className="text-xs font-mono">{`{{${v.key}}}`}</div>
                                                <div className="text-xs text-gray-500">{v.description}</div>
                                            </div>
                                        </div>
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Subject */}
                    <div className="mb-4">
                        <label className="block text-sm font-medium mb-2">Subject</label>
                        <input
                            type="text"
                            value={subject}
                            onChange={(e) => setSubject(e.target.value)}
                            className="w-full border rounded px-3 py-2"
                            placeholder="Enter email subject..."
                        />
                    </div>

                    {/* Email Provider Selection */}
                    <div className="mb-4">
                        <label className="block text-sm font-medium mb-2">Email Provider</label>
                        <div className="flex space-x-4">
                            <button
                                onClick={() => setEmailProvider('STRATUM')}
                                className={`px-4 py-2 rounded transition-colors ${
                                    emailProvider === 'STRATUM'
                                        ? 'bg-blue-600 text-white'
                                        : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                                }`}
                            >
                                Stratum (Default)
                            </button>
                            <button
                                onClick={() => setEmailProvider('MAILJET')}
                                className={`px-4 py-2 rounded transition-colors ${
                                    emailProvider === 'MAILJET'
                                        ? 'bg-green-600 text-white'
                                        : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                                }`}
                            >
                                Mailjet
                            </button>
                        </div>
                        <p className="text-xs text-gray-500 mt-1">
                            {emailProvider === 'STRATUM'
                                ? 'Using Stratum for email delivery (existing system)'
                                : 'Using Mailjet for email delivery (new system)'}
                        </p>
                    </div>

                    {/* Content */}
                    <div className="mb-4">
                        <label className="block text-sm font-medium mb-2">Content</label>
                        <textarea
                            ref={contentRef}
                            value={content}
                            onChange={(e) => setContent(e.target.value)}
                            rows={15}
                            className="w-full border rounded px-3 py-2 font-mono text-sm"
                            placeholder="Enter email content..."
                        />
                    </div>

                    <div className="bg-yellow-50 dark:bg-yellow-900 p-3 rounded text-sm">
                        <p className="text-yellow-800 dark:text-yellow-200">
                            ⚠️ <strong>Note:</strong> Emails are sent as plain text. HTML formatting is not supported.
                        </p>
                    </div>
                </div>

                {/* Recipients Preview */}
                {previewMode && (
                    <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
                        <div className="flex justify-between items-center mb-4">
                            <h2 className="text-lg font-semibold">
                                Recipients ({selectedRecipients.size} of {recipients.length} selected)
                            </h2>
                            <div className="space-x-2">
                                <button
                                    onClick={selectAll}
                                    className="text-blue-600 hover:text-blue-800 text-sm"
                                >
                                    Select All
                                </button>
                                <button
                                    onClick={selectNone}
                                    className="text-blue-600 hover:text-blue-800 text-sm"
                                >
                                    Select None
                                </button>
                            </div>
                        </div>

                        <div className="max-h-96 overflow-y-auto">
                            <table className="min-w-full">
                                <thead className="bg-gray-50 dark:bg-gray-700 sticky top-0">
                                <tr>
                                    <th className="px-4 py-2 text-left">
                                        <input
                                            type="checkbox"
                                            checked={selectedRecipients.size === recipients.length && recipients.length > 0}
                                            onChange={() => {
                                                if (selectedRecipients.size === recipients.length) {
                                                    selectNone();
                                                } else {
                                                    selectAll();
                                                }
                                            }}
                                        />
                                    </th>
                                    <th className="px-4 py-2 text-left">Name</th>
                                    <th className="px-4 py-2 text-left">Email</th>
                                    <th className="px-4 py-2 text-left">Region</th>
                                    <th className="px-4 py-2 text-left">Industry</th>
                                    <th className="px-4 py-2 text-left">Status</th>
                                </tr>
                                </thead>
                                <tbody>
                                {recipients.map((member) => {
                                    const id = member.id?.toString() || member.email || member.membershipNumber;
                                    return (
                                        <tr key={id} className="border-b hover:bg-gray-50 dark:hover:bg-gray-700">
                                            <td className="px-4 py-2">
                                                <input
                                                    type="checkbox"
                                                    checked={selectedRecipients.has(id)}
                                                    onChange={() => toggleRecipient(id)}
                                                />
                                            </td>
                                            <td className="px-4 py-2">{member.name}</td>
                                            <td className="px-4 py-2">{member.primaryEmail}</td>
                                            <td className="px-4 py-2">{member.regionDesc}</td>
                                            <td className="px-4 py-2">{member.siteIndustryDesc}</td>
                                            <td className="px-4 py-2">
                                                {member.hasRegistered ? (
                                                    <span className="text-green-600">Registered</span>
                                                ) : (
                                                    <span className="text-gray-500">Not Registered</span>
                                                )}
                                            </td>
                                        </tr>
                                    );
                                })}
                                </tbody>
                            </table>
                        </div>

                        {/* Progress Bar */}
                        {sendingProgress && (
                            <div className="mt-4 p-4 bg-blue-50 dark:bg-blue-900 rounded-lg">
                                <div className="flex justify-between items-center mb-2">
                                    <h4 className="font-medium text-blue-800 dark:text-blue-200">
                                        Sending Progress
                                    </h4>
                                    <span className="text-sm text-blue-600 dark:text-blue-300">
                                        {sendingProgress.sent} / {sendingProgress.total} emails
                                    </span>
                                </div>

                                {/* Progress Bar */}
                                <div className="w-full bg-gray-200 rounded-full h-3 mb-3">
                                    <div
                                        className="bg-gradient-to-r from-blue-500 to-green-500 h-3 rounded-full transition-all duration-500 ease-out"
                                        style={{ width: `${sendingProgress.percentage}%` }}
                                    ></div>
                                </div>

                                <div className="flex justify-between items-center text-sm">
                                    <span className="text-gray-600 dark:text-gray-400">{sendingProgress.status}</span>
                                    <span className="font-medium text-blue-600 dark:text-blue-300">
                                        {Math.round(sendingProgress.percentage)}%
                                    </span>
                                </div>

                                {emailProvider === 'MAILJET' && (
                                    <div className="mt-2 text-xs text-green-600 dark:text-green-400">
                                        📧 Using Mailjet for delivery
                                    </div>
                                )}
                                {emailProvider === 'STRATUM' && (
                                    <div className="mt-2 text-xs text-blue-600 dark:text-blue-400">
                                        📧 Using Stratum for delivery
                                    </div>
                                )}
                            </div>
                        )}

                        <div className="mt-4 flex justify-end">
                            <button
                                onClick={handleSend}
                                disabled={sending || selectedRecipients.size === 0}
                                className="bg-green-600 text-white px-6 py-2 rounded hover:bg-green-700 disabled:bg-gray-400 transition-colors"
                            >
                                {sending ? (
                                    <div className="flex items-center">
                                        <svg className="animate-spin -ml-1 mr-3 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                        </svg>
                                        Sending...
                                    </div>
                                ) : (
                                    `Send to ${selectedRecipients.size} members`
                                )}
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </Layout>
    );
}