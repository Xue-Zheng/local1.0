import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/common/Layout';
import api from '@/services/api';
import { toast } from 'react-hot-toast';

interface TimelineEvent {
    date: string;
    stage: string;
    stageName: string;
    totalSent: number;
    successCount: number;
    failureCount: number;
    regionBreakdown: { [key: string]: number };
    notificationTypes: { [key: string]: number };
}

interface StageProgress {
    stage1: {
        name: string;
        total: number;
        completed: number;
        percentage: number;
    };
    stage2: {
        name: string;
        total: number;
        completed: number;
        percentage: number;
    };
    stage3: {
        name: string;
        total: number;
        completed: number;
        percentage: number;
    };
}

interface RegionCompletion {
    totalMembers: number;
    stage1_preRegistered: number;
    stage1_completion: number;
    stage2_confirmed: number;
    stage2_completion: number;
    stage2_attending: number;
    stage3_eligible: number;
    stage3_applied: number;
    stage3_completion: number;
}

interface StageCompletionData {
    byRegion: {
        [key: string]: RegionCompletion;
    };
    generatedAt: string;
}

interface TimelineData {
    events: TimelineEvent[];
    stageProgress: StageProgress;
    recentActivity: {
        registrations: number;
        confirmations: number;
        notifications: number;
        period: string;
        since: string;
    };
    dateRange: {
        start: string;
        end: string;
    };
    totalEvents: number;
}

