'use client';
import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import Link from 'next/link';
import api from '@/services/api';

interface Event {
    id: number;
    name: string;
    eventCode: string;
    eventType: 'GENERAL_MEETING' | 'SPECIAL_CONFERENCE' | 'SURVEY_MEETING' | 'BMM_VOTING' | 'BALLOT_VOTING' | 'ANNUAL_MEETING' | 'WORKSHOP' | 'UNION_MEETING';
    eventDate: string;
    venue: string;
    description: string;
    isActive: boolean;
    isRegistrationEnabled: boolean;
    isVotingEnabled: boolean;
    registrationOpen: boolean;
    qrScanEnabled: boolean;
// 三个Informer数据源URL
    informerAttendeeUrl: string;
    informerEmailMembersUrl: string;
    informerSmsMembersUrl: string;
// 同步状态
    lastAttendeeSyncTime: string;
    lastEmailMembersSyncTime: string;
    lastSmsMembersSyncTime: string;
    attendeeSyncCount: number;
    emailMembersSyncCount: number;
    smsMembersSyncCount: number;
// 统计数据
    totalMembers: number;
    registeredMembers: number;
    attendingMembers: number;
    checkedInMembers: number;
    emailSentCount: number;
    smsSentCount: number;
    totalInvited: number;
    maxAttendees: number;
// 配置字段
    registrationFlow: string;
    eventConfig: string;
    customFields: string;
    pageContent: string;
    themeColor: string;
    targetRegions: string;
    primaryRegion: string;
    syncStatus: string;
    createdAt: string;
    updatedAt: string;
}

