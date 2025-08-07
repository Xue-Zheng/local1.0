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

interface SMSTemplate {
    name: string;
    content: string;
}

export default function SMSBroadcastPage() {
    const router = useRouter();
    const [loading, setLoading] = useState(true);
    const [events, setEvents] = useState<any[]>([]);
    const [selectedEvent, setSelectedEvent] = useState<any>(null);

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
        hasEmail: false,
        hasMobile: true,
        smsOnly: false,
        // BMM specific filters
        bmmStage: '',
        preferenceStatus: '',
        attendanceIntention: '',
        venueAssignment: ''
    });

    // SMS content
    const [message, setMessage] = useState('');
    const [selectedTemplate, setSelectedTemplate] = useState('');
    const [charCount, setCharCount] = useState(0);
    const [smsCount, setSmsCount] = useState(1);

    // Pre-selected members from other pages
    const [preSelectedMembers, setPreSelectedMembers] = useState<any[]>([]);

    // Preview and sending
    const [previewMode, setPreviewMode] = useState(false);
    const [recipients, setRecipients] = useState<any[]>([]);
    const [selectedRecipients, setSelectedRecipients] = useState<Set<string>>(new Set());
    const [sending, setSending] = useState(false);

    // Variable insertion helper
    const [showVariableHelper, setShowVariableHelper] = useState(false);

    // Available variables for SMS
    const variables = [
        { key: 'firstName', desc: 'First name' },
        { key: 'name', desc: 'Full name' },
        { key: 'membershipNumber', desc: 'Membership number' },
        { key: 'region', desc: 'Region' },
        { key: 'registrationLink', desc: 'Short registration link' },
        { key: 'verificationCode', desc: 'Verification code' }
    ];

    // SMS templates
    const templates: { [key: string]: SMSTemplate } = {
        bmm_invitation: {
            name: 'BMM Invitation SMS',
            content: `Hi {{firstName}}, Register for BMM 2025: {{registrationLink}} - E tū`
        },
        bmm_reminder: {
            name: 'BMM Reminder SMS',
            content: `{{firstName}}, don't forget to register for BMM 2025. Visit: {{registrationLink}}`
        },
        bmm_confirmation: {
            name: 'BMM Confirmation SMS',
            content: `Hi {{firstName}}, your BMM meeting is confirmed. Check your email for details.`
        },
        custom: {
            name: 'Custom SMS',
            content: ''
        }
    };

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }

        // Check for pre-selected members from BMM Management
        const savedMembers = localStorage.getItem('preSelectedMembers');
        if (savedMembers) {
            const data = JSON.parse(savedMembers);
            // Handle both formats: direct array or object with targetMembers
            const members = Array.isArray(data) ? data : (data.targetMembers || []);
            setPreSelectedMembers(members);
            // Convert to recipients format
            setRecipients(members);
            const allIds = new Set<string>(members.map((m: any) => m.id?.toString() || m.telephoneMobile));
            setSelectedRecipients(allIds);
            setPreviewMode(true);

            // Clear from localStorage after loading
            localStorage.removeItem('preSelectedMembers');

            toast.info(`Loaded ${members.length} pre-selected members`);
        }

        fetchEvents();
        fetchFilterOptions();
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
        // If an industry is selected, filter sub-industries
        return filterOptions.subIndustries;
    };

    const handleTemplateSelect = (templateKey: string) => {
        setSelectedTemplate(templateKey);
        if (templateKey !== 'custom') {
            const template = templates[templateKey];
            setMessage(template.content);
            updateCharCount(template.content);
        } else {
            setMessage('');
            updateCharCount('');
        }
    };

    const updateCharCount = (text: string) => {
        const length = text.length;
        setCharCount(length);

        // Calculate SMS count
        if (length <= 160) {
            setSmsCount(1);
        } else {
            // For multi-part SMS, each part is 153 chars
            setSmsCount(Math.ceil(length / 153));
        }
    };

    const handleMessageChange = (text: string) => {
        setMessage(text);
        updateCharCount(text);
    };

    const insertVariable = (variable: string) => {
        const varText = `{{${variable}}}`;
        const newMessage = message + varText;
        setMessage(newMessage);
        updateCharCount(newMessage);
        setShowVariableHelper(false);
    };

    const handlePreview = async () => {
        if (!selectedEvent) {
            toast.error('Please select an event');
            return;
        }

        // Clear pre-selected members if doing a new search
        if (preSelectedMembers.length > 0) {
            setPreSelectedMembers([]);
        }

        setPreviewMode(true);

        try {
            const criteria: any = {
                eventId: selectedEvent.id
            };

            if (filters.region) criteria.region = filters.region;
            if (filters.industry) criteria.siteIndustryDesc = filters.industry;
            if (filters.subIndustry) criteria.siteSubIndustryDesc = filters.subIndustry;
            if (filters.registrationStatus) criteria.registrationStatus = filters.registrationStatus;

            // BMM specific filters
            if (filters.bmmStage) criteria.bmmStage = filters.bmmStage;
            if (filters.preferenceStatus) criteria.preferenceStatus = filters.preferenceStatus;
            if (filters.attendanceIntention) criteria.attendanceIntention = filters.attendanceIntention;
            if (filters.venueAssignment) criteria.venueAssignment = filters.venueAssignment;

            // Contact method filter - for SMS, we want mobile numbers
            if (filters.smsOnly) {
                criteria.contactInfo = 'mobileOnly';  // Only mobile, no email
            } else if (!filters.hasEmail && filters.hasMobile) {
                criteria.contactInfo = 'mobileOnly';
            } else if (filters.hasEmail && filters.hasMobile) {
                criteria.contactInfo = 'both';
            }

            // Use SMS-specific preview endpoint
            const response = await api.post('/admin/sms/preview-advanced', {
                eventId: selectedEvent.id,
                criteria
            });

            if (response.data.status === 'success') {
                const members = response.data.data.members || [];

                // SMS endpoint should already return only SMS-able members
                setRecipients(members);

                // Auto-select all recipients
                const allIds = new Set<string>(members.map((m: any) => m.id?.toString() || m.mobile));
                setSelectedRecipients(allIds);

                toast.success(`Found ${members.length} SMS-only recipients`);
            }
        } catch (error) {
            toast.error('Failed to load recipients');
            setPreviewMode(false);
        }
    };

    const handleSend = async () => {
        if (!message) {
            toast.error('Please enter SMS message');
            return;
        }

        if (selectedRecipients.size === 0) {
            toast.error('Please select at least one recipient');
            return;
        }

        setSending(true);

        try {
            const selectedMemberIds = Array.from(selectedRecipients);

            const response = await api.post('/admin/sms/send-advanced', {
                eventId: selectedEvent.id,
                criteria: {
                    memberIds: selectedMemberIds
                },
                message,
                smsType: 'BMM_CUSTOM'
            });

            if (response.data.status === 'success') {
                toast.success(`SMS queued for ${selectedMemberIds.length} recipients`);
                // Reset form
                setPreviewMode(false);
                setRecipients([]);
                setSelectedRecipients(new Set());
                setMessage('');
                setCharCount(0);
                setSmsCount(1);
            } else {
                toast.error(response.data.message || 'Failed to send SMS');
            }
        } catch (error) {
            toast.error('Failed to send SMS');
        } finally {
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
        const allIds = new Set<string>(recipients.map(r => r.id?.toString() || r.telephoneMobile));
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
                        SMS Broadcast
                    </h1>

                    {preSelectedMembers.length > 0 && (
                        <div className="mt-4 p-4 bg-green-50 dark:bg-green-900 rounded-lg">
                            <p className="text-green-800 dark:text-green-200">
                                ✅ {preSelectedMembers.length} pre-selected members loaded from BMM Management
                            </p>
                        </div>
                    )}
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Left Panel - Filters */}
                    <div className="lg:col-span-1">
                        <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
                            <h2 className="text-lg font-semibold mb-4">Filter Conditions</h2>

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
                                    disabled={preSelectedMembers.length > 0}
                                >
                                    <option value="">Select an event...</option>
                                    {events.map(event => (
                                        <option key={event.id} value={event.id}>
                                            {event.name} ({event.eventType})
                                        </option>
                                    ))}
                                </select>
                            </div>

                            {/* Show filters only if not using pre-selected members */}
                            {preSelectedMembers.length === 0 && (
                                <>
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
                                                    <option value="not_submitted">Preferences Not Submitted</option>
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
                                        </>
                                    )}

                                    {/* Contact Method */}
                                    <div className="mb-6">
                                        <label className="block text-sm font-medium mb-2">Contact Method</label>
                                        <div className="space-y-2">
                                            <label className="flex items-center">
                                                <input
                                                    type="checkbox"
                                                    checked={filters.smsOnly}
                                                    onChange={(e) => setFilters({...filters, smsOnly: e.target.checked, hasEmail: e.target.checked ? false : filters.hasEmail})}
                                                    className="mr-2"
                                                />
                                                <span className="font-medium text-orange-600">SMS Only Members</span>
                                            </label>
                                            <label className="flex items-center">
                                                <input
                                                    type="checkbox"
                                                    checked={filters.hasEmail}
                                                    onChange={(e) => setFilters({...filters, hasEmail: e.target.checked, smsOnly: false})}
                                                    className="mr-2"
                                                    disabled={filters.smsOnly}
                                                />
                                                <span>Also has Email</span>
                                            </label>
                                            <label className="flex items-center">
                                                <input
                                                    type="checkbox"
                                                    checked={filters.hasMobile}
                                                    onChange={(e) => setFilters({...filters, hasMobile: e.target.checked})}
                                                    className="mr-2"
                                                    disabled
                                                />
                                                <span>Has Mobile (Required)</span>
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
                                </>
                            )}

                            {/* SMS Cost Estimate */}
                            {selectedRecipients.size > 0 && (
                                <div className="mt-4 p-4 bg-yellow-50 dark:bg-yellow-900 rounded">
                                    <h3 className="font-medium text-yellow-800 dark:text-yellow-200 mb-2">
                                        SMS Cost Estimate
                                    </h3>
                                    <p className="text-sm text-yellow-700 dark:text-yellow-300">
                                        Recipients: {selectedRecipients.size}<br/>
                                        SMS parts per message: {smsCount}<br/>
                                        Total SMS count: {selectedRecipients.size * smsCount}
                                    </p>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Right Panel - SMS Content */}
                    <div className="lg:col-span-2">
                        <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
                            <h2 className="text-lg font-semibold mb-4">SMS Content</h2>

                            {/* Template Selection */}
                            <div className="mb-4">
                                <label className="block text-sm font-medium mb-2">SMS Template</label>
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

                            {/* Message */}
                            <div className="mb-4">
                                <label className="block text-sm font-medium mb-2">
                                    SMS Message
                                    <span className="ml-2 text-gray-500">
                                        ({charCount} chars, {smsCount} SMS)
                                    </span>
                                    <button
                                        onClick={() => setShowVariableHelper(true)}
                                        className="ml-2 text-blue-600 hover:text-blue-800 text-xs"
                                    >
                                        + Add Variable
                                    </button>
                                </label>
                                <textarea
                                    value={message}
                                    onChange={(e) => handleMessageChange(e.target.value)}
                                    placeholder="Enter SMS message..."
                                    rows={6}
                                    className="w-full border rounded px-3 py-2"
                                />
                                <div className="mt-1">
                                    <div className="flex justify-between text-xs text-gray-500">
                                        <span>Standard SMS: 160 chars</span>
                                        <span>Multi-part SMS: 153 chars each</span>
                                    </div>
                                    {charCount > 160 && (
                                        <p className="text-xs text-orange-600 mt-1">
                                            ⚠️ Message will be sent as {smsCount} separate SMS parts
                                        </p>
                                    )}
                                </div>
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

                            {/* Message Preview */}
                            {message && (
                                <div className="mb-6 p-4 bg-gray-50 dark:bg-gray-700 rounded">
                                    <h3 className="font-medium mb-2">Message Preview</h3>
                                    <div className="bg-white dark:bg-gray-600 p-3 rounded border">
                                        <p className="whitespace-pre-wrap text-sm">{message}</p>
                                    </div>
                                </div>
                            )}

                            {/* Recipients count */}
                            {recipients.length > 0 && (
                                <div className="text-sm text-gray-600 dark:text-gray-400">
                                    Current filter results: {selectedRecipients.size} of {recipients.length} members selected
                                </div>
                            )}

                            {/* Send button */}
                            {previewMode && recipients.length > 0 && (
                                <div className="mt-6 flex justify-end">
                                    <button
                                        onClick={handleSend}
                                        disabled={sending || selectedRecipients.size === 0 || !message}
                                        className="bg-green-600 text-white px-6 py-2 rounded hover:bg-green-700 disabled:bg-gray-400"
                                    >
                                        {sending ? 'Sending...' : `Send SMS to ${selectedRecipients.size} members`}
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
                                            <th className="px-4 py-2 text-left">Mobile</th>
                                            <th className="px-4 py-2 text-left">Region</th>
                                            <th className="px-4 py-2 text-left">Industry</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {recipients.map((member) => {
                                            const id = member.id?.toString() || member.primaryMobile;
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
                                                    <td className="px-4 py-2">{member.primaryMobile}</td>
                                                    <td className="px-4 py-2">{member.regionDesc}</td>
                                                    <td className="px-4 py-2">{member.siteIndustryDesc}</td>
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