export default function BmmTimelinePage() {
    const router = useRouter();
    const [loading, setLoading] = useState(true);
    const [timelineData, setTimelineData] = useState<TimelineData | null>(null);
    const [stageCompletionData, setStageCompletionData] = useState<StageCompletionData | null>(null);

    // Filter states
    const [selectedRegion, setSelectedRegion] = useState('');
    const [selectedStage, setSelectedStage] = useState('');
    const [selectedDays, setSelectedDays] = useState(7);
    const [refreshInterval, setRefreshInterval] = useState<NodeJS.Timeout | null>(null);

    const regions = ['Northern Region', 'Central Region', 'Southern Region'];
    const stages = [
        { value: '', label: 'All Stages' },
        { value: 'pre_registration', label: 'Pre-Registration' },
        { value: 'confirmation', label: 'Confirmation' },
        { value: 'special_vote', label: 'Special Vote' }
    ];

    const fetchTimelineData = async () => {
        try {
            const params = new URLSearchParams();
            if (selectedRegion) params.append('region', selectedRegion);
            if (selectedStage) params.append('stage', selectedStage);
            params.append('days', selectedDays.toString());

            const response = await api.get(`/admin/bmm/timeline?${params.toString()}`);
            if (response.data.status === 'success') {
                setTimelineData(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch timeline data:', error);
            toast.error('Failed to load timeline data');
        }
    };

    const fetchStageCompletionData = async () => {
        try {
            const response = await api.get('/admin/bmm/stage-completion');
            if (response.data.status === 'success') {
                setStageCompletionData(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch stage completion data:', error);
            toast.error('Failed to load stage completion data');
        }
    };

    useEffect(() => {
        const loadData = async () => {
            setLoading(true);
            await Promise.all([fetchTimelineData(), fetchStageCompletionData()]);
            setLoading(false);
        };

        loadData();
    }, [selectedRegion, selectedStage, selectedDays]);

    // Auto-refresh every 30 seconds
    useEffect(() => {
        const interval = setInterval(() => {
            fetchTimelineData();
            fetchStageCompletionData();
        }, 30000);

        setRefreshInterval(interval);
        return () => clearInterval(interval);
    }, [selectedRegion, selectedStage, selectedDays]);

    const getStageColor = (stage: string) => {
        switch (stage) {
            case 'pre_registration': return 'bg-blue-100 text-blue-800';
            case 'confirmation': return 'bg-green-100 text-green-800';
            case 'special_vote': return 'bg-purple-100 text-purple-800';
            default: return 'bg-gray-100 text-gray-800';
        }
    };

    const getRegionColor = (region: string) => {
        switch (region) {
            case 'Northern Region': return 'bg-green-100 text-green-800';
            case 'Central Region': return 'bg-blue-100 text-blue-800';
            case 'Southern Region': return 'bg-purple-100 text-purple-800';
            default: return 'bg-gray-100 text-gray-800';
        }
    };

    const formatDate = (dateString: string) => {
        return new Date(dateString).toLocaleDateString('en-NZ', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    };

    const formatTime = (dateString: string) => {
        return new Date(dateString).toLocaleTimeString('en-NZ', {
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    if (loading) {
        return (
            <Layout>
                <div className="flex items-center justify-center h-96">
                    <div className="text-center">
                        <div className="inline-block animate-spin rounded-full h-12 w-12 border-t-4 border-blue-500"></div>
                        <p className="mt-4 text-lg text-gray-700">Loading BMM Timeline...</p>
                    </div>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div>
                <div className="container mx-auto px-4 py-8">
                    {/* Header */}
                    <div className="mb-8">
                        <h1 className="text-3xl font-bold text-gray-900 mb-4">
                            📊 BMM Timeline & Progress Tracking
                        </h1>
                        <p className="text-gray-600 mb-6">
                            Comprehensive tracking of BMM progress across all three stages and regions
                        </p>

                        {/* Filter Controls */}
                        <div className="bg-white rounded-lg shadow p-6 mb-6">
                            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Region</label>
                                    <select
                                        value={selectedRegion}
                                        onChange={(e) => setSelectedRegion(e.target.value)}
                                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                                    >
                                        <option value="">All Regions</option>
                                        {regions.map(region => (
                                            <option key={region} value={region}>{region}</option>
                                        ))}
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Stage</label>
                                    <select
                                        value={selectedStage}
                                        onChange={(e) => setSelectedStage(e.target.value)}
                                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                                    >
                                        {stages.map(stage => (
                                            <option key={stage.value} value={stage.value}>{stage.label}</option>
                                        ))}
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Time Period</label>
                                    <select
                                        value={selectedDays}
                                        onChange={(e) => setSelectedDays(Number(e.target.value))}
                                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                                    >
                                        <option value={1}>Last 24 hours</option>
                                        <option value={3}>Last 3 days</option>
                                        <option value={7}>Last 7 days</option>
                                        <option value={14}>Last 14 days</option>
                                        <option value={30}>Last 30 days</option>
                                    </select>
                                </div>

                                <div className="flex items-end">
                                    <button
                                        onClick={() => {
                                            fetchTimelineData();
                                            fetchStageCompletionData();
                                        }}
                                        className="w-full bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                    >
                                        🔄 Refresh
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Overall Stage Progress */}
                    {timelineData && (
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
                            <div className="bg-white rounded-lg shadow p-6">
                                <div className="flex items-center justify-between mb-4">
                                    <h3 className="text-lg font-semibold text-gray-900">Stage 1: Pre-Registration</h3>
                                    <span className="text-2xl">📝</span>
                                </div>
                                <div className="flex items-center justify-between mb-2">
                                    <span className="text-sm text-gray-600">Progress</span>
                                    <span className="text-sm font-medium text-gray-900">
                                        {timelineData.stageProgress.stage1.completed}/{timelineData.stageProgress.stage1.total}
                                    </span>
                                </div>
                                <div className="w-full bg-gray-200 rounded-full h-2 mb-4">
                                    <div
                                        className="bg-blue-600 h-2 rounded-full"
                                        style={{ width: `${timelineData.stageProgress.stage1.percentage}%` }}
                                    ></div>
                                </div>
                                <div className="text-2xl font-bold text-blue-600">
                                    {Math.round(timelineData.stageProgress.stage1.percentage)}%
                                </div>
                            </div>

                            <div className="bg-white rounded-lg shadow p-6">
                                <div className="flex items-center justify-between mb-4">
                                    <h3 className="text-lg font-semibold text-gray-900">Stage 2: Confirmation</h3>
                                    <span className="text-2xl">✅</span>
                                </div>
                                <div className="flex items-center justify-between mb-2">
                                    <span className="text-sm text-gray-600">Progress</span>
                                    <span className="text-sm font-medium text-gray-900">
                                        {timelineData.stageProgress.stage2.completed}/{timelineData.stageProgress.stage2.total}
                                    </span>
                                </div>
                                <div className="w-full bg-gray-200 rounded-full h-2 mb-4">
                                    <div
                                        className="bg-green-600 h-2 rounded-full"
                                        style={{ width: `${timelineData.stageProgress.stage2.percentage}%` }}
                                    ></div>
                                </div>
                                <div className="text-2xl font-bold text-green-600">
                                    {Math.round(timelineData.stageProgress.stage2.percentage)}%
                                </div>
                            </div>

                            <div className="bg-white rounded-lg shadow p-6">
                                <div className="flex items-center justify-between mb-4">
                                    <h3 className="text-lg font-semibold text-gray-900">Stage 3: Special Vote</h3>
                                    <span className="text-2xl">🗳️</span>
                                </div>
                                <div className="flex items-center justify-between mb-2">
                                    <span className="text-sm text-gray-600">Progress</span>
                                    <span className="text-sm font-medium text-gray-900">
                                        {timelineData.stageProgress.stage3.completed}/{timelineData.stageProgress.stage3.total}
                                    </span>
                                </div>
                                <div className="w-full bg-gray-200 rounded-full h-2 mb-4">
                                    <div
                                        className="bg-purple-600 h-2 rounded-full"
                                        style={{ width: `${timelineData.stageProgress.stage3.percentage}%` }}
                                    ></div>
                                </div>
                                <div className="text-2xl font-bold text-purple-600">
                                    {Math.round(timelineData.stageProgress.stage3.percentage)}%
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Recent Activity Summary */}
                    {timelineData && (
                        <div className="bg-white rounded-lg shadow p-6 mb-8">
                            <h3 className="text-lg font-semibold text-gray-900 mb-4">Recent Activity (Last 24 Hours)</h3>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                <div className="text-center">
                                    <div className="text-3xl font-bold text-blue-600">{timelineData.recentActivity.registrations}</div>
                                    <div className="text-sm text-gray-600">New Registrations</div>
                                </div>
                                <div className="text-center">
                                    <div className="text-3xl font-bold text-green-600">{timelineData.recentActivity.confirmations}</div>
                                    <div className="text-sm text-gray-600">Confirmations</div>
                                </div>
                                <div className="text-center">
                                    <div className="text-3xl font-bold text-purple-600">{timelineData.recentActivity.notifications}</div>
                                    <div className="text-sm text-gray-600">Notifications Sent</div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Regional Stage Completion */}
                    {stageCompletionData && (
                        <div className="bg-white rounded-lg shadow p-6 mb-8">
                            <h3 className="text-lg font-semibold text-gray-900 mb-4">Regional Stage Completion</h3>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                                {Object.entries(stageCompletionData.byRegion).map(([region, data]) => (
                                    <div key={region} className="border rounded-lg p-4">
                                        <div className="flex items-center justify-between mb-3">
                                            <h4 className="font-medium text-gray-900">{region}</h4>
                                            <span className={`px-2 py-1 rounded text-xs ${getRegionColor(region)}`}>
                                                {data.totalMembers} members
                                            </span>
                                        </div>

                                        <div className="space-y-3">
                                            <div>
                                                <div className="flex justify-between text-sm mb-1">
                                                    <span>Pre-Registration</span>
                                                    <span>{Math.round(data.stage1_completion)}%</span>
                                                </div>
                                                <div className="w-full bg-gray-200 rounded-full h-2">
                                                    <div
                                                        className="bg-blue-600 h-2 rounded-full"
                                                        style={{ width: `${data.stage1_completion}%` }}
                                                    ></div>
                                                </div>
                                            </div>

                                            <div>
                                                <div className="flex justify-between text-sm mb-1">
                                                    <span>Confirmation</span>
                                                    <span>{Math.round(data.stage2_completion)}%</span>
                                                </div>
                                                <div className="w-full bg-gray-200 rounded-full h-2">
                                                    <div
                                                        className="bg-green-600 h-2 rounded-full"
                                                        style={{ width: `${data.stage2_completion}%` }}
                                                    ></div>
                                                </div>
                                            </div>

                                            {region === 'Southern Region' && (
                                                <div>
                                                    <div className="flex justify-between text-sm mb-1">
                                                        <span>Special Vote</span>
                                                        <span>{Math.round(data.stage3_completion)}%</span>
                                                    </div>
                                                    <div className="w-full bg-gray-200 rounded-full h-2">
                                                        <div
                                                            className="bg-purple-600 h-2 rounded-full"
                                                            style={{ width: `${data.stage3_completion}%` }}
                                                        ></div>
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Timeline Events */}
                    {timelineData && (
                        <div className="bg-white rounded-lg shadow p-6">
                            <h3 className="text-lg font-semibold text-gray-900 mb-4">Communication Timeline</h3>

                            {timelineData.events.length === 0 ? (
                                <div className="text-center py-8">
                                    <div className="text-gray-500">No timeline events found for the selected criteria</div>
                                </div>
                            ) : (
                                <div className="space-y-4">
                                    {timelineData.events.map((event, index) => (
                                        <div key={index} className="border-l-4 border-blue-500 pl-4">
                                            <div className="flex flex-col md:flex-row md:items-center md:justify-between">
                                                <div className="flex-1">
                                                    <div className="flex items-center space-x-2 mb-2">
                                                        <span className={`px-2 py-1 rounded text-xs ${getStageColor(event.stage)}`}>
                                                            {event.stageName}
                                                        </span>
                                                        <span className="text-sm text-gray-600">{formatDate(event.date)}</span>
                                                    </div>

                                                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                                                        <div>
                                                            <span className="text-gray-600">Total Sent:</span>
                                                            <span className="font-medium ml-1">{event.totalSent}</span>
                                                        </div>
                                                        <div>
                                                            <span className="text-gray-600">Success:</span>
                                                            <span className="font-medium ml-1 text-green-600">{event.successCount}</span>
                                                        </div>
                                                        <div>
                                                            <span className="text-gray-600">Failed:</span>
                                                            <span className="font-medium ml-1 text-red-600">{event.failureCount}</span>
                                                        </div>
                                                        <div>
                                                            <span className="text-gray-600">Success Rate:</span>
                                                            <span className="font-medium ml-1">
                                                                {event.totalSent > 0 ? Math.round((event.successCount / event.totalSent) * 100) : 0}%
                                                            </span>
                                                        </div>
                                                    </div>
                                                </div>

                                                <div className="mt-2 md:mt-0 md:ml-4">
                                                    <div className="flex flex-wrap gap-1">
                                                        {Object.entries(event.regionBreakdown).map(([region, count]) => (
                                                            <span key={region} className={`px-2 py-1 rounded text-xs ${getRegionColor(region)}`}>
                                                                {region}: {count}
                                                            </span>
                                                        ))}
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </Layout>
    );
}