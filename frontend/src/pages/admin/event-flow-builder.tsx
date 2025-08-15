import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import Layout from '../../components/common/Layout';
import { QRCodeSVG } from 'qrcode.react';
import { toast } from 'react-toastify';
import api from '@/services/api';

interface Event {
    id: number;
    name: string;
    eventCode: string;
    venue: string;
    description: string;
    eventDate: string;
    isActive: boolean;
    eventType: string;
}

interface VenueLink {
    checkinUrl: string;
    qrCodeData: string;
    adminToken: string;
    venueName: string;
    eventName: string;
    adminName: string;
    adminEmail: string;
    generatedAt: string;
}

export default function VenueLinksPage() {
    const router = useRouter();
    const [events, setEvents] = useState<Event[]>([]);
    const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [venueLinks, setVenueLinks] = useState<VenueLink[]>([]);

// Form data
    const [venueName, setVenueName] = useState('');
    const [adminName, setAdminName] = useState('');
    const [adminEmail, setAdminEmail] = useState('');

    const [showLinkDialog, setShowLinkDialog] = useState(false);
    const [currentLink, setCurrentLink] = useState<VenueLink | null>(null);

    const [generating, setGenerating] = useState(false);
    const [bmmRegionLinks, setBmmRegionLinks] = useState<VenueLink[]>([]);
    const [showBMMLinksModal, setShowBMMLinksModal] = useState(false);

// Predefined BMM voting regions
    const predefinedVenues = [
        'Northern Region',
        'Central Region',
        'Southern Region'
    ];

    useEffect(() => {
        fetchEvents();
    }, []);

    const fetchEvents = async () => {
        try {
            const response = await api.get('/admin/events');
            if (response.data.status === 'success') {
                setEvents(response.data.data?.filter((event: Event) => event.isActive) || []);
            } else {
                setError('Failed to fetch events');
            }
        } catch (error) {
            console.error('Failed to fetch events:', error);
            setError('Failed to fetch events');
        }
    };

    const generateVenueLink = async () => {
        if (!selectedEventId || !venueName.trim() || !adminName.trim() || !adminEmail.trim()) {
            toast.error('Please fill in all required fields');
            return;
        }

        try {
            setGenerating(true);
            const response = await api.post(`/venue/checkin/generate-venue-link/${selectedEventId}`, {
                venueName,
                adminName,
                adminEmail
            });

            if (response.data.status === 'success') {
                const newLink: VenueLink = response.data.data;
                setVenueLinks(prev => [...prev, newLink]);
                setCurrentLink(newLink);
                setShowLinkDialog(true);
                toast.success('QR scan link generated successfully!');

// Clear form
                setVenueName('');
                setAdminName('');
                setAdminEmail('');
            }
        } catch (error: any) {
            console.error('Failed to generate venue link', error);
            toast.error(error.response?.data?.message || 'Failed to generate venue link');
        } finally {
            setGenerating(false);
        }
    };

    const generateAllBMMLinks = async () => {
        if (!selectedEventId) {
            toast.error('Please select a BMM event first');
            return;
        }

        try {
            setGenerating(true);
            const response = await api.post(`/venue/checkin/generate-all-bmm-links/${selectedEventId}`, {
                generatedBy: 'admin',
                timestamp: new Date().toISOString()
            });

            if (response.data.status === 'success') {
                setBmmRegionLinks(response.data.data.regions);
                setShowBMMLinksModal(true);
                toast.success('All BMM region links generated successfully!');
            }
        } catch (error: any) {
            console.error('Failed to generate BMM links', error);
            toast.error(error.response?.data?.message || 'Failed to generate BMM links');
        } finally {
            setGenerating(false);
        }
    };

    const copyToClipboard = async (text: string) => {
        try {
            await navigator.clipboard.writeText(text);
            alert('Link copied to clipboard!');
        } catch (error) {
            console.error('Failed to copy to clipboard:', error);
            alert('Failed to copy to clipboard');
        }
    };

    const formatDateTime = (dateTime: string) => {
        return new Date(dateTime).toLocaleString('en-NZ', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        });
    };

    return (
        <Layout>
            <div className="min-h-screen bg-gray-50 dark:bg-gray-900 py-6">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="bg-white dark:bg-gray-800 shadow rounded-lg">
                        <div className="px-4 py-5 sm:p-6">
                            {/* Page Header */}
                            <div className="flex justify-between items-center mb-6">
                                <div>
                                    <h1 className="text-3xl font-bold text-gray-900 dark:text-white">Multi-Venue BMM Voting Management</h1>
                                    <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
                                        Generate password-free QR scan check-in links for admins in different regions, supporting simultaneous BMM voting in five regions
                                    </p>
                                </div>
                                <button
                                    onClick={() => router.push('/admin/dashboard')}
                                    className="inline-flex items-center px-4 py-2 border border-gray-300 dark:border-gray-600 shadow-sm text-sm font-medium rounded-md text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700"
                                >
                                    Back to Dashboard
                                </button>
                            </div>

                            {/* Error Alert */}
                            {error && (
                                <div className="mb-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-md p-4">
                                    <div className="flex">
                                        <div className="flex-shrink-0">
                                            <svg className="h-5 w-5 text-red-400" viewBox="0 0 20 20" fill="currentColor">
                                                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                                            </svg>
                                        </div>
                                        <div className="ml-3">
                                            <p className="text-sm text-red-800 dark:text-red-200">{error}</p>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Generate Link Form */}
                            <div className="bg-gray-50 dark:bg-gray-700 p-6 rounded-lg mb-8">
                                <h2 className="text-lg font-medium text-gray-900 dark:text-white mb-4">Enhanced BMM Multi-Region Venue Management</h2>

                                <div className="space-y-6">
                                    <div className="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg p-4">
                                        <div className="flex items-start">
                                            <div className="flex-shrink-0">
                                                <svg className="h-5 w-5 text-blue-400" fill="currentColor" viewBox="0 0 20 20">
                                                    <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                                                </svg>
                                            </div>
                                            <div className="ml-3">
                                                <h3 className="text-sm font-medium text-blue-800 dark:text-blue-200">Enhanced BMM Multi-Region Venue Management</h3>
                                                <div className="mt-2 text-sm text-blue-700 dark:text-blue-300">
                                                    <ul className="list-disc list-inside space-y-1">
                                                        <li>BMM events support all 3 regions: Northern, Central, Southern</li>
                                                        <li>Generate individual venue links or bulk generate for all regions</li>
                                                        <li>Each link includes region-specific statistics and validation</li>
                                                        <li>Cross-region check-ins are logged but allowed for flexibility</li>
                                                        <li>Real-time regional attendance tracking and statistics</li>
                                                        <li>Enhanced QR codes with venue and timestamp validation</li>
                                                    </ul>
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    {/* BMM Quick Setup Section */}
                                    {selectedEventId && events.find(e => e.id === selectedEventId)?.eventType === 'BMM_VOTING' && (
                                        <div className="bg-purple-50 dark:bg-purple-900/30 border border-purple-200 dark:border-purple-700 rounded-lg p-6">
                                            <h3 className="text-lg font-medium text-purple-900 dark:text-purple-100 mb-4">
                                                🗳️ BMM Quick Setup
                                            </h3>
                                            <div className="space-y-4">
                                                <button
                                                    onClick={generateAllBMMLinks}
                                                    disabled={generating}
                                                    className="w-full bg-purple-600 hover:bg-purple-700 text-white font-medium py-3 px-4 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed"
                                                >
                                                    {generating ? 'Generating...' : 'Generate All 3 BMM Region Links'}
                                                </button>
                                                <p className="text-sm text-purple-700 dark:text-purple-300">
                                                    This will generate password-free QR scan links for all 3 BMM regions: Northern, Central, Southern.
                                                </p>
                                            </div>
                                        </div>
                                    )}

                                    {/* Individual Venue Setup */}
                                    <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
                                        <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-4">Individual Venue Setup</h3>

                                        <div className="grid grid-cols-1 gap-4">
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                    Select Event <span className="text-red-500">*</span>
                                                </label>
                                                <select
                                                    value={selectedEventId || ''}
                                                    onChange={(e) => setSelectedEventId(Number(e.target.value) || null)}
                                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-800 dark:text-white"
                                                    disabled={loading}
                                                >
                                                    <option value="">Please select an event</option>
                                                    {events.map((event) => (
                                                        <option key={event.id} value={event.id}>
                                                            {event.name} ({event.eventCode}) - {event.eventType}
                                                        </option>
                                                    ))}
                                                </select>
                                            </div>

                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                    Venue/Location
                                                </label>
                                                <input
                                                    type="text"
                                                    value={venueName}
                                                    onChange={(e) => setVenueName(e.target.value)}
                                                    placeholder="Enter venue name"
                                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                                />
                                            </div>

                                            {/* Admin Name */}
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                    Regional Admin Name <span className="text-red-500">*</span>
                                                </label>
                                                <input
                                                    type="text"
                                                    value={adminName}
                                                    onChange={(e) => setAdminName(e.target.value)}
                                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-800 dark:text-white"
                                                    placeholder="Enter admin name"
                                                    disabled={generating}
                                                />
                                            </div>

                                            {/* Admin Email */}
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                    Regional Admin Email <span className="text-red-500">*</span>
                                                </label>
                                                <input
                                                    type="primaryEmail"
                                                    value={adminEmail}
                                                    onChange={(e) => setAdminEmail(e.target.value)}
                                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-800 dark:text-white"
                                                    placeholder="Enter admin email"
                                                    disabled={generating}
                                                />
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <div className="mt-6">
                                    <button
                                        onClick={generateVenueLink}
                                        disabled={generating || !selectedEventId || !venueName.trim() || !adminName.trim() || !adminEmail.trim()}
                                        className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:bg-gray-400 disabled:cursor-not-allowed"
                                    >
                                        {generating ? (
                                            <>
                                                <div className="animate-spin rounded-full h-4 w-4 border-t-2 border-b-2 border-white mr-2"></div>
                                                Generating...
                                            </>
                                        ) : (
                                            <>
                                                <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" />
                                                </svg>
                                                Generate QR Scan Link
                                            </>
                                        )}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                {/* QR Code Display Modal */}
                {showLinkDialog && currentLink && (
                    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                        <div className="bg-white dark:bg-gray-800 rounded-lg max-w-md w-full">
                            <div className="p-6">
                                <div className="flex justify-between items-center mb-4">
                                    <h2 className="text-xl font-bold text-gray-900 dark:text-white">Venue Check-in Link</h2>
                                    <button
                                        onClick={() => {
                                            setShowLinkDialog(false);
                                            setCurrentLink(null);
                                        }}
                                        className="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300"
                                    >
                                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                        </svg>
                                    </button>
                                </div>

                                <div className="text-center">
                                    <div className="mb-4">
                                        <h3 className="text-lg font-medium text-gray-900 dark:text-white">{currentLink.venueName}</h3>
                                        <p className="text-sm text-gray-500 dark:text-gray-400">{currentLink.adminName} ({currentLink.adminEmail})</p>
                                    </div>

                                    <div className="bg-white p-4 rounded-lg inline-block mb-4">
                                        <QRCodeSVG
                                            value={currentLink.checkinUrl}
                                            size={200}
                                            level="M"
                                            includeMargin={true}
                                        />
                                    </div>

                                    <div className="mb-4">
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                            Check-in URL
                                        </label>
                                        <div className="flex">
                                            <input
                                                type="text"
                                                value={currentLink.checkinUrl}
                                                readOnly
                                                className="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-l-md bg-gray-50 dark:bg-gray-700 text-gray-900 dark:text-white text-sm"
                                            />
                                            <button
                                                onClick={() => copyToClipboard(currentLink.checkinUrl)}
                                                className="px-3 py-2 border border-l-0 border-gray-300 dark:border-gray-600 rounded-r-md bg-gray-50 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-600"
                                            >
                                                Copy
                                            </button>
                                        </div>
                                    </div>

                                    <div className="text-xs text-gray-500 dark:text-gray-400 mb-4">
                                        <p>Generated: {formatDateTime(currentLink.generatedAt)}</p>
                                    </div>

                                    <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-3">
                                        <p className="text-xs text-yellow-800 dark:text-yellow-200">
                                            <strong>Security Note:</strong> This link provides direct access to check-in functionality.
                                            Share only with authorized regional administrators.
                                        </p>
                                    </div>
                                </div>

                                <div className="flex justify-end mt-6">
                                    <button
                                        onClick={() => {
                                            setShowLinkDialog(false);
                                            setCurrentLink(null);
                                        }}
                                        className="px-4 py-2 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-md hover:bg-gray-200 dark:hover:bg-gray-600"
                                    >
                                        Close
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </Layout>
    );
}