import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/common/Layout';
import api from '@/services/api';

interface Event {
    eventId: number;
    eventName: string;
    eventCode: string;
    eventType: string;
    totalAttending: number;
    ticketsSent: number;
    pending: number;
    completionRate: string;
}

interface TicketEmailStats {
    eventId: number;
    eventName: string;
    totalAttendingMembers: number;
    ticketEmailsSent: number;
    pendingTicketEmails: number;
    membersWithoutEmail: number;
    emailDeliveryRate: string;
}

export default function TicketManagement() {
    const router = useRouter();
    const [events, setEvents] = useState<Event[]>([]);
    const [selectedEvent, setSelectedEvent] = useState<number | null>(null);
    const [eventStats, setEventStats] = useState<TicketEmailStats | null>(null);
    const [pendingMembers, setPendingMembers] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    const [sendingEmails, setSendingEmails] = useState(false);

    useEffect(() => {
        loadTicketEmailOverview();
    }, []);

    const loadTicketEmailOverview = async () => {
        try {
            setLoading(true);
            const response = await api.get('/admin/ticket-emails/overview');
            setEvents(response.data.data);
        } catch (error) {
            console.error('Failed to load ticket email overview:', error);
        } finally {
            setLoading(false);
        }
    };

    const loadEventStats = async (eventId: number) => {
        try {
            setLoading(true);
            const [statsResponse, pendingResponse] = await Promise.all([
                api.get(`/admin/ticket-emails/event/${eventId}/stats`),
                api.get(`/admin/ticket-emails/event/${eventId}/pending`)
            ]);

            setEventStats(statsResponse.data.data);
            setPendingMembers(pendingResponse.data.data);
        } catch (error) {
            console.error('Failed to load event stats:', error);
        } finally {
            setLoading(false);
        }
    };

    const sendAllTicketEmails = async (eventId: number) => {
        if (!confirm('Are you sure you want to send all pending ticket emails for this event?')) {
            return;
        }

        try {
            setSendingEmails(true);
            const response = await api.post(`/admin/ticket-emails/event/${eventId}/send-all`);
            alert(`Successfully sent ${response.data.data.primaryEmailsSent} ticket emails!`);

            // Refresh data
            await loadTicketEmailOverview();
            if (selectedEvent === eventId) {
                await loadEventStats(eventId);
            }
        } catch (error) {
            console.error('Failed to send ticket emails:', error);
            alert('Failed to send ticket emails, please check console for error details');
        } finally {
            setSendingEmails(false);
        }
    };

    const sendSingleTicketEmail = async (memberId: number, memberName: string) => {
        if (!confirm(`Are you sure you want to send ticket email to ${memberName}?`)) {
            return;
        }

        try {
            const response = await api.post(`/admin/ticket-emails/member/${memberId}/send`);
            alert(`Successfully sent ticket email to ${memberName}!`);

            // Refresh data
            if (selectedEvent) {
                await loadEventStats(selectedEvent);
            }
        } catch (error) {
            console.error('Failed to send single ticket email:', error);
            alert('Failed to send ticket emails, please check console for error details');
        }
    };

    return (
        <Layout>
            <div className="max-w-7xl mx-auto p-6 space-y-6">
                <div className="flex items-center justify-between">
                    <h1 className="text-3xl font-bold text-gray-900">🎫 Ticket Email Management</h1>
                    <div className="flex gap-2">
                        <button
                            onClick={() => router.push('/admin/bmm-bulk-tickets')}
                            className="bg-green-500 hover:bg-green-600 text-white px-4 py-2 rounded-lg"
                        >
                            Bulk BMM Tickets
                        </button>
                        <button
                            onClick={loadTicketEmailOverview}
                            disabled={loading}
                            className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded-lg disabled:opacity-50"
                        >
                            {loading ? 'Loading...' : 'Refresh Data'}
                        </button>
                    </div>
                </div>

                {/* Event Overview */}
                <div className="bg-white rounded-lg shadow-md p-6">
                    <h2 className="text-xl font-semibold mb-4">📊 Event Ticket Sending Overview</h2>
                    <div className="overflow-x-auto">
                        <table className="min-w-full table-auto">
                            <thead>
                            <tr className="bg-gray-50">
                                <th className="px-4 py-2 text-left">Event Name</th>
                                <th className="px-4 py-2 text-left">Event Type</th>
                                <th className="px-4 py-2 text-center">Attendees</th>
                                <th className="px-4 py-2 text-center">Sent</th>
                                <th className="px-4 py-2 text-center">Pending</th>
                                <th className="px-4 py-2 text-center">Completion</th>
                                <th className="px-4 py-2 text-center">Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            {events.map((event) => (
                                <tr key={event.eventId} className="border-t hover:bg-gray-50">
                                    <td className="px-4 py-2">
                                        <div>
                                            <div className="font-medium">{event.eventName}</div>
                                            <div className="text-sm text-gray-500">{event.eventCode}</div>
                                        </div>
                                    </td>
                                    <td className="px-4 py-2">
                      <span className="px-2 py-1 bg-blue-100 text-blue-800 rounded-full text-xs">
                        {event.eventType}
                      </span>
                                    </td>
                                    <td className="px-4 py-2 text-center font-medium">{event.totalAttending}</td>
                                    <td className="px-4 py-2 text-center">
                                        <span className="text-green-600 font-medium">{event.ticketsSent}</span>
                                    </td>
                                    <td className="px-4 py-2 text-center">
                      <span className={`font-medium ${event.pending > 0 ? 'text-red-600' : 'text-gray-400'}`}>
                        {event.pending}
                      </span>
                                    </td>
                                    <td className="px-4 py-2 text-center">
                                        <div className="flex items-center justify-center">
                                            <div className="w-12 text-sm font-medium">{event.completionRate}</div>
                                            <div className="w-20 bg-gray-200 rounded-full h-2 ml-2">
                                                <div
                                                    className="bg-green-500 h-2 rounded-full"
                                                    style={{ width: event.completionRate }}
                                                ></div>
                                            </div>
                                        </div>
                                    </td>
                                    <td className="px-4 py-2 text-center space-x-2">
                                        <button
                                            onClick={() => {
                                                setSelectedEvent(event.eventId);
                                                loadEventStats(event.eventId);
                                            }}
                                            className="bg-blue-500 hover:bg-blue-600 text-white px-3 py-1 rounded text-sm"
                                        >
                                            Details
                                        </button>
                                        {event.pending > 0 && (
                                            <button
                                                onClick={() => sendAllTicketEmails(event.eventId)}
                                                disabled={sendingEmails}
                                                className="bg-green-500 hover:bg-green-600 text-white px-3 py-1 rounded text-sm disabled:opacity-50"
                                            >
                                                {sendingEmails ? 'Sending...' : 'Send All'}
                                            </button>
                                        )}
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                </div>

                {/* Event Detailed Statistics */}
                {selectedEvent && eventStats && (
                    <div className="bg-white rounded-lg shadow-md p-6">
                        <h2 className="text-xl font-semibold mb-4">📈 {eventStats.eventName} - Detailed Statistics</h2>

                        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
                            <div className="bg-blue-50 p-4 rounded-lg">
                                <div className="text-2xl font-bold text-blue-600">{eventStats.totalAttendingMembers}</div>
                                <div className="text-sm text-gray-600">Total Attendees</div>
                            </div>
                            <div className="bg-green-50 p-4 rounded-lg">
                                <div className="text-2xl font-bold text-green-600">{eventStats.ticketEmailsSent}</div>
                                <div className="text-sm text-gray-600">Emails Sent</div>
                            </div>
                            <div className="bg-orange-50 p-4 rounded-lg">
                                <div className="text-2xl font-bold text-orange-600">{eventStats.pendingTicketEmails}</div>
                                <div className="text-sm text-gray-600">Emails Pending</div>
                            </div>
                            <div className="bg-red-50 p-4 rounded-lg">
                                <div className="text-2xl font-bold text-red-600">{eventStats.membersWithoutEmail}</div>
                                <div className="text-sm text-gray-600">No Email Address</div>
                            </div>
                        </div>

                        <div className="text-center mb-4">
                            <div className="text-3xl font-bold text-gray-800">{eventStats.emailDeliveryRate}</div>
                            <div className="text-gray-600">Email Delivery Rate</div>
                        </div>

                        {/* Pending Members List */}
                        {pendingMembers.length > 0 && (
                            <div className="mt-6">
                                <h3 className="text-lg font-semibold mb-3">📋 Members Pending Ticket Email ({pendingMembers.length})</h3>
                                <div className="overflow-x-auto">
                                    <table className="min-w-full table-auto">
                                        <thead>
                                        <tr className="bg-gray-50">
                                            <th className="px-4 py-2 text-left">Member ID</th>
                                            <th className="px-4 py-2 text-left">Name</th>
                                            <th className="px-4 py-2 text-left">Email</th>
                                            <th className="px-4 py-2 text-left">Region</th>
                                            <th className="px-4 py-2 text-center">Actions</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {pendingMembers.map((member) => (
                                            <tr key={member.id} className="border-t hover:bg-gray-50">
                                                <td className="px-4 py-2 font-mono">{member.membershipNumber}</td>
                                                <td className="px-4 py-2">{member.name}</td>
                                                <td className="px-4 py-2">{member.primaryEmail}</td>
                                                <td className="px-4 py-2">{member.regionDesc || '-'}</td>
                                                <td className="px-4 py-2 text-center">
                                                    <button
                                                        onClick={() => sendSingleTicketEmail(member.id, member.name)}
                                                        className="bg-green-500 hover:bg-green-600 text-white px-3 py-1 rounded text-sm"
                                                    >
                                                        Send Ticket
                                                    </button>
                                                </td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {/* Usage Instructions */}
                <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
                    <h3 className="text-lg font-semibold text-yellow-800 mb-2">🎯 Unified System Architecture</h3>
                    <div className="text-yellow-700 space-y-2">
                        <p>✅ <strong>Automatic Ticket Sending</strong>: Beautiful ticket emails are sent automatically after members confirm attendance</p>
                        <p>✅ <strong>EventMember System</strong>: Unified data architecture supporting multi-event management</p>
                        <p>✅ <strong>Cinema Ticket Experience</strong>: Professionally designed HTML email templates with QR codes and complete instructions</p>
                        <p>✅ <strong>SMS Bulk Support</strong>: Event-based precision sending with Stratum API support</p>
                        <p>✅ <strong>Real-time Statistics</strong>: Complete sending status monitoring and data statistics</p>
                    </div>
                </div>
            </div>
        </Layout>
    );
}