export default function EventsPage() {
    const router = useRouter();
    const [isAuthorized, setIsAuthorized] = useState(false);
    const [loading, setLoading] = useState(true);
    const [events, setEvents] = useState<Event[]>([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [filterType, setFilterType] = useState('all');
    const [filterStatus, setFilterStatus] = useState('all');
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [showConfigModal, setShowConfigModal] = useState(false);
    const [showSyncModal, setShowSyncModal] = useState(false);
    const [selectedEvent, setSelectedEvent] = useState<Event | null>(null);
    const [syncProgress, setSyncProgress] = useState<{[key: string]: boolean}>({});

    const [newEvent, setNewEvent] = useState({
        name: '',
        eventCode: '',
        datasetId: '',
        attendeeDatasetId: '',
        description: '',
        eventType: 'GENERAL_MEETING' as Event['eventType'],
        eventDate: '',
        venue: '',
        maxAttendees: '',
        isVotingEnabled: false,
        registrationOpen: true,
        qrScanEnabled: true,
        themeColor: '#1e40af',
        informerAttendeeUrl: '',
        informerEmailMembersUrl: '',
        informerSmsMembersUrl: '',
        eventTemplateId: '' as string
    });

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }
        setIsAuthorized(true);
        fetchEvents();
    }, [router]);

    const fetchEvents = async () => {
        try {
            setLoading(true);
            const response = await api.get('/admin/events');
            if (response.data.status === 'success') {
                setEvents(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch events', error);
            toast.error('Failed to load events');
        } finally {
            setLoading(false);
        }
    };

    const handleCreateEvent = async () => {
        try {
// 验证必填字段
            if (!newEvent.name || !newEvent.eventCode || !newEvent.datasetId) {
                toast.error('Please fill in all required fields (Name, Event Code, Dataset ID)');
                return;
            }

// 准备发送的数据，确保数据类型正确
            const eventData = {
                name: newEvent.name,
                eventCode: newEvent.eventCode,
                datasetId: newEvent.datasetId,
                attendeeDatasetId: newEvent.attendeeDatasetId || null,
                description: newEvent.description,
                eventType: newEvent.eventType,
                eventDate: newEvent.eventDate ? new Date(newEvent.eventDate).toISOString() : null,
                venue: newEvent.venue,
                maxAttendees: newEvent.maxAttendees ? parseInt(newEvent.maxAttendees) : null,
                isVotingEnabled: newEvent.isVotingEnabled,
                registrationOpen: newEvent.registrationOpen,
                eventTemplateId: newEvent.eventTemplateId && newEvent.eventTemplateId !== '' ? parseInt(newEvent.eventTemplateId) : null
            };

            const response = await api.post('/admin/events', eventData);
            if (response.data.status === 'success') {
                toast.success('Event created successfully');
                setShowCreateModal(false);
                fetchEvents();
                setNewEvent({
                    name: '',
                    eventCode: '',
                    datasetId: '',
                    attendeeDatasetId: '',
                    description: '',
                    eventType: 'GENERAL_MEETING' as Event['eventType'],
                    eventDate: '',
                    venue: '',
                    maxAttendees: '',
                    isVotingEnabled: false,
                    registrationOpen: true,
                    qrScanEnabled: true,
                    themeColor: '#1e40af',
                    informerAttendeeUrl: '',
                    informerEmailMembersUrl: '',
                    informerSmsMembersUrl: '',
                    eventTemplateId: '' as string
                });
            }
        } catch (error: any) {
            console.error('Failed to create event', error);
            if (error.response?.data?.message) {
                toast.error(`Failed to create event: ${error.response.data.message}`);
            } else {
                toast.error('Failed to create event');
            }
        }
    };

    const handleSyncData = async (eventId: number, dataSource: 'attendees' | 'email' | 'sms') => {
        try {
            setSyncProgress(prev => ({ ...prev, [`${eventId}-${dataSource}`]: true }));
            const response = await api.post(`/admin/events/${eventId}/sync/${dataSource}`);
            if (response.data.status === 'success') {
                toast.success(`${dataSource} data synced successfully`);
                fetchEvents();
            }
        } catch (error) {
            console.error(`Failed to sync ${dataSource} data`, error);
            toast.error(`Failed to sync ${dataSource} data`);
        } finally {
            setSyncProgress(prev => ({ ...prev, [`${eventId}-${dataSource}`]: false }));
        }
    };

    const handleToggleStatus = async (eventId: number, field: string, currentValue: boolean) => {
        try {
            const response = await api.put(`/admin/events/${eventId}`, {
                [field]: !currentValue
            });
            if (response.data.status === 'success') {
                toast.success(`Event ${field} updated successfully`);
                fetchEvents();
            }
        } catch (error) {
            console.error(`Failed to update ${field}`, error);
            toast.error(`Failed to update ${field}`);
        }
    };

    const getEventTypeColor = (type: string) => {
        const colors = {
            'GENERAL_MEETING': 'bg-blue-100 text-blue-800',
            'SPECIAL_CONFERENCE': 'bg-purple-100 text-purple-800',
            'SURVEY_MEETING': 'bg-green-100 text-green-800',
            'BMM_VOTING': 'bg-red-100 text-red-800',
            'BALLOT_VOTING': 'bg-orange-100 text-orange-800',
            'ANNUAL_MEETING': 'bg-indigo-100 text-indigo-800',
            'WORKSHOP': 'bg-yellow-100 text-yellow-800',
            'UNION_MEETING': 'bg-gray-100 text-gray-800'
        };
        return colors[type as keyof typeof colors] || 'bg-gray-100 text-gray-800';
    };

    const getEventTypeFeatures = (type: string) => {
        const features = {
            'SPECIAL_CONFERENCE': ['Attendance Confirmation', 'Absence Reason'],
            'SURVEY_MEETING': ['Survey Forms', 'Industry Filtering'],
            'BMM_VOTING': ['Special Vote Application', 'Three-Region QR Scan', 'Distance Verification'],
            'BALLOT_VOTING': ['Voting Function', 'QR Check-in'],
            'GENERAL_MEETING': ['Basic Registration', 'QR Check-in'],
            'ANNUAL_MEETING': ['Annual Statistics', 'QR Check-in'],
            'WORKSHOP': ['Workshop Functions', 'Skills Assessment'],
            'UNION_MEETING': ['Union Exclusive', 'Member Verification']
        };
        return features[type as keyof typeof features] || ['Basic Functions'];
    };

    const filteredEvents = events.filter(event => {
        const matchesSearch = event.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
            event.eventCode.toLowerCase().includes(searchTerm.toLowerCase());
        const matchesType = filterType === 'all' || event.eventType === filterType;
        const matchesStatus = filterStatus === 'all' ||
            (filterStatus === 'active' && event.isActive) ||
            (filterStatus === 'inactive' && !event.isActive);
        return matchesSearch && matchesType && matchesStatus;
    });

    if (!isAuthorized) return null;

    if (loading) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12 text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-purple-500"></div>
                    <p className="mt-4 text-gray-700 dark:text-gray-300">Loading events...</p>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="container mx-auto px-4 py-8">
                {/* Header */}
                <div className="flex justify-between items-center mb-8">
                    <div className="flex items-center">
                        <Link href="/admin/dashboard">
                            <button className="mr-4 text-gray-600 dark:text-gray-400 hover:text-black dark:hover:text-white">
                                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
                                </svg>
                            </button>
                        </Link>
                        <h1 className="text-3xl font-bold text-black dark:text-white">🗓️ E tū Events Management</h1>
                    </div>
                    <button
                        onClick={() => setShowCreateModal(true)}
                        className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 flex items-center"
                    >
                        <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                        </svg>
                        Create Event
                    </button>
                </div>

                {/* Filters */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6 mb-6">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Search Events</label>
                            <input
                                type="text"
                                placeholder="Search by name or code..."
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Event Type</label>
                            <select
                                value={filterType}
                                onChange={(e) => setFilterType(e.target.value)}
                                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                            >
                                <option value="all">All Types</option>
                                <option value="GENERAL_MEETING">General Meeting</option>
                                <option value="SPECIAL_CONFERENCE">Special Conference</option>
                                <option value="SURVEY_MEETING">Survey Meeting</option>
                                <option value="BMM_VOTING">BMM Voting</option>
                                <option value="BALLOT_VOTING">Ballot Voting</option>
                                <option value="ANNUAL_MEETING">Annual Meeting</option>
                                <option value="WORKSHOP">Workshop</option>
                                <option value="UNION_MEETING">Union Meeting</option>
                            </select>
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Status</label>
                            <select
                                value={filterStatus}
                                onChange={(e) => setFilterStatus(e.target.value)}
                                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                            >
                                <option value="all">All Status</option>
                                <option value="active">Active</option>
                                <option value="inactive">Inactive</option>
                            </select>
                        </div>
                    </div>
                </div>

                {/* Events Grid */}
                <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6">
                    {filteredEvents.map((event) => (
                        <div key={event.id} className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 overflow-hidden">
                            {/* Event Header */}
                            <div className="p-6 border-b border-gray-100 dark:border-gray-700">
                                <div className="flex justify-between items-start mb-3">
                                    <div>
                                        <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-1">{event.name}</h3>
                                        <p className="text-sm text-gray-500 dark:text-gray-400">Code: {event.eventCode}</p>
                                    </div>
                                    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${getEventTypeColor(event.eventType)}`}>
{event.eventType.replace('_', ' ')}
</span>
                                </div>
                                <div className="flex flex-wrap gap-1 mb-3">
                                    {getEventTypeFeatures(event.eventType).map((feature, index) => (
                                        <span key={index} className="inline-flex items-center px-2 py-1 rounded text-xs bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300">
{feature}
</span>
                                    ))}
                                </div>

                                <div className="text-sm text-gray-600 dark:text-gray-400">
                                    <p>📍 {event.venue}</p>
                                    <p>📅 {event.eventDate ? new Date(event.eventDate).toLocaleDateString() : 'TBD'}</p>
                                </div>
                            </div>

                            {/* Statistics */}
                            <div className="p-4 bg-gray-50 dark:bg-gray-700/50">
                                <div className="grid grid-cols-2 gap-4 text-sm">
                                    <div>
                                        <p className="text-gray-500 dark:text-gray-400">Total Members</p>
                                        <p className="font-semibold text-blue-600 dark:text-blue-400">{event.totalMembers || 0}</p>
                                    </div>
                                    <div>
                                        <p className="text-gray-500 dark:text-gray-400">Attending</p>
                                        <p className="font-semibold text-green-600 dark:text-green-400">{event.attendingMembers || 0}</p>
                                    </div>
                                    <div>
                                        <p className="text-gray-500 dark:text-gray-400">Checked In</p>
                                        <p className="font-semibold text-purple-600 dark:text-purple-400">{event.checkedInMembers || 0}</p>
                                    </div>
                                    <div>
                                        <p className="text-gray-500 dark:text-gray-400">Emails Sent</p>
                                        <p className="font-semibold text-orange-600 dark:text-orange-400">{event.emailSentCount || 0}</p>
                                    </div>
                                </div>
                            </div>

                            {/* Sync Status */}
                            <div className="p-4 border-t border-gray-100 dark:border-gray-700">
                                <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">🔄 Informer Data Sources</h4>
                                <div className="space-y-2">
                                    <div className="flex justify-between items-center">
                                        <span className="text-xs text-gray-600 dark:text-gray-400">Attendees</span>
                                        <div className="flex items-center space-x-2">
                                            <span className="text-xs text-gray-500 dark:text-gray-400">{event.attendeeSyncCount || 0}</span>
                                            <button
                                                onClick={() => handleSyncData(event.id, 'attendees')}
                                                disabled={syncProgress[`${event.id}-attendees`]}
                                                className="text-xs bg-blue-100 dark:bg-blue-900/50 text-blue-700 dark:text-blue-300 px-2 py-1 rounded hover:bg-blue-200 dark:hover:bg-blue-900/70 disabled:opacity-50"
                                            >
                                                {syncProgress[`${event.id}-attendees`] ? '⏳' : '🔄'}
                                            </button>
                                        </div>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-xs text-gray-600 dark:text-gray-400">Email Members</span>
                                        <div className="flex items-center space-x-2">
                                            <span className="text-xs text-gray-500 dark:text-gray-400">{event.emailMembersSyncCount || 0}</span>
                                            <button
                                                onClick={() => handleSyncData(event.id, 'email')}
                                                disabled={syncProgress[`${event.id}-email`]}
                                                className="text-xs bg-green-100 dark:bg-green-900/50 text-green-700 dark:text-green-300 px-2 py-1 rounded hover:bg-green-200 dark:hover:bg-green-900/70 disabled:opacity-50"
                                            >
                                                {syncProgress[`${event.id}-email`] ? '⏳' : '📧'}
                                            </button>
                                        </div>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-xs text-gray-600 dark:text-gray-400">SMS Members</span>
                                        <div className="flex items-center space-x-2">
                                            <span className="text-xs text-gray-500 dark:text-gray-400">{event.smsMembersSyncCount || 0}</span>
                                            <button
                                                onClick={() => handleSyncData(event.id, 'sms')}
                                                disabled={syncProgress[`${event.id}-sms`]}
                                                className="text-xs bg-yellow-100 dark:bg-yellow-900/50 text-yellow-700 dark:text-yellow-300 px-2 py-1 rounded hover:bg-yellow-200 dark:hover:bg-yellow-900/70 disabled:opacity-50"
                                            >
                                                {syncProgress[`${event.id}-sms`] ? '⏳' : '📱'}
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            {/* Actions */}
                            <div className="p-4 border-t border-gray-100 dark:border-gray-700">
                                <div className="flex justify-between items-center mb-3">
                                    <div className="flex space-x-2">
                                        <button
                                            onClick={() => handleToggleStatus(event.id, 'isActive', event.isActive)}
                                            className={`text-xs px-3 py-1 rounded ${event.isActive
                                                ? 'bg-green-100 dark:bg-green-900/50 text-green-700 dark:text-green-300'
                                                : 'bg-red-100 dark:bg-red-900/50 text-red-700 dark:text-red-300'}`}
                                        >
                                            {event.isActive ? '✅ Active' : '❌ Inactive'}
                                        </button>
                                        <button
                                            onClick={() => handleToggleStatus(event.id, 'registrationOpen', event.registrationOpen)}
                                            className={`text-xs px-3 py-1 rounded ${event.registrationOpen
                                                ? 'bg-blue-100 dark:bg-blue-900/50 text-blue-700 dark:text-blue-300'
                                                : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300'}`}
                                        >
                                            {event.registrationOpen ? '🔓 Reg Open' : '🔒 Reg Closed'}
                                        </button>
                                    </div>
                                </div>
                                <div className="flex space-x-2">
                                    <button
                                        onClick={() => {
                                            setSelectedEvent(event);
                                            setShowConfigModal(true);
                                        }}
                                        className="flex-1 bg-blue-600 hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-800 text-white text-sm py-2 px-3 rounded"
                                    >
                                        ⚙️ Configure
                                    </button>
                                    <Link href={`/admin/members?eventId=${event.id}`}>
                                        <button className="flex-1 bg-green-600 hover:bg-green-700 dark:bg-green-700 dark:hover:bg-green-800 text-white text-sm py-2 px-3 rounded">
                                            👥 Members
                                        </button>
                                    </Link>
                                    {event.qrScanEnabled && (
                                        <Link href={`/admin/checkin?eventId=${event.id}`}>
                                            <button className="bg-purple-600 hover:bg-purple-700 dark:bg-purple-700 dark:hover:bg-purple-800 text-white text-sm py-2 px-3 rounded">
                                                📱 QR
                                            </button>
                                        </Link>
                                    )}
                                </div>
                            </div>
                        </div>
                    ))}
                </div>

                {/* Create Event Modal */}
                {showCreateModal && (
                    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                        <div className="bg-white dark:bg-gray-800 rounded-lg p-6 w-full max-w-2xl max-h-[90vh] overflow-y-auto">
                            <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-4">Create New Event</h2>
                            <div className="space-y-4">
                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Event Name *</label>
                                        <input
                                            type="text"
                                            value={newEvent.name}
                                            onChange={(e) => setNewEvent({...newEvent, name: e.target.value})}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                            required
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Event Code *</label>
                                        <input
                                            type="text"
                                            value={newEvent.eventCode}
                                            onChange={(e) => setNewEvent({...newEvent, eventCode: e.target.value})}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                            required
                                        />
                                    </div>
                                </div>

                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Dataset ID *</label>
                                        <input
                                            type="text"
                                            value={newEvent.datasetId}
                                            onChange={(e) => setNewEvent({...newEvent, datasetId: e.target.value})}
                                            placeholder="e.g., BMM2025_MEMBERS"
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                            required
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Attendee Dataset ID</label>
                                        <input
                                            type="text"
                                            value={newEvent.attendeeDatasetId}
                                            onChange={(e) => setNewEvent({...newEvent, attendeeDatasetId: e.target.value})}
                                            placeholder="e.g., BMM2025_ATTENDEES"
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                        />
                                    </div>
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Event Type</label>
                                    <select
                                        value={newEvent.eventType}
                                        onChange={(e) => setNewEvent({...newEvent, eventType: e.target.value as Event['eventType']})}
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                    >
                                        <option value="GENERAL_MEETING">General Meeting</option>
                                        <option value="SPECIAL_CONFERENCE">Special Conference</option>
                                        <option value="SURVEY_MEETING">Survey Meeting</option>
                                        <option value="BMM_VOTING">BMM Voting</option>
                                        <option value="BALLOT_VOTING">Ballot Voting</option>
                                        <option value="ANNUAL_MEETING">Annual Meeting</option>
                                        <option value="WORKSHOP">Workshop</option>
                                        <option value="UNION_MEETING">Union Meeting</option>
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Event Template (Optional)</label>
                                    <select
                                        value={newEvent.eventTemplateId}
                                        onChange={(e) => setNewEvent({...newEvent, eventTemplateId: e.target.value})}
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                    >
                                        <option value="">Use Default Template</option>
                                        <option value="1">BMM Voting Template</option>
                                        <option value="2">Special Conference Template</option>
                                        <option value="3">Survey Meeting Template</option>
                                        <option value="4">Custom Template</option>
                                    </select>
                                    <p className="text-xs text-gray-500 mt-1">
                                        Templates define the custom pages, flows, and questions for this event type
                                    </p>
                                </div>

                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Event Date</label>
                                        <input
                                            type="datetime-local"
                                            value={newEvent.eventDate}
                                            onChange={(e) => setNewEvent({...newEvent, eventDate: e.target.value})}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Venue</label>
                                        <input
                                            type="text"
                                            value={newEvent.venue}
                                            onChange={(e) => setNewEvent({...newEvent, venue: e.target.value})}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                        />
                                    </div>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Description</label>
                                    <textarea
                                        value={newEvent.description}
                                        onChange={(e) => setNewEvent({...newEvent, description: e.target.value})}
                                        rows={3}
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Max Attendees</label>
                                    <input
                                        type="number"
                                        value={newEvent.maxAttendees}
                                        onChange={(e) => setNewEvent({...newEvent, maxAttendees: e.target.value})}
                                        placeholder="Total across all venues"
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100"
                                    />
                                    <p className="text-sm text-gray-500 mt-1">
                                        For BMM Voting: Total capacity across all 5 regional venues
                                    </p>
                                </div>

                                <div className="flex space-x-4">
                                    <label className="flex items-center text-gray-700 dark:text-gray-300">
                                        <input
                                            type="checkbox"
                                            checked={newEvent.isVotingEnabled}
                                            onChange={(e) => setNewEvent({...newEvent, isVotingEnabled: e.target.checked})}
                                            className="mr-2"
                                        />
                                        🗳️ Enable Voting
                                    </label>
                                    <label className="flex items-center text-gray-700 dark:text-gray-300">
                                        <input
                                            type="checkbox"
                                            checked={newEvent.qrScanEnabled}
                                            onChange={(e) => setNewEvent({...newEvent, qrScanEnabled: e.target.checked})}
                                            className="mr-2"
                                        />
                                        📱 Enable QR Scan
                                    </label>
                                </div>
                            </div>

                            <div className="flex justify-end space-x-3 mt-6">
                                <button
                                    onClick={() => setShowCreateModal(false)}
                                    className="px-4 py-2 text-gray-600 dark:text-gray-400 border border-gray-300 dark:border-gray-600 rounded-md hover:bg-gray-50 dark:hover:bg-gray-700"
                                >
                                    Cancel
                                </button>
                                <button
                                    onClick={handleCreateEvent}
                                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-800 text-white rounded-md"
                                >
                                    ✨ Create Event
                                </button>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </Layout>
    );
}