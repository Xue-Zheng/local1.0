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
    const [isAuthorized, setIsAuthorized] = useState(false);
    const [events, setEvents] = useState<Event[]>([]);
    const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [venueLinks, setVenueLinks] = useState<VenueLink[]>([]);

    // 表单数据
    const [venueName, setVenueName] = useState('');
    const [customVenueName, setCustomVenueName] = useState('');
    const [adminName, setAdminName] = useState('');
    const [adminEmail, setAdminEmail] = useState('');

    const [showLinkDialog, setShowLinkDialog] = useState(false);
    const [currentLink, setCurrentLink] = useState<VenueLink | null>(null);
    const [availableForums, setAvailableForums] = useState<string[]>([]);

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }
        setIsAuthorized(true);
        fetchEvents();
    }, [router]);

    // Load saved venue links and available forums when an event is selected
    useEffect(() => {
        if (selectedEventId) {
            fetchSavedVenueLinks();
            fetchAvailableForums();
        }
    }, [selectedEventId]);

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

    const fetchSavedVenueLinks = async () => {
        try {
            const response = await api.get(`/venue/checkin/saved-links/${selectedEventId}`);
            if (response.data.status === 'success') {
                setVenueLinks(response.data.data || []);
            } else {
                console.error('Failed to fetch saved venue links:', response.data.message);
            }
        } catch (error) {
            console.error('Failed to fetch saved venue links:', error);
        }
    };

    const fetchAvailableForums = async () => {
        try {
            const response = await api.get(`/venue/checkin/available-forums/${selectedEventId}`);
            if (response.data.status === 'success') {
                setAvailableForums(response.data.data || []);
            } else {
                console.error('Failed to fetch available forums:', response.data.message);
            }
        } catch (error) {
            console.error('Failed to fetch available forums:', error);
        }
    };

    const generateVenueLink = async () => {
        const finalVenueName = venueName === 'custom' ? customVenueName.trim() : venueName.trim();

        if (!selectedEventId || !finalVenueName || !adminName.trim() || !adminEmail.trim()) {
            setError('Please fill in all required fields');
            return;
        }

        try {
            setLoading(true);
            setError(null);

            const response = await api.post(`/venue/checkin/generate-venue-link/${selectedEventId}`, {
                venueName: finalVenueName,
                adminName: adminName.trim(),
                adminEmail: adminEmail.trim(),
            });

            if (response.data.status === 'success') {
                const newLink: VenueLink = response.data.data;
                setCurrentLink(newLink);
                setShowLinkDialog(true);

                // Clear form
                setVenueName('');
                setCustomVenueName('');
                setAdminName('');
                setAdminEmail('');

                // Reload saved venue links to show the new one
                fetchSavedVenueLinks();

                toast.success('Venue link generated and saved successfully');
            } else {
                setError(response.data.message || 'Failed to generate venue link');
            }
        } catch (error: any) {
            console.error('Failed to generate venue link:', error);
            setError(error.response?.data?.message || 'Failed to generate venue link');
        } finally {
            setLoading(false);
        }
    };

    const copyToClipboard = async (text: string) => {
        try {
            await navigator.clipboard.writeText(text);
            toast.success('Link copied to clipboard');
        } catch (error) {
            console.error('Failed to copy to clipboard:', error);
            toast.error('Failed to copy to clipboard');
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
                            {/* 页面头部 */}
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

                            {/* 错误提示 */}
                            {error && (
                                <div className="mb-4 bg-red-50 border border-red-200 rounded-md p-4">
                                    <div className="flex">
                                        <div className="flex-shrink-0">
                                            <svg className="h-5 w-5 text-red-400" viewBox="0 0 20 20" fill="currentColor">
                                                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                                            </svg>
                                        </div>
                                        <div className="ml-3">
                                            <p className="text-sm text-red-800">{error}</p>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* 生成链接表单 */}
                            <div className="bg-gray-50 p-6 rounded-lg mb-8">
                                <h2 className="text-lg font-medium text-gray-900 mb-4">Generate Regional Admin QR Scan Links</h2>

                                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                    {/* 活动选择 */}
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2">
                                            Select BMM Voting Event <span className="text-red-500">*</span>
                                        </label>
                                        <select
                                            value={selectedEventId || ''}
                                            onChange={(e) => setSelectedEventId(Number(e.target.value) || null)}
                                            className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                                            disabled={loading}
                                        >
                                            <option value="">Please select an event</option>
                                            {events.map((event) => (
                                                <option key={event.id} value={event.id}>
                                                    {event.name} ({event.eventCode})
                                                </option>
                                            ))}
                                        </select>
                                    </div>

                                    {/* 地区名称 */}
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2">
                                            Voting Region Name <span className="text-red-500">*</span>
                                        </label>
                                        <select
                                            value={venueName}
                                            onChange={(e) => setVenueName(e.target.value)}
                                            className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                                            disabled={loading}
                                        >
                                            <option value="">Select Forum/Venue</option>
                                            {availableForums.map((forum) => (
                                                <option key={forum} value={forum}>
                                                    {forum}
                                                </option>
                                            ))}
                                            <option value="custom">Custom Forum...</option>
                                        </select>
                                        {venueName === 'custom' && (
                                            <input
                                                type="text"
                                                placeholder="Enter custom region name"
                                                value={customVenueName}
                                                onChange={(e) => setCustomVenueName(e.target.value)}
                                                className="mt-2 w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                                            />
                                        )}
                                    </div>

                                    {/* 管理员姓名 */}
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2">
                                            Regional Admin Name <span className="text-red-500">*</span>
                                        </label>
                                        <input
                                            type="text"
                                            value={adminName}
                                            onChange={(e) => setAdminName(e.target.value)}
                                            placeholder="e.g., John Smith"
                                            className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                                            disabled={loading}
                                        />
                                    </div>

                                    {/* 管理员邮箱 */}
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2">
                                            Regional Admin Email <span className="text-red-500">*</span>
                                        </label>
                                        <input
                                            type="email"
                                            value={adminEmail}
                                            onChange={(e) => setAdminEmail(e.target.value)}
                                            placeholder="e.g., admin@etu.nz"
                                            className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                                            disabled={loading}
                                        />
                                    </div>
                                </div>

                                <div className="mt-6">
                                    <button
                                        onClick={generateVenueLink}
                                        disabled={loading || !selectedEventId || !(venueName === 'custom' ? customVenueName.trim() : venueName.trim()) || !adminName.trim() || !adminEmail.trim()}
                                        className="inline-flex items-center px-6 py-3 border border-transparent text-base font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed"
                                    >
                                        {loading ? 'Generating...' : 'Generate Password-Free QR Link'}
                                    </button>
                                </div>
                            </div>

                            {/* 已生成的链接列表 */}
                            {venueLinks.length > 0 && (
                                <div>
                                    <h2 className="text-lg font-medium text-gray-900 mb-4">
                                        Saved Regional Admin Links
                                        <span className="ml-2 text-sm text-green-600 font-normal">
                                            ✓ Saved to database
                                        </span>
                                    </h2>
                                    <div className="bg-white shadow overflow-hidden sm:rounded-md">
                                        <ul className="divide-y divide-gray-200">
                                            {venueLinks.map((link, index) => (
                                                <li key={index} className="px-6 py-4">
                                                    <div className="flex items-center justify-between">
                                                        <div className="flex-1">
                                                            <div className="flex items-center justify-between">
                                                                <h3 className="text-lg font-medium text-indigo-600">{link.venueName}</h3>
                                                                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                                                                    ♾️ Permanent
                                                                </span>
                                                            </div>
                                                            <div className="mt-2 text-sm text-gray-600">
                                                                <p><span className="font-medium">Admin:</span> {link.adminName} ({link.adminEmail})</p>
                                                                <p><span className="font-medium">Event:</span> {link.eventName}</p>
                                                                <p><span className="font-medium">Generated:</span> {formatDateTime(link.generatedAt)}</p>
                                                            </div>
                                                        </div>
                                                        <div className="ml-6 flex space-x-3">
                                                            <button
                                                                onClick={() => copyToClipboard(link.checkinUrl)}
                                                                className="inline-flex items-center px-3 py-2 border border-gray-300 shadow-sm text-sm leading-4 font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50"
                                                            >
                                                                Copy Link
                                                            </button>
                                                            <button
                                                                onClick={() => {
                                                                    setCurrentLink(link);
                                                                    setShowLinkDialog(true);
                                                                }}
                                                                className="inline-flex items-center px-3 py-2 border border-transparent text-sm leading-4 font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700"
                                                            >
                                                                View QR Code
                                                            </button>
                                                        </div>
                                                    </div>
                                                </li>
                                            ))}
                                        </ul>
                                    </div>
                                </div>
                            )}

                            {/* QR码弹窗 */}
                            {showLinkDialog && currentLink && (
                                <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full z-50">
                                    <div className="relative top-20 mx-auto p-5 border w-11/12 max-w-md shadow-lg rounded-md bg-white">
                                        <div className="mt-3">
                                            <div className="text-center">
                                                <h3 className="text-lg leading-6 font-medium text-gray-900 mb-4">
                                                    {currentLink.venueName} - QR Check-in
                                                </h3>

                                                <div className="mb-4">
                                                    <div className="inline-block p-4 bg-white border-2 border-gray-200 rounded-lg">
                                                        <QRCodeSVG
                                                            value={`${currentLink.checkinUrl}&adminName=${encodeURIComponent(currentLink.adminName)}&adminEmail=${encodeURIComponent(currentLink.adminEmail)}`}
                                                            size={200}
                                                            level="M"
                                                            includeMargin={true}
                                                        />
                                                    </div>
                                                </div>

                                                <div className="text-sm text-gray-600 dark:text-gray-400 space-y-2">
                                                    <p><strong>Admin:</strong> {currentLink.adminName}</p>
                                                    <p><strong>Email:</strong> {currentLink.adminEmail}</p>
                                                    <p><strong>Venue:</strong> {currentLink.venueName}</p>
                                                    <p><strong>Event:</strong> {currentLink.eventName}</p>
                                                </div>

                                                <div className="mt-4">
                                                    <input
                                                        type="text"
                                                        value={`${currentLink.checkinUrl}&adminName=${encodeURIComponent(currentLink.adminName)}&adminEmail=${encodeURIComponent(currentLink.adminEmail)}`}
                                                        readOnly
                                                        className="w-full p-2 text-xs border border-gray-300 dark:border-gray-600 rounded dark:bg-gray-700 dark:text-white"
                                                    />
                                                    <button
                                                        onClick={() => {
                                                            const urlWithAdmin = `${currentLink.checkinUrl}&adminName=${encodeURIComponent(currentLink.adminName)}&adminEmail=${encodeURIComponent(currentLink.adminEmail)}`;
                                                            navigator.clipboard.writeText(urlWithAdmin);
                                                            toast.success('Link copied to clipboard');
                                                        }}
                                                        className="mt-2 w-full bg-gray-600 hover:bg-gray-700 text-white px-4 py-2 rounded text-sm"
                                                    >
                                                        📋 Copy Link
                                                    </button>
                                                </div>

                                                <div className="mt-6 flex space-x-3">
                                                    <button
                                                        onClick={() => setShowLinkDialog(false)}
                                                        className="flex-1 px-4 py-2 bg-gray-200 text-gray-800 text-sm font-medium rounded-md hover:bg-gray-300"
                                                    >
                                                        Close
                                                    </button>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </Layout>
    );
}