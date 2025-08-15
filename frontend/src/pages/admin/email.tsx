'use client';
import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import api from "@/services/api";

interface FilterOptions {
    regions: string[];
    industries: string[];
    subIndustries: string[];
}

interface EmailTemplate {
    name: string;
    subject: string;
    content: string;
}

export default function EmailBroadcastPage() {
    const router = useRouter();
    const [loading, setLoading] = useState(true);
    const [events, setEvents] = useState<any[]>([]);
    const [selectedEvent, setSelectedEvent] = useState<any>(null);
    const [preSelectedMembers, setPreSelectedMembers] = useState<any[]>([]);

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
        attendanceIntention: '',
        venueAssignment: '',
        excludeForums: '',
        includeForums: '',
        specificTimePreference: '',
        // Search filters
        searchName: '',
        searchEmail: '',
        searchMembershipNumber: ''
    });

    // Email content
    const [subject, setSubject] = useState('');
    const [content, setContent] = useState('');
    const [selectedTemplate, setSelectedTemplate] = useState('');
    const [emailProvider, setEmailProvider] = useState<'STRATUM' | 'MAILJET'>('STRATUM');

    // Preview and sending
    const [previewMode, setPreviewMode] = useState(false);
    const [recipients, setRecipients] = useState<any[]>([]);
    const [selectedRecipients, setSelectedRecipients] = useState<Set<string>>(new Set());
    const [sending, setSending] = useState(false);
    const [sendingProgress, setSendingProgress] = useState<{
        sent: number;
        total: number;
        percentage: number;
        status: string;
    } | null>(null);

    // Variable insertion helper
    const [showVariableHelper, setShowVariableHelper] = useState(false);
    const [cursorPosition, setCursorPosition] = useState<'subject' | 'content' | null>(null);

    // Available variables
    const variables = [
        { key: 'name', desc: 'Full name' },
        { key: 'firstName', desc: 'First name' },
        { key: 'membershipNumber', desc: 'Membership number' },
        { key: 'region', desc: 'Region' },
        { key: 'verificationCode', desc: 'Verification code' },
        { key: 'registrationLink', desc: 'Registration link' },
        { key: 'confirmationLink', desc: 'Confirmation link' },
        { key: 'ticketUrl', desc: 'Ticket URL' },
        { key: 'assignedVenue', desc: 'Assigned venue' },
        { key: 'assignedDateTime', desc: 'Meeting date/time' },
        { key: 'bmmLink', desc: 'BMM main link' },
        { key: 'preferenceLink', desc: 'Stage 1 preference link' },
        { key: 'specialVoteLink', desc: 'Special vote link' },
        { key: 'preferredTimes', desc: 'Preferred session times' },
        { key: 'workplaceInfo', desc: 'Workplace information' },
        { key: 'forumDesc', desc: 'Forum description' }
    ];

    // Email templates
    const templates: { [key: string]: EmailTemplate } = {
        bmm_invitation: {
            name: 'BMM Invitation',
            subject: 'Register your interest for the 2025 E tū Biennial Membership Meeting',
            content: `Kia ora {{firstName}},

We're pleased to invite you to attend the 2025 E tū Biennial Membership Meeting (BMMs) - your opportunity to stay informed, have your say, and help shape the future of our union.

What are the BMMs about?
These meetings are a vital part of our union democracy. At these meetings members will:

• Hear updates on current issues and union campaigns
• Discuss E tū's strategic direction
• Debate and vote on National Executive (Regional Representative) (Southern Region only)

Meeting details:
The meetings will take place in September 2025. At the stage, 25 meetings have been planned across the country. After reviewing member pre-registrations, we may be able to add more meetings so make sure everyone has a chance to be involved.

Paid attendance and transport:
In line with Section 26 of the Employment Relations Act, this is a paid union meeting and you are entitled to attend during paid work hours. Where possible we will also help with transport for groups of members from the same workplace.

If your local meeting is scheduled outside of your work hours, you should still join the meeting if you can – just be aware that you won't be paid for attending.

Pre-register now
We're asking all members to register their interest by 31 July 2025. Once we receive your pre-registration, we'll send you the final meeting details and ask you to confirm that you're coming from 1 August 2025.

{{ENTER THE MAIL MERGED SPECIFIC MEETING DETAILS FOR THE MEMBER HERE!}}

Click here to pre-register for your local BMM!

{{preferenceLink}}

Employer notification
After you confirm your attendance at a specific BMM, we will write directly to your employer to notify them know and confirm you will still be paid if the meeting is during your work hours.

Special vote applications (only for the Southern Region members)
If you can't attend a meeting due to illness, disability, or other unexpected circumstances, you may apply for a special vote. This application must be made at least 14 days before the expected meeting date.

Ngā mihi,
E tū Events Team`
        },
        bmm_reminder: {
            name: 'BMM Reminder',
            subject: 'Reminder: BMM 2025 Registration',
            content: `Kia ora {{firstName}},

This is a friendly reminder to register for BMM 2025.

Register now: {{registrationLink}}

If you have any questions, reply to this email.

Ngā mihi,
E tū Events Team`
        },
        bmm_stage2_confirmation: {
            name: 'BMM Stage 2 - Confirmation Request',
            subject: 'Action Required: Confirm Your BMM 2025 Attendance',
            content: `Kia ora {{firstName}},

Thank you for submitting your preferences for BMM 2025. 

Based on your preferences:
- Preferred times: {{preferredTimes}}
- Workplace: {{workplaceInfo}}

Your assigned meeting details:
📍 Venue: {{assignedVenue}}
📅 Date & Time: {{assignedDateTime}}

⚠️ IMPORTANT: You must now confirm whether you will attend.

Please click the link below to confirm your attendance:
🔗 {{confirmationLink}}

The deadline to confirm is [DATE]. Please act promptly.

Ngā mihi,
E tū Events Team`
        },
        bmm_confirmed: {
            name: 'BMM Attendance Confirmed',
            subject: 'Confirmed: Your BMM 2025 Attendance',
            content: `Kia ora {{firstName}},

Thank you for confirming your attendance at BMM 2025.

Your ticket has been generated and can be accessed here:
🎟️ {{ticketUrl}}

Meeting details:
📍 Venue: {{assignedVenue}}
📅 Date & Time: {{assignedDateTime}}

Please save this ticket - you will need it for check-in.

Ngā mihi,
E tū Events Team`
        },
        custom: {
            name: 'Custom Email',
            subject: '',
            content: ''
        }
    };

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }

        fetchEvents();
        fetchFilterOptions();

        // Check for pre-selected members from BMM management
        const savedMembers = localStorage.getItem('preSelectedMembers');
        if (savedMembers) {
            const data = JSON.parse(savedMembers);
            // Handle both formats: direct array or object with targetMembers
            const members = Array.isArray(data) ? data : (data.targetMembers || []);
            setPreSelectedMembers(members);
            // Clear after reading to prevent stale data
            localStorage.removeItem('preSelectedMembers');
        }
    }, [router]);

    const fetchEvents = async () => {
        try {
            const response = await api.get('/admin/events');
            if (response.data.status === 'success') {
                const eventList = response.data.data || [];
                setEvents(eventList);

                // Auto-select BMM event if available
                const bmmEvent = eventList.find((e: any) => e.eventType === 'BMM_VOTING');
                if (bmmEvent) {
                    setSelectedEvent(bmmEvent);
                }
            }
        } catch (error) {
            toast.error('Failed to load events');
        } finally {
            setLoading(false);
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

    const getFilteredSubIndustries = () => {
        // If an industry is selected, filter sub-industries (you might want to implement this logic)
        return filterOptions.subIndustries;
    };

    const handleTemplateSelect = (templateKey: string) => {
        setSelectedTemplate(templateKey);
        if (templateKey !== 'custom') {
            const template = templates[templateKey];
            setSubject(template.subject);
            setContent(template.content);
        } else {
            setSubject('');
            setContent('');
        }
    };

    const insertVariable = (variable: string) => {
        const varText = `{{${variable}}}`;

        if (cursorPosition === 'subject') {
            setSubject(prev => prev + varText);
        } else if (cursorPosition === 'content') {
            setContent(prev => prev + varText);
        }

        setShowVariableHelper(false);
        setCursorPosition(null);
    };

    const handlePreview = async () => {
        if (!selectedEvent) {
            toast.error('Please select an event');
            return;
        }

        setPreviewMode(true);

        // If we have pre-selected members from BMM management, use them directly
        if (preSelectedMembers.length > 0) {
            setRecipients(preSelectedMembers);
            const allIds = new Set<string>(preSelectedMembers.map((m: any) => (m.id?.toString() || m.email) as string));
            setSelectedRecipients(allIds);
            toast.success(`Loaded ${preSelectedMembers.length} pre-selected recipients`);
            return;
        }

        try {
            const criteria: any = {
                eventId: selectedEvent.id
            };

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
            if (filters.attendanceIntention) criteria.attendanceIntention = filters.attendanceIntention;
            if (filters.venueAssignment) criteria.venueAssignment = filters.venueAssignment;
            if (filters.excludeForums) criteria.excludeForums = filters.excludeForums;
            if (filters.includeForums) criteria.includeForums = filters.includeForums;
            if (filters.specificTimePreference) criteria.specificTimePreference = filters.specificTimePreference;

            // Contact method filter
            if (filters.hasEmail && !filters.hasMobile) {
                criteria.contactInfo = 'emailOnly';
            } else if (!filters.hasEmail && filters.hasMobile) {
                criteria.contactInfo = 'mobileOnly';
            }

            const response = await api.post('/admin/email/preview-advanced', { criteria });

            if (response.data.status === 'success') {
                const members = response.data.data.members || [];
                setRecipients(members);

                // Auto-select all recipients
                const allIds = new Set<string>(members.map((m: any) => (m.id?.toString() || m.email) as string));
                setSelectedRecipients(allIds);

                toast.success(`Found ${members.length} recipients`);
            }
        } catch (error) {
            toast.error('Failed to load recipients');
            setPreviewMode(false);
        }
    };

    const handleSendTicket = async () => {
        if (selectedRecipients.size !== 1) {
            toast.error('Please select exactly one member to send ticket');
            return;
        }

        setSending(true);
        try {
            const selectedMemberId = Array.from(selectedRecipients)[0];
            const selectedMember = recipients.find(r => (r.id?.toString() || r.email) === selectedMemberId);

            if (!selectedMember) {
                toast.error('Selected member not found');
                return;
            }

            // Generate and send ticket directly, regardless of attendance confirmation status
            const response = await api.post(`/admin/ticket-emails/member/${selectedMember.id}/generate-and-send`);

            if (response.data.status === 'success') {
                toast.success(`BMM ticket sent to ${selectedMember.name}`);
                // Clear selection
                setSelectedRecipients(new Set());
            } else {
                toast.error('Failed to send ticket');
            }
        } catch (error: any) {
            console.error('Failed to send ticket:', error);
            toast.error(error.response?.data?.message || 'Failed to send ticket');
        } finally {
            setSending(false);
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
                eventId: selectedEvent.id,
                criteria: {
                    memberIds: selectedMemberIds
                },
                subject,
                content,
                emailType: 'BMM_CUSTOM',
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
            setTimeout(() => {
                setSendingProgress(null);
                setSending(false);
            }, 2000);
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
        const allIds = new Set(recipients.map(r => r.id?.toString() || r.email));
        setSelectedRecipients(allIds);
    };

    const selectNone = () => {
        setSelectedRecipients(new Set());
    };

    if (loading) {
        return (
            <Layout>
                <div className="flex justify-center items-center h-64">
                    <div className="text-gray-500">Loading...</div>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="container mx-auto px-4 py-8">
                <div className="mb-8">
                    <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
                        Email Broadcast
                    </h1>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Left Panel - Filters */}
                    <div className="lg:col-span-1">
                        <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
                            <h2 className="text-lg font-semibold mb-4">Filter Conditions</h2>

                            {/* Search Section */}
                            <div className="mb-6 p-4 bg-blue-50 dark:bg-blue-900 rounded-lg">
                                <h3 className="text-sm font-medium mb-3 text-blue-800 dark:text-blue-200">🔍 Search Specific Members</h3>
                                <div className="space-y-3">
                                    <div>
                                        <label className="block text-xs font-medium mb-1">Search by Name</label>
                                        <input
                                            type="text"
                                            value={filters.searchName}
                                            onChange={(e) => setFilters({...filters, searchName: e.target.value})}
                                            placeholder="Enter name..."
                                            className="w-full border rounded px-2 py-1 text-xs"
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-xs font-medium mb-1">Search by Email</label>
                                        <input
                                            type="text"
                                            value={filters.searchEmail}
                                            onChange={(e) => setFilters({...filters, searchEmail: e.target.value})}
                                            placeholder="Enter email..."
                                            className="w-full border rounded px-2 py-1 text-xs"
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-xs font-medium mb-1">Search by Membership #</label>
                                        <input
                                            type="text"
                                            value={filters.searchMembershipNumber}
                                            onChange={(e) => setFilters({...filters, searchMembershipNumber: e.target.value})}
                                            placeholder="Enter membership number..."
                                            className="w-full border rounded px-2 py-1 text-xs"
                                        />
                                    </div>
                                </div>
                                <p className="text-xs text-blue-600 dark:text-blue-300 mt-2">
                                    💡 Tip: Use search to find specific members for testing
                                </p>
                                <p className="text-xs text-green-600 dark:text-green-300 mt-1">
                                    🎫 For BMM: Search by membership number + Preview + Select member → Send BMM Ticket
                                </p>
                            </div>

                            {/* Event Selection */}
                            <div className="mb-4">
                                <label className="block text-sm font-medium mb-2">Select Event</label>
                                <select
                                    value={selectedEvent?.id || ''}
                                    onChange={(e) => {
                                        const event = events.find(ev => ev.id === parseInt(e.target.value));
                                        setSelectedEvent(event);
                                    }}
                                    className="w-full border rounded px-3 py-2"
                                >
                                    <option value="">Select an event...</option>
                                    {events.map(event => (
                                        <option key={event.id} value={event.id}>
                                            {event.name} ({event.eventType})
                                        </option>
                                    ))}
                                </select>
                            </div>

                            {/* Region Filter */}
                            <div className="mb-4">
                                <label className="block text-sm font-medium mb-2">Region</label>
                                <select
                                    value={filters.region}
                                    onChange={(e) => setFilters({...filters, region: e.target.value})}
                                    className="w-full border rounded px-3 py-2"
                                >
                                    <option value="">All Regions</option>
                                    {filterOptions.regions.map(region => (
                                        <option key={region} value={region}>{region}</option>
                                    ))}
                                </select>
                            </div>

                            {/* Industry Filter */}
                            <div className="mb-4">
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
                            <div className="mb-4">
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
                            <div className="mb-4">
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

                            {/* BMM Stage Filter - Only show for BMM events */}
                            {selectedEvent?.eventType === 'BMM_VOTING' && (
                                <>
                                    <div className="mb-4">
                                        <label className="block text-sm font-medium mb-2">BMM Stage</label>
                                        <select
                                            value={filters.bmmStage}
                                            onChange={(e) => setFilters({...filters, bmmStage: e.target.value})}
                                            className="w-full border rounded px-3 py-2"
                                        >
                                            <option value="">All Stages</option>
                                            <option value="INVITED">Invited (Not Started)</option>
                                            <option value="PREFERENCE_SUBMITTED">Preference Submitted</option>
                                            <option value="VENUE_ASSIGNED">Venue Assigned</option>
                                            <option value="ATTENDANCE_CONFIRMED">Attendance Confirmed</option>
                                            <option value="NOT_ATTENDING">Not Attending</option>
                                            <option value="TICKET_ISSUED">Ticket Issued</option>
                                            <option value="CHECKED_IN">Checked In</option>
                                        </select>
                                    </div>

                                    <div className="mb-4">
                                        <label className="block text-sm font-medium mb-2">Preference Status</label>
                                        <select
                                            value={filters.preferenceStatus}
                                            onChange={(e) => setFilters({...filters, preferenceStatus: e.target.value})}
                                            className="w-full border rounded px-3 py-2"
                                        >
                                            <option value="">All Members</option>
                                            <option value="submitted">Preferences Submitted</option>
                                            <option value="submitted_attending">Preferences Submitted - Attending</option>
                                            <option value="not_submitted">Preferences Not Submitted</option>
                                            <option value="exclude_not_attending">Exclude Not Attending (Stage 2 Invites)</option>
                                        </select>
                                    </div>

                                    <div className="mb-4">
                                        <label className="block text-sm font-medium mb-2">Attendance Intention</label>
                                        <select
                                            value={filters.attendanceIntention}
                                            onChange={(e) => setFilters({...filters, attendanceIntention: e.target.value})}
                                            className="w-full border rounded px-3 py-2"
                                        >
                                            <option value="">All Intentions</option>
                                            <option value="intend_yes">Intends to Attend</option>
                                            <option value="intend_no">Unlikely to Attend</option>
                                            <option value="not_specified">Not Specified</option>
                                        </select>
                                    </div>

                                    <div className="mb-4">
                                        <label className="block text-sm font-medium mb-2">Venue Assignment</label>
                                        <select
                                            value={filters.venueAssignment}
                                            onChange={(e) => setFilters({...filters, venueAssignment: e.target.value})}
                                            className="w-full border rounded px-3 py-2"
                                        >
                                            <option value="">All Members</option>
                                            <option value="assigned">Venue Assigned</option>
                                            <option value="not_assigned">Venue Not Assigned</option>
                                        </select>
                                    </div>
                                    <div className="mb-4">
                                        <label className="block text-sm font-medium mb-2">Forum Filter</label>
                                        <select
                                            value={filters.includeForums === 'Greymouth' ? 'Greymouth_only' : filters.excludeForums === 'Greymouth' ? 'Greymouth_exclude' : ''}
                                            onChange={(e) => {
                                                const value = e.target.value;
                                                if (value === 'Greymouth_only') {
                                                    setFilters({...filters, includeForums: 'Greymouth', excludeForums: ''});
                                                } else if (value === 'Greymouth_exclude') {
                                                    setFilters({...filters, includeForums: '', excludeForums: 'Greymouth'});
                                                } else {
                                                    setFilters({...filters, includeForums: '', excludeForums: ''});
                                                }
                                            }}
                                            className="w-full border rounded px-3 py-2"
                                        >
                                            <option value="">All Forums</option>
                                            <option value="Greymouth_only">Only Greymouth</option>
                                            <option value="Greymouth_exclude">Exclude Greymouth</option>
                                        </select>
                                        <p className="text-xs text-gray-500 mt-1">
                                            Note: Greymouth venue has been cancelled
                                        </p>
                                    </div>
                                    <div className="mb-4">
                                        <label className="block text-sm font-medium mb-2">Time Preference Filter</label>
                                        <select
                                            value={filters.specificTimePreference}
                                            onChange={(e) => setFilters({...filters, specificTimePreference: e.target.value})}
                                            className="w-full border rounded px-3 py-2"
                                        >
                                            <option value="">All Time Preferences</option>
                                            <option value="morning">Morning (10:30 AM)</option>
                                            <option value="lunchtime">Lunchtime (12:30 PM)</option>
                                            <option value="afternoon">Afternoon (2:30 PM)</option>
                                            <option value="after work">After Work</option>
                                            <option value="night shift">Night Shift</option>
                                        </select>
                                    </div>
                                </>
                            )}

                            {/* Contact Method */}
                            <div className="mb-6">
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
                                        <span>Has Mobile (SMS)</span>
                                    </label>
                                </div>
                            </div>

                            <button
                                onClick={handlePreview}
                                disabled={!selectedEvent}
                                className="w-full bg-blue-600 text-white py-2 rounded hover:bg-blue-700 disabled:bg-gray-400"
                            >
                                Preview Recipients
                            </button>
                        </div>
                    </div>

                    {/* Right Panel - Email Content */}
                    <div className="lg:col-span-2">
                        <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
                            <h2 className="text-lg font-semibold mb-4">Email Content</h2>

                            {/* Template Selection */}
                            <div className="mb-4">
                                <label className="block text-sm font-medium mb-2">Email Template</label>
                                <select
                                    value={selectedTemplate}
                                    onChange={(e) => handleTemplateSelect(e.target.value)}
                                    className="w-full border rounded px-3 py-2"
                                >
                                    <option value="">Select a template...</option>
                                    {Object.entries(templates).map(([key, template]) => (
                                        <option key={key} value={key}>{template.name}</option>
                                    ))}
                                </select>
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

                            {/* Subject */}
                            <div className="mb-4">
                                <label className="block text-sm font-medium mb-2">
                                    Email Subject
                                    <button
                                        onClick={() => {
                                            setCursorPosition('subject');
                                            setShowVariableHelper(true);
                                        }}
                                        className="ml-2 text-blue-600 hover:text-blue-800 text-xs"
                                    >
                                        + Add Variable
                                    </button>
                                </label>
                                <input
                                    type="text"
                                    value={subject}
                                    onChange={(e) => setSubject(e.target.value)}
                                    onFocus={() => setCursorPosition('subject')}
                                    placeholder="Enter email subject..."
                                    className="w-full border rounded px-3 py-2"
                                />
                            </div>

                            {/* Content */}
                            <div className="mb-4">
                                <label className="block text-sm font-medium mb-2">
                                    Email Content
                                    <button
                                        onClick={() => {
                                            setCursorPosition('content');
                                            setShowVariableHelper(true);
                                        }}
                                        className="ml-2 text-blue-600 hover:text-blue-800 text-xs"
                                    >
                                        + Add Variable
                                    </button>
                                </label>
                                <textarea
                                    value={content}
                                    onChange={(e) => setContent(e.target.value)}
                                    onFocus={() => setCursorPosition('content')}
                                    placeholder="Enter email content..."
                                    rows={10}
                                    className="w-full border rounded px-3 py-2"
                                />
                            </div>

                            {/* Variable Helper */}
                            {showVariableHelper && (
                                <div className="mb-4 p-4 bg-blue-50 dark:bg-blue-900 rounded">
                                    <div className="flex justify-between items-center mb-2">
                                        <h3 className="font-medium">Available Variables</h3>
                                        <button
                                            onClick={() => setShowVariableHelper(false)}
                                            className="text-gray-500 hover:text-gray-700"
                                        >
                                            ✕
                                        </button>
                                    </div>
                                    <div className="grid grid-cols-2 gap-2">
                                        {variables.map(v => (
                                            <button
                                                key={v.key}
                                                onClick={() => insertVariable(v.key)}
                                                className="text-left p-2 hover:bg-blue-100 dark:hover:bg-blue-800 rounded"
                                            >
                                                <div className="font-mono text-sm">{`{{${v.key}}}`}</div>
                                                <div className="text-xs text-gray-600 dark:text-gray-400">{v.desc}</div>
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {/* Recipients count */}
                            {recipients.length > 0 && (
                                <div className="text-sm text-gray-600 dark:text-gray-400">
                                    Current filter results: {selectedRecipients.size} of {recipients.length} members selected
                                </div>
                            )}

                            {/* Progress Bar */}
                            {sendingProgress && (
                                <div className="mt-6 p-4 bg-blue-50 dark:bg-blue-900 rounded-lg">
                                    <div className="flex justify-between items-center mb-2">
                                        <h4 className="font-medium text-blue-800 dark:text-blue-200">
                                            📧 Email Sending Progress
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
                                            🚀 Using Mailjet for enhanced delivery
                                        </div>
                                    )}
                                    {emailProvider === 'STRATUM' && (
                                        <div className="mt-2 text-xs text-blue-600 dark:text-blue-400">
                                            📨 Using Stratum for delivery
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* Send button */}
                            {previewMode && recipients.length > 0 && (
                                <div className="mt-6 flex justify-end space-x-4">
                                    {/* Send Ticket Button - Only show for single BMM member selection */}
                                    {selectedEvent?.eventType === 'BMM_VOTING' && selectedRecipients.size === 1 && (
                                        <button
                                            onClick={handleSendTicket}
                                            disabled={sending}
                                            className="bg-blue-600 text-white px-6 py-2 rounded hover:bg-blue-700 disabled:bg-gray-400 transition-colors"
                                        >
                                            {sending ? 'Sending...' : 'Send BMM Ticket'}
                                        </button>
                                    )}

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
                            )}
                        </div>

                        {/* Recipients Preview */}
                        {previewMode && recipients.length > 0 && (
                            <div className="mt-6 bg-white dark:bg-gray-800 rounded-lg shadow p-6">
                                <div className="flex justify-between items-center mb-4">
                                    <h3 className="text-lg font-semibold">Recipients Preview</h3>
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
                                        <thead className="bg-gray-50 dark:bg-gray-700">
                                        <tr>
                                            <th className="px-4 py-2 text-left">
                                                <input
                                                    type="checkbox"
                                                    checked={selectedRecipients.size === recipients.length}
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
                                            {selectedEvent?.eventType === 'BMM_VOTING' && (
                                                <>
                                                    <th className="px-4 py-2 text-left">BMM Stage</th>
                                                    <th className="px-4 py-2 text-left">Venue</th>
                                                </>
                                            )}
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {recipients.map((member) => {
                                            const id = member.id?.toString() || member.email;
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
                                                    {selectedEvent?.eventType === 'BMM_VOTING' && (
                                                        <>
                                                            <td className="px-4 py-2">
                                                                <span className={`text-xs px-2 py-1 rounded ${
                                                                    member.bmmRegistrationStage === 'ATTENDANCE_CONFIRMED' ? 'bg-green-100 text-green-800' :
                                                                        member.bmmRegistrationStage === 'TICKET_ISSUED' ? 'bg-blue-100 text-blue-800' :
                                                                            member.bmmRegistrationStage === 'VENUE_ASSIGNED' ? 'bg-yellow-100 text-yellow-800' :
                                                                                'bg-gray-100 text-gray-800'
                                                                }`}>
                                                                    {member.bmmRegistrationStage || 'Not Started'}
                                                                </span>
                                                            </td>
                                                            <td className="px-4 py-2 text-sm">{member.assignedVenue || '-'}</td>
                                                        </>
                                                    )}
                                                </tr>
                                            );
                                        })}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </Layout>
    );
}