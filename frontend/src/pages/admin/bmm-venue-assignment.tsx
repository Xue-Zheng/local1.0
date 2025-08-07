'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import api from '@/services/api';

interface Member {
    id: number;
    name: string;
    membershipNumber: string;
    email: string;
    region: string;
    bmmStage: string;
    preferredVenuesJson?: string;
    preferredDatesJson?: string;
    preferredTimesJson?: string;
    assignedVenueFinal?: string;
    assignedDatetimeFinal?: string;
    workplace?: string;
}

interface Venue {
    name: string;
    address: string;
    region: string;
    capacity: number;
    assignedCount: number;
    availableDates: string[];
    availableTimes: string[];
}

interface Assignment {
    memberId: number;
    venueName: string;
    datetime: string;
}

interface PreferenceAnalysis {
    venueName: string;
    preferenceCount: number;
    members: Member[];
}

export default function BMVenueAssignmentPage() {
    const [members, setMembers] = useState<Member[]>([]);
    const [venues, setVenues] = useState<Venue[]>([]);
    const [assignments, setAssignments] = useState<Assignment[]>([]);
    const [preferenceAnalysis, setPreferenceAnalysis] = useState<PreferenceAnalysis[]>([]);
    const [selectedRegion, setSelectedRegion] = useState<string>('all');
    const [selectedVenue, setSelectedVenue] = useState<string>('');
    const [isLoading, setIsLoading] = useState(false);
    const [isAssigning, setIsAssigning] = useState(false);

    const regions = ['Northern Region', 'Central Region', 'Southern Region'];

    const fetchData = useCallback(async () => {
        setIsLoading(true);
        try {
            const [membersResponse, venuesResponse] = await Promise.all([
                api.get('/admin/bmm/members', {
                    params: {
                        stage: 'PREFERENCE_SUBMITTED',
                        region: selectedRegion === 'all' ? undefined : selectedRegion
                    }
                }),
                api.get('/admin/bmm/venues-with-capacity')
            ]);

            if (membersResponse.data.status === 'success') {
                // Handle paginated response structure
                const memberData = membersResponse.data.data;
                const membersList = memberData.content || memberData || [];
                setMembers(membersList);
            }

            if (venuesResponse.data.status === 'success') {
                // Convert venue data from region-grouped format to flat array
                const venueData = venuesResponse.data.data || {};
                const flatVenues: Venue[] = [];

                Object.entries(venueData).forEach(([region, venueList]) => {
                    if (Array.isArray(venueList)) {
                        venueList.forEach((venue: any) => {
                            flatVenues.push({
                                name: venue.name,
                                address: venue.address || '',
                                region: region,
                                capacity: venue.capacity || 0,
                                assignedCount: venue.assigned || 0,
                                availableDates: venue.availableDates || [],
                                availableTimes: venue.availableTimes || []
                            });
                        });
                    }
                });

                setVenues(flatVenues);
            }

            // Analyze preferences
            if (membersResponse.data.status === 'success') {
                const memberData = membersResponse.data.data;
                const membersList = memberData.content || memberData || [];
                analyzePreferences(membersList);
            }
        } catch (error) {
            console.error('Error fetching data:', error);
            toast.error('Failed to load data');
        } finally {
            setIsLoading(false);
        }
    }, [selectedRegion]);

    const analyzePreferences = (membersList: Member[]) => {
        const venuePreferences: { [venueName: string]: Member[] } = {};

        membersList.forEach(member => {
            if (member.preferredVenuesJson) {
                try {
                    const preferences = JSON.parse(member.preferredVenuesJson);
                    preferences.forEach((venueName: string) => {
                        if (!venuePreferences[venueName]) {
                            venuePreferences[venueName] = [];
                        }
                        venuePreferences[venueName].push(member);
                    });
                } catch (e) {
                    console.warn('Failed to parse preferred venues for member:', member.id);
                }
            }
        });

        const analysis = Object.entries(venuePreferences).map(([venueName, members]) => ({
            venueName,
            preferenceCount: members.length,
            members
        })).sort((a, b) => b.preferenceCount - a.preferenceCount);

        setPreferenceAnalysis(analysis);
    };

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const handleAutoAssign = async () => {
        if (assignments.length > 0) {
            const confirmReplace = window.confirm('This will replace existing assignments. Continue?');
            if (!confirmReplace) return;
        }

        setIsAssigning(true);
        try {
            const response = await api.post('/admin/bmm/auto-assign-venues', {
                region: selectedRegion === 'all' ? undefined : selectedRegion
            });

            if (response.data.status === 'success') {
                toast.success('Venues assigned successfully!');
                setAssignments(response.data.assignments || []);
                await fetchData(); // Refresh data
            } else {
                toast.error(response.data.message || 'Auto-assignment failed');
            }
        } catch (error: any) {
            console.error('Error during auto-assignment:', error);
            toast.error('Auto-assignment failed');
        } finally {
            setIsAssigning(false);
        }
    };

    const handleManualAssign = async (memberId: number, venueName: string, datetime: string) => {
        try {
            const response = await api.post('/admin/bmm/manual-assign-venue', {
                memberId,
                venueName,
                datetime
            });

            if (response.data.status === 'success') {
                toast.success('Member assigned successfully!');

                // Update local assignments
                setAssignments(prev => [
                    ...prev.filter(a => a.memberId !== memberId),
                    { memberId, venueName, datetime }
                ]);

                // Update member in local state
                setMembers(prev => prev.map(member =>
                    member.id === memberId
                        ? { ...member, assignedVenueFinal: venueName, assignedDatetimeFinal: datetime, bmmStage: 'VENUE_ASSIGNED' }
                        : member
                ));
            } else {
                toast.error(response.data.message || 'Assignment failed');
            }
        } catch (error: any) {
            console.error('Error during manual assignment:', error);
            toast.error('Assignment failed');
        }
    };

    const handleBulkAssign = async () => {
        if (assignments.length === 0) {
            toast.error('No assignments to apply');
            return;
        }

        const confirmed = window.confirm(`Apply ${assignments.length} venue assignments?`);
        if (!confirmed) return;

        setIsAssigning(true);
        try {
            const response = await api.post('/admin/bmm/bulk-assign-venues', {
                assignments
            });

            if (response.data.status === 'success') {
                toast.success(`Successfully assigned ${assignments.length} members to venues!`);
                await fetchData(); // Refresh data
            } else {
                toast.error(response.data.message || 'Bulk assignment failed');
            }
        } catch (error: any) {
            console.error('Error during bulk assignment:', error);
            toast.error('Bulk assignment failed');
        } finally {
            setIsAssigning(false);
        }
    };

    const getPreferredVenues = (member: Member): string[] => {
        if (!member.preferredVenuesJson) return [];
        try {
            return JSON.parse(member.preferredVenuesJson);
        } catch {
            return [];
        }
    };

    const getAssignmentForMember = (memberId: number): Assignment | undefined => {
        return assignments.find(a => a.memberId === memberId);
    };

    const getVenueCapacityStatus = (venueName: string): { assigned: number; capacity: number; percentage: number } => {
        const venue = (venues || []).find(v => v.name === venueName);
        const assigned = assignments.filter(a => a.venueName === venueName).length;
        const capacity = venue?.capacity || 0;
        const percentage = capacity > 0 ? (assigned / capacity) * 100 : 0;
        return { assigned, capacity, percentage };
    };

    const filteredMembers = selectedVenue
        ? members.filter(member => {
            const preferences = getPreferredVenues(member);
            return preferences.includes(selectedVenue);
        })
        : members;

    if (isLoading) {
        return (
            <Layout>
                <div className="flex items-center justify-center h-96">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-purple-600"></div>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="container mx-auto px-4 py-8">
                <div className="mb-8">
                    <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-4">
                        🎯 BMM Venue Assignment
                    </h1>
                    <p className="text-gray-600 dark:text-gray-400">
                        Assign members who have submitted preferences to their final meeting venues.
                    </p>
                </div>

                {/* Controls */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 mb-6">
                    <div className="flex flex-wrap gap-4 items-center justify-between">
                        <div className="flex gap-4 items-center">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                                    Filter by Region
                                </label>
                                <select
                                    value={selectedRegion}
                                    onChange={(e) => setSelectedRegion(e.target.value)}
                                    className="border border-gray-300 dark:border-gray-600 dark:bg-gray-700 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500"
                                >
                                    <option value="all">All Regions</option>
                                    {regions.map(region => (
                                        <option key={region} value={region}>{region}</option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                                    Filter by Venue
                                </label>
                                <select
                                    value={selectedVenue}
                                    onChange={(e) => setSelectedVenue(e.target.value)}
                                    className="border border-gray-300 dark:border-gray-600 dark:bg-gray-700 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500"
                                >
                                    <option value="">All Venues</option>
                                    {(venues || [])
                                        .filter(venue => selectedRegion === 'all' || venue.region === selectedRegion)
                                        .map(venue => (
                                            <option key={venue.name} value={venue.name}>{venue.name}</option>
                                        ))
                                    }
                                </select>
                            </div>
                        </div>

                        <div className="flex gap-2">
                            <button
                                onClick={handleAutoAssign}
                                disabled={isAssigning || members.length === 0}
                                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                {isAssigning ? 'Assigning...' : '🤖 Auto Assign'}
                            </button>

                            <button
                                onClick={handleBulkAssign}
                                disabled={isAssigning || assignments.length === 0}
                                className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                ✅ Apply Assignments ({assignments.length})
                            </button>
                        </div>
                    </div>
                </div>

                {/* Statistics */}
                <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
                    <div className="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4">
                        <div className="text-2xl font-bold text-blue-600 dark:text-blue-400">
                            {members.length}
                        </div>
                        <div className="text-sm text-blue-800 dark:text-blue-300">
                            Members to Assign
                        </div>
                    </div>

                    <div className="bg-green-50 dark:bg-green-900/20 rounded-lg p-4">
                        <div className="text-2xl font-bold text-green-600 dark:text-green-400">
                            {assignments.length}
                        </div>
                        <div className="text-sm text-green-800 dark:text-green-300">
                            Pending Assignments
                        </div>
                    </div>

                    <div className="bg-purple-50 dark:bg-purple-900/20 rounded-lg p-4">
                        <div className="text-2xl font-bold text-purple-600 dark:text-purple-400">
                            {(venues || []).filter(v => selectedRegion === 'all' || v.region === selectedRegion).length}
                        </div>
                        <div className="text-sm text-purple-800 dark:text-purple-300">
                            Available Venues
                        </div>
                    </div>

                    <div className="bg-yellow-50 dark:bg-yellow-900/20 rounded-lg p-4">
                        <div className="text-2xl font-bold text-yellow-600 dark:text-yellow-400">
                            {Math.round((assignments.length / Math.max(members.length, 1)) * 100)}%
                        </div>
                        <div className="text-sm text-yellow-800 dark:text-yellow-300">
                            Assignment Progress
                        </div>
                    </div>
                </div>

                {/* Venue Capacity Overview */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 mb-6">
                    <h2 className="text-xl font-bold text-gray-900 dark:text-white mb-4">🏢 Venue Capacity Status</h2>
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        {venues
                            .filter(venue => selectedRegion === 'all' || venue.region === selectedRegion)
                            .map(venue => {
                                const status = getVenueCapacityStatus(venue.name);
                                return (
                                    <div key={venue.name} className="border border-gray-200 dark:border-gray-600 rounded-lg p-4">
                                        <div className="font-semibold text-gray-900 dark:text-white mb-2">{venue.name}</div>
                                        <div className="text-sm text-gray-600 dark:text-gray-400 mb-2">{venue.region}</div>
                                        <div className="mb-2">
                                            <div className="flex justify-between text-sm">
                                                <span>Assigned: {status.assigned}/{status.capacity}</span>
                                                <span>{status.percentage.toFixed(1)}%</span>
                                            </div>
                                            <div className="w-full bg-gray-200 rounded-full h-2">
                                                <div
                                                    className={`h-2 rounded-full ${
                                                        status.percentage > 90 ? 'bg-red-500' :
                                                            status.percentage > 75 ? 'bg-yellow-500' : 'bg-green-500'
                                                    }`}
                                                    style={{ width: `${Math.min(status.percentage, 100)}%` }}
                                                ></div>
                                            </div>
                                        </div>
                                        <div className="text-xs text-gray-500 dark:text-gray-400">
                                            Preferences: {preferenceAnalysis.find(p => p.venueName === venue.name)?.preferenceCount || 0}
                                        </div>
                                    </div>
                                );
                            })
                        }
                    </div>
                </div>

                {/* Members Table */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg overflow-hidden">
                    <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
                        <h2 className="text-xl font-bold text-gray-900 dark:text-white">
                            👥 Members Awaiting Venue Assignment ({filteredMembers.length})
                        </h2>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                            <thead className="bg-gray-50 dark:bg-gray-900">
                            <tr>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                    Member
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                    Region
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                    Preferred Venues
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                    Current Assignment
                                </th>
                                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                    Actions
                                </th>
                            </tr>
                            </thead>
                            <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                            {filteredMembers.map((member) => {
                                const preferredVenues = getPreferredVenues(member);
                                const currentAssignment = getAssignmentForMember(member.id);

                                return (
                                    <tr key={member.id}>
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div>
                                                <div className="text-sm font-medium text-gray-900 dark:text-white">
                                                    {member.name}
                                                </div>
                                                <div className="text-sm text-gray-500 dark:text-gray-400">
                                                    {member.membershipNumber}
                                                </div>
                                                {member.workplace && (
                                                    <div className="text-xs text-gray-400">
                                                        {member.workplace}
                                                    </div>
                                                )}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-white">
                                            {member.region}
                                        </td>
                                        <td className="px-6 py-4">
                                            <div className="space-y-1">
                                                {preferredVenues.map((venue, index) => (
                                                    <div key={index} className="text-sm">
                                                            <span className="text-blue-600 dark:text-blue-400">
                                                                #{index + 1}
                                                            </span>
                                                        <span className="ml-2 text-gray-900 dark:text-white">
                                                                {venue}
                                                            </span>
                                                    </div>
                                                ))}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            {currentAssignment ? (
                                                <div className="text-sm">
                                                    <div className="font-medium text-green-600 dark:text-green-400">
                                                        {currentAssignment.venueName}
                                                    </div>
                                                    <div className="text-gray-500 dark:text-gray-400">
                                                        {new Date(currentAssignment.datetime).toLocaleString()}
                                                    </div>
                                                </div>
                                            ) : member.assignedVenueFinal ? (
                                                <div className="text-sm">
                                                    <div className="font-medium text-blue-600 dark:text-blue-400">
                                                        {member.assignedVenueFinal}
                                                    </div>
                                                    <div className="text-gray-500 dark:text-gray-400">
                                                        Already assigned
                                                    </div>
                                                </div>
                                            ) : (
                                                <span className="text-gray-400 text-sm">Not assigned</span>
                                            )}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm">
                                            {!member.assignedVenueFinal && (
                                                <select
                                                    onChange={(e) => {
                                                        if (e.target.value) {
                                                            const [venueName, datetime] = e.target.value.split('|');
                                                            handleManualAssign(member.id, venueName, datetime);
                                                        }
                                                    }}
                                                    className="border border-gray-300 dark:border-gray-600 dark:bg-gray-700 rounded px-2 py-1 text-xs"
                                                >
                                                    <option value="">Assign to...</option>
                                                    {venues
                                                        .filter(venue => venue.region === member.region)
                                                        .map(venue =>
                                                            venue.availableDates.flatMap(date =>
                                                                venue.availableTimes.map(time => {
                                                                    const datetime = `${date}T${time}`;
                                                                    const capacity = getVenueCapacityStatus(venue.name);
                                                                    const isPreferred = preferredVenues.includes(venue.name);
                                                                    return (
                                                                        <option
                                                                            key={`${venue.name}-${datetime}`}
                                                                            value={`${venue.name}|${datetime}`}
                                                                            disabled={capacity.assigned >= capacity.capacity}
                                                                        >
                                                                            {isPreferred ? '⭐ ' : ''}{venue.name} - {date} {time}
                                                                            {capacity.assigned >= capacity.capacity ? ' (FULL)' : ''}
                                                                        </option>
                                                                    );
                                                                })
                                                            )
                                                        )
                                                    }
                                                </select>
                                            )}
                                        </td>
                                    </tr>
                                );
                            })}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </Layout>
    );
}