import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/common/Layout';
import api from '@/services/api';
import { toast } from 'react-toastify';

interface EventMember {
    id: number;
    membershipNumber: string;
    name: string;
    primaryEmail: string;
    mobile?: string;
    regionDesc: string;
    forumDesc: string;
    workplace: string;
    isAttending: boolean;
    bmmRegistrationStage: string;
    ticketEmailSent: boolean;
    assignedVenue?: string;
    ticketToken?: string;
    preferredAttending?: boolean;
}

interface WorksiteGroup {
    worksite: string;
    memberCount: number;
    ticketsSent: number;
    pendingTickets: number;
    smsOnlyCount: number;
    members: EventMember[];
}

export default function BMMBulkTickets() {
    const router = useRouter();
    const [loading, setLoading] = useState(false);
    const [worksiteGroups, setWorksiteGroups] = useState<WorksiteGroup[]>([]);
    const [selectedWorksite, setSelectedWorksite] = useState<string>('');
    const [selectedMembers, setSelectedMembers] = useState<EventMember[]>([]);
    const [sendingTickets, setSendingTickets] = useState(false);
    const [eventId, setEventId] = useState<number | null>(null);
    const [emailProvider, setEmailProvider] = useState<'STRATUM' | 'MAILJET'>('STRATUM');
    const [membersWithMobile, setMembersWithMobile] = useState<EventMember[]>([]);

    useEffect(() => {
        loadBMMEvent();
    }, []);

    const loadBMMEvent = async () => {
        try {
            setLoading(true);
            // Get BMM event info
            const eventsResponse = await api.get('/admin/ticket-emails/overview');
            const bmmEvent = eventsResponse.data.data.find((event: any) =>
                event.eventName.toLowerCase().includes('bmm') ||
                event.eventName.toLowerCase().includes('biennial')
            );

            if (bmmEvent) {
                setEventId(bmmEvent.eventId);
                await loadWorksiteData(bmmEvent.eventId);
            } else {
                toast.error('BMM event not found');
            }
        } catch (error) {
            console.error('Failed to load BMM event:', error);
            toast.error('Failed to load BMM event data');
        } finally {
            setLoading(false);
        }
    };

    const loadWorksiteData = async (eventId: number) => {
        try {
            // Get all BMM members
            const response = await api.get(`/admin/events/${eventId}/members`);
            const members: EventMember[] = response.data.data;

            // Filter out members who selected "not attend" in preference stage
            const eligibleMembers = members.filter(member => {
                // Include if preferredAttending is null, undefined, or true
                // Exclude only if explicitly false
                return member.preferredAttending !== false;
            });

            // Group by worksite
            const groupedByWorksite = eligibleMembers.reduce((groups: Record<string, EventMember[]>, member) => {
                // Normalize worksite value
                const worksite = member.workplace && member.workplace.trim() !== '' ? member.workplace : 'No Worksite';
                if (!groups[worksite]) {
                    groups[worksite] = [];
                }
                groups[worksite].push(member);
                return groups;
            }, {});

            // Create worksite group objects
            const worksiteGroups: WorksiteGroup[] = Object.entries(groupedByWorksite).map(([worksite, members]) => {
                const ticketsSent = members.filter(m => m.ticketEmailSent).length;
                const pendingTickets = members.length - ticketsSent;

                // Count SMS only members (no email OR temp email)
                const smsOnlyCount = members.filter(m =>
                    !m.primaryEmail ||
                    m.primaryEmail.trim() === '' ||
                    m.primaryEmail.includes('@temp-email.etu.nz')
                ).length;

                return {
                    worksite,
                    memberCount: members.length,
                    ticketsSent,
                    pendingTickets,
                    smsOnlyCount,
                    members
                };
            }).sort((a, b) => b.memberCount - a.memberCount);

            setWorksiteGroups(worksiteGroups);
        } catch (error) {
            console.error('Failed to load worksite data:', error);
            toast.error('Failed to load member data');
        }
    };

    const handleWorksiteSelect = (worksite: string) => {
        setSelectedWorksite(worksite);
        const group = worksiteGroups.find(g => g.worksite === worksite);
        if (group) {
            // Only show members who haven't received tickets yet
            let pendingMembers = group.members.filter(m => !m.ticketEmailSent);

            // Apply worksite filtering logic properly
            if (worksite && worksite !== 'No Worksite') {
                pendingMembers = pendingMembers.filter(m => m.workplace === worksite);
            } else if (worksite === 'No Worksite') {
                pendingMembers = pendingMembers.filter(m => !m.workplace || m.workplace === '');
            }

            setSelectedMembers(pendingMembers);

            // Update members with mobile for export
            const withMobile = pendingMembers.filter(m =>
                (!m.primaryEmail || m.primaryEmail.trim() === '' || m.primaryEmail.includes('@temp-email.etu.nz')) &&
                m.mobile && m.mobile.trim() !== ''
            );
            setMembersWithMobile(withMobile);
        }
    };

    const sendBulkTickets = async () => {
        if (!selectedMembers.length) {
            toast.error('No members selected to send tickets');
            return;
        }

        const confirmMessage = `Send tickets to ${selectedMembers.length} members from ${selectedWorksite}?`;
        if (!confirm(confirmMessage)) {
            return;
        }

        try {
            setSendingTickets(true);

            // Since all members in same worksite are from same forum, get forum from first member
            const forumDesc = selectedMembers[0]?.forumDesc || '';

            // Prepare email content with ticket template (simple format for Stratum)
            const subject = 'Your BMM 2025 Event Ticket - Action Required';
            const content = `Kia ora {{firstName}},

Your ticket for the 2025 E tu Biennial Membership Meeting has been generated.

EVENT DETAILS:
Event: 2025 E tu Biennial Membership Meeting
Venue: {{venue}}
Address: {{venueAddress}}
Date: {{eventDate}}
Session Time: {{sessionTime}}
Travel Time Span: {{timeSpan}}
Your Region: {{region}}
Forum: ${forumDesc}
Workplace: ${selectedWorksite}

YOUR TICKET:
View your ticket here: {{ticketUrl}}

IMPORTANT - SAVE YOUR TICKET NOW:
1. Click the link above to view your ticket
2. Save the image to your phone gallery
3. Print a physical copy as backup
4. Copy and save the link
5. Add the event to your calendar

CHECK-IN INSTRUCTIONS:
- Arrive 15 minutes before the meeting time
- Present your ticket at the venue registration desk
- Show the QR code on your phone or printed ticket
- Staff will scan your QR code to confirm attendance
- Bring valid ID if requested

HELPFUL TIPS:
- Take a screenshot of your ticket now
- The QR code is your check-in pass
- This ticket is valid only for your assigned venue and session

If you have any questions or need to change your attendance, please reply to this email.

Nga mihi nui,
E tu Events Team

P.S. If you cannot attend, please let us know as soon as possible.`;

            // Prepare member IDs and ensure tickets are generated
            const memberIds = selectedMembers.map(m => m.id);

            // First, generate tickets for all members
            for (const member of selectedMembers) {
                try {
                    await api.post(`/admin/ticket-emails/member/${member.id}/generate-and-send`);
                } catch (error) {
                    console.log(`Ticket generation for ${member.name}:`, error);
                }
            }

            // Separate members with email and without email (SMS only)
            // SMS only: no email OR empty email OR temp email (@temp-email.etu.nz)
            const membersWithEmail = selectedMembers.filter(m =>
                m.primaryEmail &&
                m.primaryEmail.trim() !== '' &&
                !m.primaryEmail.includes('@temp-email.etu.nz')
            );
            const membersNoEmail = selectedMembers.filter(m =>
                !m.primaryEmail ||
                m.primaryEmail.trim() === '' ||
                m.primaryEmail.includes('@temp-email.etu.nz')
            );

            if (membersNoEmail.length > 0) {
                toast.info(`${membersNoEmail.length} members have no email - tickets generated for SMS export`);
                // Store members with mobile for export
                const withMobile = membersNoEmail.filter(m => m.mobile && m.mobile !== '');
                setMembersWithMobile(withMobile);
            }

            // Send emails only to members with email addresses
            if (membersWithEmail.length > 0) {
                const emailMemberIds = membersWithEmail.map(m => m.id);
                const emailRequest = {
                    eventId: eventId,
                    subject: subject,
                    content: content,
                    provider: emailProvider,
                    criteria: {
                        memberIds: emailMemberIds
                    }
                };

                const response = await api.post('/admin/email/send-advanced', emailRequest);

                if (response.data.status === 'success') {
                    const emailResult = response.data;
                    toast.success(`Successfully queued ${emailResult.data?.successCount || membersWithEmail.length} ticket emails`);
                } else {
                    toast.error('Failed to send ticket emails');
                }
            }

            // Reload data
            if (eventId) {
                await loadWorksiteData(eventId);
                handleWorksiteSelect(selectedWorksite); // Refresh selected members
            }

        } catch (error) {
            console.error('Bulk ticket sending failed:', error);
            toast.error('Failed to send tickets');
        } finally {
            setSendingTickets(false);
        }
    };

    const exportSMSData = () => {
        if (membersWithMobile.length === 0) {
            toast.error('No members with mobile numbers to export');
            return;
        }

        // Create CSV data for ClickSend
        const csvHeader = 'To,From,Message\n';
        const csvRows = membersWithMobile.map(member => {
            const ticketUrl = `https://events.etu.nz/ticket?token=${member.ticketToken}`;
            const message = `Kia ora ${member.name}, your BMM 2025 ticket is ready! View and save your ticket: ${ticketUrl} - E tu Events`;
            return `${member.mobile},E tu,"${message}"`;
        }).join('\n');

        const csvContent = csvHeader + csvRows;
        const blob = new Blob([csvContent], { type: 'text/csv' });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `bmm-sms-tickets-${selectedWorksite}-${new Date().toISOString().split('T')[0]}.csv`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);

        toast.success(`Exported ${membersWithMobile.length} SMS records for ClickSend`);
    };

    return (
        <Layout>
            <div className="space-y-6">
                <div className="flex items-center justify-between">
                    <h1 className="text-3xl font-bold text-gray-900">BMM Bulk Ticket Sending</h1>
                    <div className="flex gap-2">
                        {membersWithMobile.length > 0 && (
                            <button
                                onClick={exportSMSData}
                                className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg"
                            >
                                Export SMS Data ({membersWithMobile.length})
                            </button>
                        )}
                        <button
                            onClick={() => router.push('/admin/ticket-management')}
                            className="bg-gray-500 hover:bg-gray-600 text-white px-4 py-2 rounded-lg"
                        >
                            Back to Ticket Management
                        </button>
                    </div>
                </div>

                {/* Worksite Overview */}
                <div className="bg-white rounded-lg shadow-md p-6">
                    <h2 className="text-xl font-semibold mb-4">Worksite Groups</h2>
                    <div className="overflow-x-auto">
                        <table className="min-w-full table-auto">
                            <thead>
                            <tr className="bg-gray-50">
                                <th className="px-4 py-2 text-left">Worksite</th>
                                <th className="px-4 py-2 text-center">Total Members</th>
                                <th className="px-4 py-2 text-center">Tickets Sent</th>
                                <th className="px-4 py-2 text-center">Pending</th>
                                <th className="px-4 py-2 text-center">SMS Only</th>
                                <th className="px-4 py-2 text-center">Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            {worksiteGroups.map((group) => {
                                // Get forum from first member in the group
                                const forum = group.members[0]?.forumDesc || 'N/A';
                                return (
                                    <tr key={group.worksite} className={`border-t hover:bg-gray-50 ${selectedWorksite === group.worksite ? 'bg-blue-50' : ''}`}>
                                        <td className="px-4 py-2">
                                            <div className="font-medium">{group.worksite}</div>
                                            <div className="text-xs text-gray-500">Forum: {forum}</div>
                                        </td>
                                        <td className="px-4 py-2 text-center">{group.memberCount}</td>
                                        <td className="px-4 py-2 text-center">
                                            <span className="text-green-600">{group.ticketsSent}</span>
                                        </td>
                                        <td className="px-4 py-2 text-center">
                                            <span className={group.pendingTickets > 0 ? 'text-red-600 font-medium' : 'text-gray-400'}>
                                                {group.pendingTickets}
                                            </span>
                                        </td>
                                        <td className="px-4 py-2 text-center">
                                            <span className={group.smsOnlyCount > 0 ? 'text-orange-600 font-medium' : 'text-gray-400'}>
                                                {group.smsOnlyCount}
                                            </span>
                                        </td>
                                        <td className="px-4 py-2 text-center">
                                            <button
                                                onClick={() => handleWorksiteSelect(group.worksite)}
                                                disabled={group.pendingTickets === 0}
                                                className={`px-3 py-1 rounded text-sm ${
                                                    group.pendingTickets > 0
                                                        ? 'bg-blue-500 hover:bg-blue-600 text-white'
                                                        : 'bg-gray-300 text-gray-500 cursor-not-allowed'
                                                }`}
                                            >
                                                {selectedWorksite === group.worksite ? 'Selected' : 'Select'}
                                            </button>
                                        </td>
                                    </tr>
                                );
                            })}
                            </tbody>
                        </table>
                    </div>
                </div>

                {/* Selected Members */}
                {selectedWorksite && selectedMembers.length > 0 && (
                    <div className="bg-white rounded-lg shadow-md p-6">
                        <div className="flex items-center justify-between mb-4">
                            <div>
                                <h2 className="text-xl font-semibold">
                                    {selectedWorksite} - {selectedMembers.length} Members Pending Tickets
                                </h2>
                                <p className="text-sm text-gray-600 mt-1">
                                    Forum: {selectedMembers[0]?.forumDesc || 'N/A'}
                                </p>
                                {membersWithMobile.length > 0 && (
                                    <p className="text-sm text-orange-600">
                                        {membersWithMobile.length} SMS only members (no email or temp email)
                                    </p>
                                )}
                            </div>
                            <div className="flex items-center gap-4">
                                <select
                                    value={emailProvider}
                                    onChange={(e) => setEmailProvider(e.target.value as 'STRATUM' | 'MAILJET')}
                                    className="border rounded px-3 py-2 text-sm"
                                >
                                    <option value="STRATUM">Stratum (Primary)</option>
                                    <option value="MAILJET">Mailjet (Backup)</option>
                                </select>
                                <button
                                    onClick={sendBulkTickets}
                                    disabled={sendingTickets}
                                    className="bg-green-500 hover:bg-green-600 text-white px-4 py-2 rounded-lg disabled:opacity-50"
                                >
                                    {sendingTickets ? 'Sending...' : `Send ${selectedMembers.length} Tickets`}
                                </button>
                            </div>
                        </div>

                        <div className="overflow-x-auto">
                            <table className="min-w-full table-auto">
                                <thead>
                                <tr className="bg-gray-50">
                                    <th className="px-4 py-2 text-left">Member ID</th>
                                    <th className="px-4 py-2 text-left">Name</th>
                                    <th className="px-4 py-2 text-left">Email</th>
                                    <th className="px-4 py-2 text-left">Mobile</th>
                                    <th className="px-4 py-2 text-left">Region</th>
                                    <th className="px-4 py-2 text-left">Forum</th>
                                    <th className="px-4 py-2 text-left">Registration Stage</th>
                                </tr>
                                </thead>
                                <tbody>
                                {selectedMembers.map((member) => (
                                    <tr key={member.id} className="border-t hover:bg-gray-50">
                                        <td className="px-4 py-2 font-mono">{member.membershipNumber}</td>
                                        <td className="px-4 py-2">{member.name}</td>
                                        <td className="px-4 py-2">
                                            {member.primaryEmail ? (
                                                <span>{member.primaryEmail}</span>
                                            ) : (
                                                <span className="text-red-500 font-medium">No email</span>
                                            )}
                                        </td>
                                        <td className="px-4 py-2">{member.mobile || '-'}</td>
                                        <td className="px-4 py-2">{member.regionDesc || '-'}</td>
                                        <td className="px-4 py-2">{member.forumDesc || '-'}</td>
                                        <td className="px-4 py-2">
                                                <span className={`px-2 py-1 rounded-full text-xs ${
                                                    member.bmmRegistrationStage === 'ATTENDANCE_CONFIRMED'
                                                        ? 'bg-green-100 text-green-800'
                                                        : 'bg-gray-100 text-gray-800'
                                                }`}>
                                                    {member.bmmRegistrationStage || 'Not Registered'}
                                                </span>
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}

                {/* Instructions */}
                <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                    <h3 className="text-lg font-semibold text-blue-800 mb-2">Bulk Ticket Sending Instructions</h3>
                    <ul className="text-blue-700 space-y-1 list-disc list-inside">
                        <li>Select a worksite from the table above to view members</li>
                        <li>Tickets will be generated for ALL members (even without email)</li>
                        <li>Members with valid email will receive ticket emails automatically</li>
                        <li>Members without email (or with @temp-email.etu.nz) will have tickets generated for SMS export</li>
                        <li>Use "Export SMS Data" button to download CSV for ClickSend</li>
                        <li>Members can check in with QR codes even without Stage 1/2 registration</li>
                    </ul>
                </div>
            </div>
        </Layout>
    );
}