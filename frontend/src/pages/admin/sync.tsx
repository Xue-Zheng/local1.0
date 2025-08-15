'use client';
import React, { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import Link from 'next/link';
import api from '@/services/api';

interface SyncStats {
    total: number;
    toSync: number;
    SUCCESS?: number;
    FAILED?: number;
    IN_PROGRESS?: number;
    PENDING?: number;
    totalMembers?: number;
    emailMembers?: number;
    smsMembers?: number;
    attendees?: number;
    lastSyncTime?: string;
    todayEmailSynced?: number;
    todaySmsSynced?: number;
}

interface Event {
    id: number;
    name: string;
    eventCode: string;
    eventType: string;
    datasetId: string;
    attendeeDatasetId: string;
    syncStatus: string;
    lastSyncTime: string;
    memberSyncCount: number;
    attendeeSyncCount: number;
}

interface InformerSyncResult {
    success: boolean;
    message: string;
    dataSource: string;
    syncId?: string;
}

interface SyncProgress {
    syncId: string;
    status: string;
    syncType: string;
    totalRecords: number;
    processedRecords: number;
    progressPercentage: number;
    errorCount: number;
    message: string;
    startTime: string;
    endTime?: string;
}

export default function SyncPage() {
    const router = useRouter();
    const [isAuthorized, setIsAuthorized] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [events, setEvents] = useState<Event[]>([]);
    const [syncStats, setSyncStats] = useState<SyncStats | null>(null);
    const [informerStats, setInformerStats] = useState<SyncStats | null>(null);
    const [isSyncing, setIsSyncing] = useState(false);
    const [syncingEvents, setSyncingEvents] = useState<Set<number>>(new Set());
    const [syncingInformer, setSyncingInformer] = useState<Set<string>>(new Set());
    const [activeTab, setActiveTab] = useState<'events' | 'informer'>('informer');
    const [activeSyncs, setActiveSyncs] = useState<Map<string, SyncProgress>>(new Map());
    const syncPollingIntervalsRef = useRef<Map<string, NodeJS.Timeout>>(new Map());
    const processedSyncsRef = useRef<Set<string>>(new Set());

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }
        setIsAuthorized(true);
        fetchData();
    }, [router]);

    // Cleanup on unmount
    useEffect(() => {
        return () => {
            // Clean up all polling intervals on unmount
            syncPollingIntervalsRef.current.forEach(interval => clearInterval(interval));
            syncPollingIntervalsRef.current.clear();
            // Clear processed syncs to avoid memory leak
            processedSyncsRef.current.clear();
        };
    }, []);

    const fetchData = async () => {
        try {
            const promises = [
                api.get('/admin/events'),
                api.get('/admin/events/sync-stats'),
                api.get('/admin/sync/status')
            ];

            const [eventsResponse, statsResponse, informerResponse] = await Promise.all(promises);

            if (eventsResponse.data.status === 'success') {
                setEvents(eventsResponse.data.data);
            }

            if (statsResponse.data.status === 'success') {
                setSyncStats(statsResponse.data.data);
            }

            if (informerResponse.data.success) {
                setInformerStats(informerResponse.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch data', error);
            toast.error('Failed to load data');
        } finally {
            setIsLoading(false);
        }
    };

    // Poll sync progress
    const pollSyncProgress = async (syncId: string) => {
        try {
            const response = await api.get(`/admin/sync/progress/${syncId}`);

            if (response.data.success) {
                const progress: SyncProgress = {
                    syncId: response.data.syncId,
                    status: response.data.status,
                    syncType: response.data.syncType,
                    totalRecords: response.data.totalRecords,
                    processedRecords: response.data.processedRecords,
                    progressPercentage: response.data.progressPercentage,
                    errorCount: response.data.errorCount,
                    message: response.data.message,
                    startTime: response.data.startTime,
                    endTime: response.data.endTime
                };

                setActiveSyncs(prev => new Map(prev).set(syncId, progress));

                // Stop polling if sync is completed or failed
                if (progress.status === 'COMPLETED' || progress.status === 'FAILED') {
                    // First check if already processed to avoid duplicate notifications
                    if (processedSyncsRef.current.has(syncId)) {
                        return; // Exit early if already processed
                    }

                    // Mark as processed immediately to prevent race conditions
                    processedSyncsRef.current.add(syncId);

                    // Clear the polling interval
                    const interval = syncPollingIntervalsRef.current.get(syncId);
                    if (interval) {
                        clearInterval(interval);
                        syncPollingIntervalsRef.current.delete(syncId);
                    }

                    // Show completion toast
                    if (progress.status === 'COMPLETED') {
                        toast.success(`Sync completed: ${progress.syncType}`);
                    } else {
                        toast.error(`Sync failed: ${progress.message}`);
                    }

                    // Remove from active syncs after a delay to prevent re-triggering
                    setTimeout(() => {
                        setActiveSyncs(prev => {
                            const newMap = new Map(prev);
                            newMap.delete(syncId);
                            return newMap;
                        });
                    }, 3000);

                    // Refresh data
                    fetchData();
                }
            }
        } catch (error) {
            console.error('Failed to poll sync progress:', error);
        }
    };

    // Start polling for a sync task
    const startSyncPolling = (syncId: string) => {
        // Poll immediately
        pollSyncProgress(syncId);

        // Then poll every 2 seconds
        const interval = setInterval(() => {
            pollSyncProgress(syncId);
        }, 2000);

        syncPollingIntervalsRef.current.set(syncId, interval);
    };

    // Informer Data Sync Functions
    const handleInformerSync = async (syncType: 'email-members' | 'sms-members' | 'attendees' | 'all') => {
        setSyncingInformer(prev => new Set(prev).add(syncType));

        try {
            const response = await api.post(`/admin/sync/${syncType}`);

            if (response.data.success) {
                // Check if this is an async sync (has syncId)
                if (response.data.syncId) {
                    toast.info(`${syncType} sync task submitted. Tracking progress...`);

                    // Start polling for progress
                    startSyncPolling(response.data.syncId);

                    // Remove from syncing set since it's now tracked in activeSyncs
                    setSyncingInformer(prev => {
                        const newSet = new Set(prev);
                        newSet.delete(syncType);
                        return newSet;
                    });
                } else {
                    // Synchronous sync completed
                    toast.success(response.data.message);
                    await fetchData(); // Refresh data after sync

                    setSyncingInformer(prev => {
                        const newSet = new Set(prev);
                        newSet.delete(syncType);
                        return newSet;
                    });
                }
            } else {
                toast.error(response.data.message);
                setSyncingInformer(prev => {
                    const newSet = new Set(prev);
                    newSet.delete(syncType);
                    return newSet;
                });
            }
        } catch (error: any) {
            console.error(`Failed to sync ${syncType}`, error);
            toast.error(`Failed to sync ${syncType}: ${error.response?.data?.message || error.message}`);
            setSyncingInformer(prev => {
                const newSet = new Set(prev);
                newSet.delete(syncType);
                return newSet;
            });
        }
    };

    // Event Sync Functions
    const handleSyncAll = async () => {
        setIsSyncing(true);
        try {
            const response = await api.post('/admin/events/sync-all');
            if (response.data.status === 'success') {
                toast.success('All events sync started successfully');
                await fetchData();
            }
        } catch (error) {
            console.error('Failed to sync all events', error);
            toast.error('Failed to start sync for all events');
        } finally {
            setIsSyncing(false);
        }
    };

    const handleSyncEvent = async (eventId: number) => {
        setSyncingEvents(prev => new Set(prev).add(eventId));
        try {
            const response = await api.post(`/admin/events/${eventId}/sync`);
            if (response.data.status === 'success') {
                toast.success('Event sync started successfully');
                await fetchData();
            }
        } catch (error) {
            console.error('Failed to sync event', error);
            toast.error('Failed to start event sync');
        } finally {
            setSyncingEvents(prev => {
                const newSet = new Set(prev);
                newSet.delete(eventId);
                return newSet;
            });
        }
    };

    const getSyncStatusBadge = (status: string) => {
        const statusStyles = {
            'SUCCESS': 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300',
            'FAILED': 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300',
            'IN_PROGRESS': 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300',
            'PENDING': 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300'
        };

        return (
            <span className={`px-2 py-1 text-xs font-medium rounded-full ${statusStyles[status as keyof typeof statusStyles] || 'bg-gray-100 text-gray-800'}`}>
                {status}
            </span>
        );
    };

    if (isLoading) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12 text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-purple-500"></div>
                    <p className="mt-4 text-gray-700 dark:text-gray-300">Loading synchronization data...</p>
                </div>
            </Layout>
        );
    }

    if (!isAuthorized) {
        return null;
    }

    return (
        <Layout>
            <div className="container mx-auto px-4 py-8">
                <div className="flex justify-between items-center mb-6">
                    <div className="flex items-center">
                        <Link href="/admin/dashboard">
                            <button className="mr-4 text-gray-600 dark:text-gray-400 hover:text-black dark:hover:text-white">
                                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
                                </svg>
                            </button>
                        </Link>
                        <h1 className="text-3xl font-bold text-black dark:text-white">🔄 Data Synchronization</h1>
                    </div>
                </div>

                {/* Tab Navigation */}
                <div className="border-b border-gray-200 dark:border-gray-700 mb-6">
                    <nav className="-mb-px flex space-x-8">
                        {[
                            { id: 'informer', name: '📊 Informer Data Sync', icon: '🔗' },
                            { id: 'events', name: '📅 Event Sync', icon: '⚡' }
                        ].map((tab) => (
                            <button
                                key={tab.id}
                                onClick={() => setActiveTab(tab.id as any)}
                                className={`py-2 px-1 border-b-2 font-medium text-sm ${
                                    activeTab === tab.id
                                        ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                        : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 dark:text-gray-400 dark:hover:text-gray-300'
                                }`}
                            >
                                {tab.icon} {tab.name}
                            </button>
                        ))}
                    </nav>
                </div>

                {/* Informer Data Sync Tab */}
                {activeTab === 'informer' && (
                    <div className="space-y-6">
                        {/* Informer Sync Statistics */}
                        {informerStats && (
                            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                                <h2 className="text-xl font-bold text-gray-800 dark:text-white mb-4">📈 Informer Data Statistics</h2>
                                <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
                                    <div className="text-center">
                                        <p className="text-2xl font-bold text-blue-600">{informerStats.totalMembers || 0}</p>
                                        <p className="text-sm text-gray-500 dark:text-gray-400">Total Members</p>
                                    </div>
                                    <div className="text-center">
                                        <p className="text-2xl font-bold text-green-600">{informerStats.emailMembers || 0}</p>
                                        <p className="text-sm text-gray-500 dark:text-gray-400">Email Members</p>
                                    </div>
                                    <div className="text-center">
                                        <p className="text-2xl font-bold text-purple-600">{informerStats.smsMembers || 0}</p>
                                        <p className="text-sm text-gray-500 dark:text-gray-400">SMS Members</p>
                                    </div>
                                    <div className="text-center">
                                        <p className="text-2xl font-bold text-orange-600">{informerStats.attendees || 0}</p>
                                        <p className="text-sm text-gray-500 dark:text-gray-400">Attendees</p>
                                    </div>
                                    <div className="text-center">
                                        <p className="text-2xl font-bold text-cyan-600">{informerStats.todayEmailSynced || 0}</p>
                                        <p className="text-sm text-gray-500 dark:text-gray-400">Today Email Sync</p>
                                    </div>
                                    <div className="text-center">
                                        <p className="text-2xl font-bold text-pink-600">{informerStats.todaySmsSynced || 0}</p>
                                        <p className="text-sm text-gray-500 dark:text-gray-400">Today SMS Sync</p>
                                    </div>
                                </div>
                                {informerStats.lastSyncTime && (
                                    <div className="mt-4 text-sm text-gray-500 dark:text-gray-400">
                                        Last sync: {informerStats.lastSyncTime}
                                    </div>
                                )}
                            </div>
                        )}

                        {/* Informer Sync Controls */}
                        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                            <h2 className="text-xl font-bold text-gray-800 dark:text-white mb-4">🔗 Informer Data Sources</h2>
                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">

                                {/* Email Members Sync */}
                                <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                                    <div className="flex items-center mb-3">
                                        <span className="text-2xl mr-2">📧</span>
                                        <h3 className="font-semibold text-gray-800 dark:text-white">Email Members</h3>
                                    </div>
                                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                                        Active E tū members with email addresses on file
                                    </p>
                                    <button
                                        onClick={() => handleInformerSync('email-members')}
                                        disabled={syncingInformer.has('email-members')}
                                        className="w-full bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed"
                                    >
                                        {syncingInformer.has('email-members') ? (
                                            <span className="flex items-center justify-center">
                                                <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                                </svg>
                                                Syncing...
                                            </span>
                                        ) : (
                                            '🔄 Sync Email Members'
                                        )}
                                    </button>
                                </div>

                                {/* SMS Members Sync */}
                                <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                                    <div className="flex items-center mb-3">
                                        <span className="text-2xl mr-2">📱</span>
                                        <h3 className="font-semibold text-gray-800 dark:text-white">SMS Members</h3>
                                    </div>
                                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                                        SMS-only members without email addresses
                                    </p>
                                    <button
                                        onClick={() => handleInformerSync('sms-members')}
                                        disabled={syncingInformer.has('sms-members')}
                                        className="w-full bg-purple-600 hover:bg-purple-700 text-white px-4 py-2 rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed"
                                    >
                                        {syncingInformer.has('sms-members') ? (
                                            <span className="flex items-center justify-center">
                                                <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                                </svg>
                                                Syncing...
                                            </span>
                                        ) : (
                                            '🔄 Sync SMS Members'
                                        )}
                                    </button>
                                </div>

                                {/* Attendees Sync - DISABLED FOR BMM */}
                                <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-4 bg-gray-50 dark:bg-gray-900 opacity-75">
                                    <div className="flex items-center mb-3">
                                        <span className="text-2xl mr-2">🎯</span>
                                        <h3 className="font-semibold text-gray-800 dark:text-white">Event Attendees</h3>
                                        <span className="ml-2 px-2 py-1 text-xs bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300 rounded">DISABLED</span>
                                    </div>
                                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                                        Complete import: Auto-creates Events + Members + EventMember
                                        <br />
                                        <span className="text-red-600 dark:text-red-400 font-medium">Note: Attendee sync disabled for BMM system compatibility</span>
                                    </p>
                                    <button
                                        disabled={true}
                                        className="w-full bg-gray-400 text-gray-600 px-4 py-2 rounded text-sm cursor-not-allowed opacity-50"
                                        title="Attendee sync is disabled for BMM system compatibility"
                                    >
                                        ❌ Sync Attendees (Disabled)
                                    </button>
                                </div>

                                {/* Sync All */}
                                <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-4 bg-gradient-to-br from-green-50 to-blue-50 dark:from-green-900/20 dark:to-blue-900/20">
                                    <div className="flex items-center mb-3">
                                        <span className="text-2xl mr-2">⚡</span>
                                        <h3 className="font-semibold text-gray-800 dark:text-white">Sync All</h3>
                                    </div>
                                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                                        Synchronize all three data sources in sequence
                                    </p>
                                    <button
                                        onClick={() => handleInformerSync('all')}
                                        disabled={syncingInformer.has('all') || syncingInformer.size > 0}
                                        className="w-full bg-green-600 hover:bg-green-700 text-white px-4 py-2 rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed font-semibold"
                                    >
                                        {syncingInformer.has('all') ? (
                                            <span className="flex items-center justify-center">
                                                <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                                </svg>
                                                Syncing All...
                                            </span>
                                        ) : (
                                            '🚀 Sync All Data Sources'
                                        )}
                                    </button>
                                </div>
                            </div>
                        </div>

                        {/* Informer Configuration Info */}
                        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                            <h2 className="text-xl font-bold text-gray-800 dark:text-white mb-4">🔧 Informer Configuration</h2>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                <div>
                                    <h3 className="font-medium text-gray-800 dark:text-white mb-2">Base URL:</h3>
                                    <p className="text-sm text-gray-600 dark:text-gray-400 font-mono break-all bg-gray-50 dark:bg-gray-700 p-2 rounded">
                                        https://etu-inf5-rsau.aptsolutions.net/api/datasets/
                                    </p>
                                </div>
                                <div>
                                    <h3 className="font-medium text-gray-800 dark:text-white mb-2">Data Sources:</h3>
                                    <ul className="text-sm text-gray-600 dark:text-gray-400 space-y-1">
                                        <li>📧 Email Members: 3bdf6d2b-e642-47a5-abc8-c466b3b8910c</li>
                                        <li>📱 SMS Members: 7fb904b4-05c9-4e14-afe9-25296fde8ed7</li>
                                        <li>🎯 Event Attendees: d382fc79-1230-4a1d-917a-7bc43aa21a84 (Complete Import)</li>
                                    </ul>
                                </div>
                            </div>
                        </div>

                        {/* Active Sync Progress */}
                        {activeSyncs.size > 0 && (
                            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                                <h2 className="text-xl font-bold text-gray-800 dark:text-white mb-4">🚀 Active Sync Tasks</h2>
                                <div className="space-y-4">
                                    {Array.from(activeSyncs.values()).map((progress) => (
                                        <div key={progress.syncId} className="border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                                            <div className="flex justify-between items-center mb-2">
                                                <div className="flex items-center">
                                                    <span className="font-semibold">{progress.syncType}</span>
                                                    <span className={`ml-2 px-2 py-1 text-xs rounded ${
                                                        progress.status === 'IN_PROGRESS' ? 'bg-blue-100 text-blue-800' :
                                                            progress.status === 'COMPLETED' ? 'bg-green-100 text-green-800' :
                                                                progress.status === 'FAILED' ? 'bg-red-100 text-red-800' :
                                                                    'bg-gray-100 text-gray-800'
                                                    }`}>
                                                        {progress.status}
                                                    </span>
                                                </div>
                                                <span className="text-sm text-gray-500">
                                                    {progress.processedRecords} / {progress.totalRecords || '?'} records
                                                </span>
                                            </div>

                                            {/* Progress Bar */}
                                            <div className="w-full bg-gray-200 rounded-full h-2.5 mb-2">
                                                <div
                                                    className="bg-blue-600 h-2.5 rounded-full transition-all duration-300"
                                                    style={{ width: `${progress.progressPercentage || 0}%` }}
                                                ></div>
                                            </div>

                                            <div className="flex justify-between items-center text-sm">
                                                <span className="text-gray-500">{progress.message || 'Processing...'}</span>
                                                <span className="text-gray-500">{Math.round(progress.progressPercentage || 0)}%</span>
                                            </div>

                                            {progress.errorCount > 0 && (
                                                <div className="mt-2 text-sm text-red-600">
                                                    ⚠️ {progress.errorCount} errors encountered
                                                </div>
                                            )}
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {/* Event Sync Tab */}
                {activeTab === 'events' && (
                    <div className="space-y-6">
                        {/* Event Sync Statistics */}
                        {syncStats && (
                            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                                <h2 className="text-xl font-bold text-gray-800 dark:text-white mb-4">📊 Event Sync Statistics</h2>
                                <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
                                    <div className="text-center">
                                        <p className="text-2xl font-bold text-gray-800 dark:text-white">{syncStats.total}</p>
                                        <p className="text-sm text-gray-500 dark:text-gray-400">Total Events</p>
                                    </div>
                                    <div className="text-center">
                                        <p className="text-2xl font-bold text-yellow-600">{syncStats.toSync}</p>
                                        <p className="text-sm text-gray-500 dark:text-gray-400">To Sync</p>
                                    </div>
                                    <div className="text-center">
                                        <p className="text-2xl font-bold text-green-600">{syncStats.SUCCESS || 0}</p>
                                        <p className="text-sm text-gray-500 dark:text-gray-400">Success</p>
                                    </div>
                                    <div className="text-center">
                                        <p className="text-2xl font-bold text-red-600">{syncStats.FAILED || 0}</p>
                                        <p className="text-sm text-gray-500 dark:text-gray-400">Failed</p>
                                    </div>
                                    <div className="text-center">
                                        <p className="text-2xl font-bold text-blue-600">{syncStats.IN_PROGRESS || 0}</p>
                                        <p className="text-sm text-gray-500 dark:text-gray-400">In Progress</p>
                                    </div>
                                </div>
                            </div>
                        )}

                        <div className="flex justify-end mb-4">
                            <button
                                onClick={handleSyncAll}
                                disabled={isSyncing}
                                className="bg-purple-600 hover:bg-purple-700 text-white px-6 py-2 rounded disabled:opacity-50"
                            >
                                {isSyncing ? 'Syncing All...' : '🚀 Sync All Events'}
                            </button>
                        </div>

                        {/* Events Table */}
                        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md overflow-hidden">
                            <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
                                <h2 className="text-xl font-bold text-gray-800 dark:text-white">📅 Events Synchronization</h2>
                            </div>
                            <div className="overflow-x-auto">
                                <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                                    <thead className="bg-gray-50 dark:bg-gray-700">
                                    <tr>
                                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Event</th>
                                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Status</th>
                                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Last Sync</th>
                                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Members/Attendees</th>
                                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Actions</th>
                                    </tr>
                                    </thead>
                                    <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                                    {events.map((event) => (
                                        <tr key={event.id} className="hover:bg-gray-50 dark:hover:bg-gray-700">
                                            <td className="px-4 py-3">
                                                <div>
                                                    <p className="text-sm font-medium text-gray-900 dark:text-white">{event.name}</p>
                                                    <p className="text-sm text-gray-500 dark:text-gray-400">{event.eventCode} - {event.eventType}</p>
                                                </div>
                                            </td>
                                            <td className="px-4 py-3">
                                                {getSyncStatusBadge(event.syncStatus)}
                                            </td>
                                            <td className="px-4 py-3">
                                                <p className="text-sm text-gray-700 dark:text-gray-300">
                                                    {event.lastSyncTime ? new Date(event.lastSyncTime).toLocaleString() : 'Never'}
                                                </p>
                                            </td>
                                            <td className="px-4 py-3">
                                                <p className="text-sm text-gray-700 dark:text-gray-300">
                                                    {event.memberSyncCount || 0} / {event.attendeeSyncCount || 0}
                                                </p>
                                            </td>
                                            <td className="px-4 py-3">
                                                <button
                                                    onClick={() => handleSyncEvent(event.id)}
                                                    disabled={syncingEvents.has(event.id)}
                                                    className="bg-indigo-600 hover:bg-indigo-700 text-white px-3 py-1 rounded text-sm disabled:opacity-50"
                                                >
                                                    {syncingEvents.has(event.id) ? 'Syncing...' : 'Sync'}
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </Layout>
    );
}