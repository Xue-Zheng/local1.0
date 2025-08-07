import React, { useState, useEffect } from 'react';
import api from '@/services/api';

interface Venue {
    id: number;
    name: string;
    venue: string;
    address: string;
    date: string;
    capacity?: number;
    region?: string;
}

interface VenueSelectorProps {
    selectedVenues?: string[];
    onVenueChange?: (venues: string[]) => void;
    memberRegion?: string;
    mode?: 'preference' | 'assignment';
}

export default function VenueSelector({
                                          selectedVenues = [],
                                          onVenueChange,
                                          memberRegion,
                                          mode = 'preference'
                                      }: VenueSelectorProps) {
    const [venues, setVenues] = useState<Venue[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [regionSummary, setRegionSummary] = useState<any>(null);

    useEffect(() => {
        fetchVenues();
    }, [memberRegion]);

    const fetchVenues = async () => {
        try {
            setLoading(true);
            setError(null);

            // Get venue configuration
            const configResponse = await api.get('/event-registration/venues/bmm-config');
            if (configResponse.data.status === 'success') {
                const config = configResponse.data.data;
                setRegionSummary(config.regions);

                if (memberRegion && mode === 'preference') {
                    // Show only venues for member's region
                    const regionVenues = config.regions[memberRegion]?.venues || [];
                    setVenues(regionVenues.map((v: any) => ({ ...v, region: memberRegion })));
                } else {
                    // Show all venues for admin assignment
                    setVenues(config.allVenues || []);
                }
            }
        } catch (err: any) {
            console.error('Failed to fetch venues:', err);
            setError('Failed to load venue information');
        } finally {
            setLoading(false);
        }
    };

    const handleVenueToggle = (venueName: string) => {
        if (!onVenueChange) return;

        const newSelection = selectedVenues.includes(venueName)
            ? selectedVenues.filter(v => v !== venueName)
            : [...selectedVenues, venueName];

        onVenueChange(newSelection);
    };

    const getRegionColor = (region: string) => {
        if (!regionSummary) return 'bg-gray-100';

        const colorMap: { [key: string]: string } = {
            'blue': 'bg-blue-100 border-blue-300',
            'green': 'bg-green-100 border-green-300',
            'pink': 'bg-pink-100 border-pink-300'
        };

        const regionData = regionSummary[region];
        const color = regionData?.color || 'gray';
        return colorMap[color] || 'bg-gray-100';
    };

    if (loading) {
        return (
            <div className="animate-pulse">
                <div className="h-4 bg-gray-200 rounded mb-4"></div>
                <div className="space-y-2">
                    {[1, 2, 3].map(i => (
                        <div key={i} className="h-16 bg-gray-200 rounded"></div>
                    ))}
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4">
                <div className="text-red-800 font-medium">Error loading venues</div>
                <div className="text-red-600 text-sm mt-1">{error}</div>
                <button
                    onClick={fetchVenues}
                    className="mt-2 px-3 py-1 bg-red-100 hover:bg-red-200 text-red-800 rounded text-sm"
                >
                    Retry
                </button>
            </div>
        );
    }

    const groupedVenues = venues.reduce((acc, venue) => {
        const region = venue.region || memberRegion || 'Unknown';
        if (!acc[region]) acc[region] = [];
        acc[region].push(venue);
        return acc;
    }, {} as { [key: string]: Venue[] });

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <h3 className="text-lg font-semibold text-gray-900">
                    {mode === 'preference' ? 'Select Your Preferred Venues' : 'Available BMM Venues'}
                </h3>
                <span className="text-sm text-gray-500">
          {venues.length} venue{venues.length !== 1 ? 's' : ''} available
        </span>
            </div>

            {mode === 'preference' && memberRegion && (
                <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                    <div className="flex items-center">
                        <div className="text-blue-600 mr-2">📍</div>
                        <div className="text-blue-800">
                            <div className="font-medium">Your Region: {memberRegion}</div>
                            <div className="text-sm text-blue-600">
                                Select up to 3 preferred venues in your region for BMM attendance
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {Object.entries(groupedVenues).map(([region, regionVenues]) => (
                <div key={region} className="space-y-3">
                    <div className="flex items-center space-x-2">
                        <h4 className="font-medium text-gray-800">{region}</h4>
                        <span className="px-2 py-1 bg-gray-100 text-gray-600 rounded text-xs">
              {regionVenues.length} venues
            </span>
                        {regionSummary && regionSummary[region] && (
                            <div className={`w-3 h-3 rounded-full ${getRegionColor(region).split(' ')[0]}`}></div>
                        )}
                    </div>

                    <div className="grid gap-3">
                        {regionVenues.map((venue) => (
                            <label
                                key={venue.id}
                                className={`cursor-pointer border rounded-lg p-4 transition-all hover:shadow-md ${
                                    selectedVenues.includes(venue.name)
                                        ? 'border-blue-500 bg-blue-50'
                                        : 'border-gray-200 hover:border-gray-300'
                                } ${getRegionColor(region)}`}
                            >
                                <div className="flex items-start space-x-3">
                                    <input
                                        type="checkbox"
                                        checked={selectedVenues.includes(venue.name)}
                                        onChange={() => handleVenueToggle(venue.name)}
                                        className="mt-1 h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
                                    />
                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-center justify-between">
                                            <h5 className="font-medium text-gray-900">{venue.name}</h5>
                                            {venue.capacity && (
                                                <span className="text-sm text-gray-500">
                          Capacity: {venue.capacity}
                        </span>
                                            )}
                                        </div>
                                        <div className="text-sm text-gray-700 mt-1">{venue.venue}</div>
                                        <div className="text-sm text-gray-600 mt-1">📍 {venue.address}</div>
                                        <div className="text-sm text-blue-600 mt-1 font-medium">📅 {venue.date}</div>
                                    </div>
                                </div>
                            </label>
                        ))}
                    </div>
                </div>
            ))}

            {venues.length === 0 && (
                <div className="text-center py-8 text-gray-500">
                    <div className="text-4xl mb-2">🏢</div>
                    <div className="text-lg font-medium">No venues available</div>
                    <div className="text-sm">Venue information will be updated soon</div>
                </div>
            )}

            {mode === 'preference' && selectedVenues.length > 0 && (
                <div className="bg-green-50 border border-green-200 rounded-lg p-4">
                    <div className="text-green-800 font-medium">Selected Venues ({selectedVenues.length})</div>
                    <div className="text-green-700 text-sm mt-1">
                        {selectedVenues.join(', ')}
                    </div>
                </div>
            )}
        </div>
    );
}