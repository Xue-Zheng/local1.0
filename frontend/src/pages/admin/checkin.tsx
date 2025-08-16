'use client';
import React, { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import Link from 'next/link';
import api from '@/services/api';
import { Html5QrcodeScanner, Html5QrcodeScanType, Html5Qrcode } from 'html5-qrcode';

interface CheckinMember {
    id: number;
    membershipNumber: string;
    name: string;
    primaryEmail: string;
    telephoneMobile: string;
    regionDesc: string;
    siteSubIndustryDesc: string;
    employerName: string;
    isAttending: boolean;
    checkinTime: string;
    token: string;
    qrCodeEmailSent: boolean;
    hasVoted: boolean;
    forumDesc?: string;
    assignedVenueFinal?: string;
    // 🔥 新增：Checkin追踪字段
    checkedIn?: boolean;
    checkInTime?: string;
    checkInLocation?: string;
    checkInAdminId?: number;
    checkInAdminUsername?: string;
    checkInAdminName?: string;
    checkInMethod?: string;
    checkInVenue?: string;
}

interface Event {
    id: number;
    name: string;
    eventCode: string;
    eventType: string;
    venue: string;
    eventDate: string;
    qrScanEnabled: boolean;
    maxAttendees: number;
}

export default function CheckinPage() {
    const router = useRouter();
    const [isAuthorized, setIsAuthorized] = useState(false);
    const [loading, setLoading] = useState(true);
    const [events, setEvents] = useState<Event[]>([]);
    const [selectedEvent, setSelectedEvent] = useState<Event | null>(null);
    const [members, setMembers] = useState<CheckinMember[]>([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [filterStatus, setFilterStatus] = useState('all');
    const [filterRegion, setFilterRegion] = useState('all');
    const [showQRModal, setShowQRModal] = useState(false);
    const [showManualCheckinModal, setShowManualCheckinModal] = useState(false);
    const [selectedMember, setSelectedMember] = useState<CheckinMember | null>(null);
    const [qrScannerActive, setQrScannerActive] = useState(false);
    const [scanMode, setScanMode] = useState<'camera' | 'upload'>('camera');
    const [cameraPermissionDenied, setCameraPermissionDenied] = useState(false);
    const [lastScannedToken, setLastScannedToken] = useState<string | null>(null);
    const [recentScans, setRecentScans] = useState<Set<string>>(new Set());
    const [checkinStats, setCheckinStats] = useState({
        totalMembers: 0,
        checkedIn: 0,
        attending: 0,
        byRegion: {} as {[key: string]: number},
        byMethod: {} as {[key: string]: number}
    });
    const [scannerInput, setScannerInput] = useState('');
    const [isProcessingScanner, setIsProcessingScanner] = useState(false);

    const qrScannerRef = useRef<Html5QrcodeScanner | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const scannerInputRef = useRef<string>('');
    const scannerTimeoutRef = useRef<NodeJS.Timeout | null>(null);

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }
        setIsAuthorized(true);
        fetchEvents();
    }, [router]);

    // 单独处理eventId参数
    useEffect(() => {
        if (!router.isReady) return;

        const { eventId } = router.query;
        if (eventId && events.length > 0) {
            const event = events.find(e => e.id === parseInt(eventId as string));
            if (event) {
                setSelectedEvent(event);
            }
        }
    }, [router.isReady, router.query.eventId, events]);

    // Clean up scanner on unmount
    useEffect(() => {
        return () => {
            if (qrScannerRef.current) {
                try {
                    qrScannerRef.current.clear();
                } catch (e) {
                    console.log('Cleanup error on unmount:', e);
                }
            }
            if (scannerTimeoutRef.current) {
                clearTimeout(scannerTimeoutRef.current);
            }
        };
    }, []);

    useEffect(() => {
        if (selectedEvent) {
            // 🔧 Performance Fix: Parallel API calls instead of sequential
            Promise.all([
                fetchEventMembers(selectedEvent.id),
                fetchCheckinStats(selectedEvent.id)
            ]).catch(error => {
                console.error('Failed to load event data:', error);
                toast.error('Failed to load event data');
            });
        }
    }, [selectedEvent]);

    useEffect(() => {
        return () => {
            if (qrScannerRef.current) {
                qrScannerRef.current.clear();
            }
        };
    }, []);

    // Handle barcode scanner input
    useEffect(() => {
        const handleBarcodeScannerInput = (event: KeyboardEvent) => {
            // Skip if modal is open or input is focused
            if (showQRModal || showManualCheckinModal) return;

            const activeElement = document.activeElement as HTMLElement;
            if (activeElement && (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA' || activeElement.tagName === 'SELECT')) {
                return;
            }

            console.log('Scanner input detected:', event.key, 'Current buffer:', scannerInputRef.current);

            // Barcode scanners typically send characters very quickly followed by Enter
            if (event.key === 'Enter' && scannerInputRef.current.length > 0) {
                event.preventDefault();
                const scannedData = scannerInputRef.current.trim();
                console.log('Processing scanned data:', scannedData);
                console.log('Selected event:', selectedEvent);

                scannerInputRef.current = '';
                setScannerInput('');

                // Clear any existing timeout
                if (scannerTimeoutRef.current) {
                    clearTimeout(scannerTimeoutRef.current);
                }

                // Validate scanned data
                if (!scannedData || scannedData.length < 3) {
                    toast.error('Invalid QR code data - too short');
                    return;
                }

                // Process the scanned data
                if (!isProcessingScanner && selectedEvent) {
                    console.log('Starting QR scan processing...');
                    setIsProcessingScanner(true);
                    handleQRScanResult(scannedData).finally(() => {
                        console.log('QR scan processing completed');
                        setIsProcessingScanner(false);
                    });
                } else {
                    if (!selectedEvent) {
                        console.error('No event selected!');
                        toast.error('Please select an event first before scanning');
                    }
                    if (isProcessingScanner) {
                        console.log('Still processing previous scan');
                        toast.warning('Still processing previous scan, please wait...');
                    }
                }
            } else if (event.key.length === 1 && event.key.match(/[\w\-{}":,\s]/)) {
                // Only accumulate valid characters (alphanumeric, JSON characters, etc.)
                scannerInputRef.current += event.key;
                setScannerInput(scannerInputRef.current);

                // Clear accumulated input after 200ms of no activity (increased from 100ms)
                if (scannerTimeoutRef.current) {
                    clearTimeout(scannerTimeoutRef.current);
                }
                scannerTimeoutRef.current = setTimeout(() => {
                    console.log('Clearing scanner buffer after timeout:', scannerInputRef.current);
                    scannerInputRef.current = '';
                    setScannerInput('');
                }, 200);
            }
        };

        // Add event listener for both keypress and keydown to catch all scanner inputs
        window.addEventListener('keypress', handleBarcodeScannerInput);
        window.addEventListener('keydown', (event) => {
            // Handle special keys that might come from scanner
            if (event.key === 'Enter' && scannerInputRef.current.length > 0) {
                handleBarcodeScannerInput(event as KeyboardEvent);
            }
        });

        return () => {
            window.removeEventListener('keypress', handleBarcodeScannerInput);
            window.removeEventListener('keydown', handleBarcodeScannerInput);
            if (scannerTimeoutRef.current) {
                clearTimeout(scannerTimeoutRef.current);
            }
        };
    }, [showQRModal, showManualCheckinModal, selectedEvent, isProcessingScanner]);

    const fetchEvents = async () => {
        try {
            setLoading(true);
            const response = await api.get('/admin/events');
            if (response.data.status === 'success') {
                setEvents(response.data.data);
                if (response.data.data.length > 0) {
                    setSelectedEvent(response.data.data[0]);
                }
            }
        } catch (error) {
            console.error('Failed to fetch events', error);
            toast.error('Failed to load events');
        } finally {
            setLoading(false);
        }
    };

    const fetchEventMembers = async (eventId: number) => {
        try {
            // 优化：只获取已注册且准备参会的成员用于签到，减少数据量
            const response = await api.post(`/admin/registration/events/${eventId}/members-by-criteria?size=10000`, {
                registrationStatus: 'attending'  // 只获取准备参会的成员
            });
            if (response.data.status === 'success') {
                const membersData = response.data.data.members || [];
                // Debug: Log first checked-in member to see data structure
                const checkedInMember = membersData.find((m: any) => m.checkedIn || m.checkInTime);
                if (checkedInMember) {
                    console.log('Checked-in member data structure:', checkedInMember);
                }
                setMembers(membersData);
            }
        } catch (error) {
            console.error('Failed to fetch event members', error);
            toast.error('Failed to load event members');
        }
    };

    const fetchCheckinStats = async (eventId: number) => {
        try {
            const response = await api.get(`/admin/events/${eventId}/checkin/stats`);
            if (response.data.status === 'success') {
                setCheckinStats(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch checkin stats', error);
        }
    };

    const handleManualCheckin = async (membershipNumber: string, location: string, venue?: string) => {
        if (!selectedEvent) {
            toast.error('Please select an event first');
            return;
        }
        try {
            const response = await api.post('/admin/checkin/manual', {
                membershipNumber,
                eventId: selectedEvent.id,
                location,
                venue: venue || location
            });
            if (response.data.status === 'success') {
                console.log('Manual checkin response:', response.data);
                toast.success('Member checked in successfully');
                // 🔧 Performance Fix: Parallel refresh after checkin
                await Promise.all([
                    fetchEventMembers(selectedEvent!.id),
                    fetchCheckinStats(selectedEvent!.id)
                ]);
                setShowManualCheckinModal(false);
                setSelectedMember(null);
            }
        } catch (error) {
            console.error('Failed to check in member', error);
            toast.error('Failed to check in member');
        }
    };

    const generateQRCode = async (eventId: number) => {
        try {
            toast.info('Generating QR codes...');
            const response = await api.post(`/admin/events/${eventId}/qr/generate`);
            if (response.data.status === 'success') {
                toast.success(`QR codes generated! Generated: ${response.data.data.newTokensGenerated}, Existing: ${response.data.data.existingTokens}`);
                // 🔧 Performance Fix: Only refresh member list after QR generation
                fetchEventMembers(eventId);
            } else {
                toast.error(response.data.message || 'Failed to generate QR codes');
            }
        } catch (error: any) {
            console.error('Failed to generate QR code', error);
            toast.error(error.response?.data?.message || 'Failed to generate QR codes');
        }
    };

    const sendQRCodes = async (eventId: number) => {
        try {
            toast.info('Sending QR codes...');
            const response = await api.post(`/admin/events/${eventId}/qr/send`);
            if (response.data.status === 'success') {
                toast.success(`QR codes sent to ${response.data.data.sentCount} members`);
                // 🔧 Performance Fix: Only refresh member list after QR sending
                fetchEventMembers(eventId);
            } else {
                toast.error(response.data.message || 'Failed to send QR codes');
            }
        } catch (error: any) {
            console.error('Failed to send QR codes', error);
            toast.error(error.response?.data?.message || 'Failed to send QR codes');
        }
    };

    const resetCameraPermission = async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ video: true });
            stream.getTracks().forEach(track => track.stop());
            setCameraPermissionDenied(false);
            toast.success('Camera permission granted, ready to scan');
        } catch (error) {
            console.error('Camera permission still denied:', error);
            toast.error('Camera permission still denied. Please allow camera access in browser settings');
        }
    };

    const startQRScanner = async () => {
        // Prevent multiple clicks
        if (qrScannerActive) {
            console.log('Scanner already starting/active');
            return;
        }

        try {
            // Clean up any existing scanner first
            if (qrScannerRef.current) {
                try {
                    qrScannerRef.current.clear();
                } catch (e) {
                    console.log('Scanner cleanup error:', e);
                }
                qrScannerRef.current = null;
            }

            // Set scanner active first to render the qr-reader element
            setQrScannerActive(true);

            // Wait for DOM update with requestAnimationFrame for better reliability
            await new Promise(resolve => {
                requestAnimationFrame(() => {
                    setTimeout(resolve, 100);
                });
            });

            // Ensure the element exists
            const element = document.getElementById("qr-reader");
            if (!element) {
                console.error('QR reader element not found');
                setQrScannerActive(false);
                return;
            }

            const stream = await navigator.mediaDevices.getUserMedia({ video: true });
            stream.getTracks().forEach(track => track.stop());

            const config = {
                fps: 30,
                qrbox: { width: 300, height: 300 },
                supportedScanTypes: [Html5QrcodeScanType.SCAN_TYPE_CAMERA],
                aspectRatio: 1.0,
                showTorchButtonIfSupported: true,
                showZoomSliderIfSupported: true,
                experimentalFeatures: {
                    useBarCodeDetectorIfSupported: true
                },
                videoConstraints: {
                    facingMode: "environment",
                    width: { ideal: 1920 },
                    height: { ideal: 1080 },
                    frameRate: { ideal: 30, min: 15 }
                },
                rememberLastUsedCamera: true,
                defaultZoomValueIfSupported: 1.0
            };

            const scanner = new Html5QrcodeScanner("qr-reader", config, false);
            qrScannerRef.current = scanner;

            scanner.render(
                (decodedText, decodedResult) => {
                    console.log(`QR Code detected: ${decodedText}`);

                    if (recentScans.has(decodedText)) {
                        toast.warning('This QR code was just scanned, please try again later');
                        return;
                    }

                    setRecentScans(prev => new Set(prev.add(decodedText)));
                    setTimeout(() => {
                        setRecentScans(prev => {
                            const newSet = new Set(prev);
                            newSet.delete(decodedText);
                            return newSet;
                        });
                    }, 5000);

                    handleQRScanResult(decodedText);
                    scanner.clear();
                    setQrScannerActive(false);
                },
                (errorMessage) => {
                    if (!errorMessage.includes('No MultiFormat Readers') &&
                        !errorMessage.includes('NotFoundException') &&
                        !errorMessage.includes('No QR code found')) {
                        console.warn(`QR Code scan error: ${errorMessage}`);
                    }
                }
            );

            // State already set at the beginning of the function
            setCameraPermissionDenied(false);
            setRecentScans(new Set());

        } catch (error: any) {
            console.error('Camera access failed:', error);
            setCameraPermissionDenied(true);

            if (error.name === 'NotAllowedError') {
                toast.error('Camera permission denied. Click the lock icon in the address bar, allow camera access, then refresh the page.');
            } else if (error.name === 'NotFoundError') {
                toast.error('No camera device found. Please connect a camera and try again.');
            } else {
                toast.error('Unable to access camera. Please check permissions or use image upload scanning.');
            }
        }
    };

    const stopQRScanner = () => {
        if (qrScannerRef.current) {
            qrScannerRef.current.clear();
            setQrScannerActive(false);
        }
    };

    const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) return;

        // Reset file input immediately
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
        }

        // Generate unique ID to avoid conflicts
        const scannerId = `qr-reader-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        let tempDiv: HTMLDivElement | null = null;

        try {
            // Create temporary div
            tempDiv = document.createElement('div');
            tempDiv.id = scannerId;
            tempDiv.style.display = 'none';
            document.body.appendChild(tempDiv);

            // Small delay to ensure DOM is ready
            await new Promise(resolve => setTimeout(resolve, 10));

            const html5QrCode = new Html5Qrcode(scannerId);

            try {
                // Try scanning with display enabled
                const qrCodeMessage = await html5QrCode.scanFile(file, true);
                console.log('QR Code scanned:', qrCodeMessage);

                // Clean up scanner before processing result
                await html5QrCode.clear();
                if (tempDiv && document.body.contains(tempDiv)) {
                    document.body.removeChild(tempDiv);
                }

                // Process the result
                await handleQRScanResult(qrCodeMessage);

            } catch (scanError) {
                // Clean up on scan error
                try {
                    await html5QrCode.clear();
                } catch (clearError) {
                    console.log('Clear error:', clearError);
                }
                throw scanError;
            }

        } catch (error: any) {
            console.error('QR scan error:', error);

            // Clean up any remaining elements
            if (tempDiv && document.body.contains(tempDiv)) {
                document.body.removeChild(tempDiv);
            }

            // Check for specific error types
            if (error.message?.includes('No MultiFormat Readers')) {
                toast.error('No QR code found in the image');
            } else if (error.message?.includes('NotFoundError')) {
                toast.error('Scanner initialization failed');
            } else {
                toast.error('Failed to scan QR code');
            }
        }
    };

    const handleQRScanResult = async (qrData: string) => {
        console.log('=== handleQRScanResult called ===');
        console.log('QR Data received:', qrData);
        console.log('QR Data length:', qrData.length);
        console.log('Selected Event:', selectedEvent);

        try {
            // Check if event is selected
            if (!selectedEvent) {
                console.error('No event selected in handleQRScanResult');
                toast.error('Please select an event first');
                return;
            }

            // Check for duplicate scan within 5 seconds
            if (recentScans.has(qrData)) {
                console.log('Duplicate scan detected');
                toast.warning('This QR code was just scanned, please wait a moment before scanning again');
                return;
            }

            // Add to recent scans and auto-remove after 5 seconds
            setRecentScans(prev => new Set(prev.add(qrData)));
            setTimeout(() => {
                setRecentScans(prev => {
                    const newSet = new Set(prev);
                    newSet.delete(qrData);
                    return newSet;
                });
            }, 5000);

            let parsedData;

            try {
                parsedData = JSON.parse(qrData);
                console.log('Successfully parsed QR data as JSON:', parsedData);
            } catch (e) {
                console.log('QR data is not JSON, treating as token string');
                parsedData = { token: qrData };
            }

            // For barcode scanner, use 'Barcode Scanner' as the location
            const scanLocation = qrScannerActive ? (scanMode === 'camera' ? 'Camera QR Scanner' : 'Image Upload QR Scanner') : 'Barcode Scanner';
            console.log('Scan location:', scanLocation);
            console.log('Making API call to:', `/admin/events/${selectedEvent?.id}/checkin/qr`);

            const response = await api.post(`/admin/events/${selectedEvent?.id}/checkin/qr`, {
                qrData: qrData,
                location: scanLocation,
                adminName: 'Admin User',
                adminEmail: 'admin@bmm.com'
            });

            console.log('API Response:', response.data);

            if (response.data.status === 'success') {
                const memberName = response.data.data?.memberName || response.data.data?.name || 'Member';
                const location = response.data.data?.checkinLocation || 'Unknown location';
                const membershipNumber = response.data.data?.membershipNumber || 'Unknown';

                toast.success(`✅ ${memberName} (${membershipNumber}) checked in successfully! Location: ${location}`, {
                    autoClose: 3000,
                });

                setLastScannedToken(qrData);
                // 🔧 Performance Fix: Parallel refresh after QR scan
                await Promise.all([
                    fetchEventMembers(selectedEvent!.id),
                    fetchCheckinStats(selectedEvent!.id)
                ]);

            } else if (response.data.status === 'warning') {
                const memberName = response.data.data?.memberName || 'Member';
                const previousLocation = response.data.data?.previousCheckinLocation || 'Unknown location';
                const previousTime = response.data.data?.previousCheckinTime ?
                    new Date(response.data.data.previousCheckinTime).toLocaleString() : 'Unknown time';

                toast.warning(`⚠️ ${memberName} already checked in!\nPrevious check-in: ${previousTime}\nLocation: ${previousLocation}`, {
                    autoClose: 5000,
                });

            } else {
                toast.error(response.data.message || 'Check-in failed, please try again');
            }
        } catch (error: any) {
            console.error('=== QR Scan Error ===');
            console.error('Failed to process QR scan', error);
            console.error('Error response:', error.response?.data);
            console.error('Error status:', error.response?.status);
            const errorMessage = error.response?.data?.message || 'Failed to process QR code, please try again';
            toast.error(errorMessage);
        }
    };

    const filteredMembers = members.filter(member => {
        const matchesSearch = member.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
            member.membershipNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
            member.primaryEmail.toLowerCase().includes(searchTerm.toLowerCase());
        const matchesStatus = filterStatus === 'all' ||
            (filterStatus === 'checked-in' && (member.checkinTime || member.checkInTime || member.checkedIn)) ||
            (filterStatus === 'not-checked-in' && !member.checkinTime && !member.checkInTime && !member.checkedIn) ||
            (filterStatus === 'attending' && member.isAttending);
        const matchesRegion = filterRegion === 'all' || member.regionDesc === filterRegion;
        return matchesSearch && matchesStatus && matchesRegion;
    }).sort((a, b) => {
        // Sort by checkin time - checked in members first, then by time
        const aCheckedIn = a.checkinTime || a.checkInTime;
        const bCheckedIn = b.checkinTime || b.checkInTime;
        
        if (aCheckedIn && !bCheckedIn) return -1;
        if (!aCheckedIn && bCheckedIn) return 1;
        if (aCheckedIn && bCheckedIn) {
            return new Date(bCheckedIn).getTime() - new Date(aCheckedIn).getTime(); // Most recent first
        }
        return a.name.localeCompare(b.name); // Alphabetical for non-checked-in
    });

    const uniqueRegions = [...new Set(members.map(member => member.regionDesc))].filter(Boolean);

    // Format time in Auckland timezone
    const formatAucklandTime = (dateString: string): string => {
        if (!dateString) return '';
        try {
            const date = new Date(dateString);
            return date.toLocaleString('en-NZ', {
                timeZone: 'Pacific/Auckland',
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
                hour12: true
            });
        } catch (error) {
            return dateString;
        }
    };

    if (!isAuthorized) return null;

    if (loading) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12 text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-purple-500"></div>
                    <p className="mt-4 text-gray-700 dark:text-gray-300">Loading check-in data...</p>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div>
                <div className="container mx-auto px-4 py-8">
                    <div className="flex justify-between items-center mb-8">
                        <div className="flex items-center">
                            <Link href="/admin/dashboard">
                                <button className="mr-4 text-gray-600 dark:text-gray-400 hover:text-black dark:hover:text-white">
                                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
                                    </svg>
                                </button>
                            </Link>
                            <h1 className="text-3xl font-bold text-black dark:text-white">  Event Check-in</h1>
                        </div>
                        <div className="flex space-x-3">
                            <button
                                onClick={() => setShowQRModal(true)}
                                className="bg-green-600 hover:bg-green-700 text-white px-4 py-2 rounded-lg flex items-center"
                            >
                                <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" />
                                </svg>
                                QR Scanner
                            </button>
                            {selectedEvent && (
                                <div className="flex space-x-2">
                                    <button
                                        onClick={() => generateQRCode(selectedEvent.id)}
                                        className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg text-sm"
                                    >
                                        Generate QR
                                    </button>
                                    <button
                                        onClick={() => sendQRCodes(selectedEvent.id)}
                                        className="bg-purple-600 hover:bg-purple-700 text-white px-4 py-2 rounded-lg text-sm"
                                    >
                                        Send QR Codes
                                    </button>
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6 mb-6">
                        <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Select Event</h2>
                        <select
                            value={selectedEvent?.id || ''}
                            onChange={(e) => {
                                const event = events.find(ev => ev.id === parseInt(e.target.value));
                                setSelectedEvent(event || null);
                            }}
                            className="w-full md:w-1/2 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                        >
                            <option value="">Select an event</option>
                            {events.map(event => (
                                <option key={event.id} value={event.id}>
                                    {event.name} - {new Date(event.eventDate).toLocaleDateString()}
                                </option>
                            ))}
                        </select>
                    </div>

                    {selectedEvent && (
                        <>
                            <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-6">
                                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6">
                                    <div className="flex items-center">
                                        <div className="p-2 bg-blue-100 dark:bg-blue-900 rounded-lg">
                                            <svg className="w-6 h-6 text-blue-600 dark:text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
                                            </svg>
                                        </div>
                                        <div className="ml-4">
                                            <p className="text-sm font-medium text-gray-500 dark:text-gray-400">Total Members</p>
                                            <p className="text-2xl font-bold text-gray-900 dark:text-white">{checkinStats.totalMembers}</p>
                                        </div>
                                    </div>
                                </div>

                                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6">
                                    <div className="flex items-center">
                                        <div className="p-2 bg-green-100 dark:bg-green-900 rounded-lg">
                                            <svg className="w-6 h-6 text-green-600 dark:text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                                            </svg>
                                        </div>
                                        <div className="ml-4">
                                            <p className="text-sm font-medium text-gray-500 dark:text-gray-400">Checked In</p>
                                            <p className="text-2xl font-bold text-gray-900 dark:text-white">{checkinStats.checkedIn}</p>
                                        </div>
                                    </div>
                                </div>

                                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6">
                                    <div className="flex items-center">
                                        <div className="p-2 bg-purple-100 dark:bg-purple-900 rounded-lg">
                                            <svg className="w-6 h-6 text-purple-600 dark:text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197m13.5-9a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0z" />
                                            </svg>
                                        </div>
                                        <div className="ml-4">
                                            <p className="text-sm font-medium text-gray-500 dark:text-gray-400">Attending</p>
                                            <p className="text-2xl font-bold text-gray-900 dark:text-white">{checkinStats.attending}</p>
                                        </div>
                                    </div>
                                </div>

                                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6">
                                    <div className="flex items-center">
                                        <div className="p-2 bg-yellow-100 dark:bg-yellow-900 rounded-lg">
                                            <svg className="w-6 h-6 text-yellow-600 dark:text-yellow-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                                            </svg>
                                        </div>
                                        <div className="ml-4">
                                            <p className="text-sm font-medium text-gray-500 dark:text-gray-400">Check-in Rate</p>
                                            <p className="text-2xl font-bold text-gray-900 dark:text-white">
                                                {checkinStats.totalMembers > 0 ? Math.round((checkinStats.checkedIn / checkinStats.totalMembers) * 100) : 0}%
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6 mb-6">
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Search Members</label>
                                        <input
                                            type="text"
                                            value={searchTerm}
                                            onChange={(e) => setSearchTerm(e.target.value)}
                                            placeholder="Search by name, membership number, or email"
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                        />
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Filter by Status</label>
                                        <select
                                            value={filterStatus}
                                            onChange={(e) => setFilterStatus(e.target.value)}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                        >
                                            <option value="all">All Members</option>
                                            <option value="checked-in">Checked In</option>
                                            <option value="not-checked-in">Not Checked In</option>
                                            <option value="attending">Attending</option>
                                        </select>
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Filter by Region</label>
                                        <select
                                            value={filterRegion}
                                            onChange={(e) => setFilterRegion(e.target.value)}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                        >
                                            <option value="all">All Regions</option>
                                            {uniqueRegions.map(region => (
                                                <option key={region} value={region}>{region}</option>
                                            ))}
                                        </select>
                                    </div>
                                </div>
                            </div>

                            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
                                <div className="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
                                    <h3 className="text-lg font-medium text-gray-900 dark:text-white">
                                        Members ({filteredMembers.length})
                                    </h3>
                                </div>
                                <div className="overflow-x-auto">
                                    <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                                        <thead className="bg-gray-50 dark:bg-gray-700">
                                        <tr>
                                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Member</th>
                                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Region</th>
                                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Status</th>
                                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Check-in Details</th>
                                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Admin & Venue</th>
                                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Actions</th>
                                        </tr>
                                        </thead>
                                        <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                                        {filteredMembers.map((member) => (
                                            <tr key={member.id} className="hover:bg-gray-50 dark:hover:bg-gray-700">
                                                <td className="px-6 py-4 whitespace-nowrap">
                                                    <div className="flex items-center">
                                                        <div>
                                                            <div className="text-sm font-medium text-gray-900 dark:text-white">{member.name}</div>
                                                            <div className="text-sm text-gray-500 dark:text-gray-400">{member.membershipNumber}</div>
                                                            <div className="text-sm text-gray-500 dark:text-gray-400">{member.primaryEmail}</div>
                                                        </div>
                                                    </div>
                                                </td>
                                                <td className="px-6 py-4 whitespace-nowrap">
                                                    <div className="text-sm text-gray-900 dark:text-white">{member.regionDesc}</div>
                                                    <div className="text-sm text-gray-500 dark:text-gray-400">{member.employerName}</div>
                                                </td>
                                                <td className="px-6 py-4 whitespace-nowrap">
                                                    <div className="flex flex-col space-y-1">
<span className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
    member.isAttending
        ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
        : 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300'
}`}>
{member.isAttending ? 'Attending' : 'Not Attending'}
</span>
                                                        {member.hasVoted && (
                                                            <span className="inline-flex px-2 py-1 text-xs font-semibold rounded-full bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200">
Voted
</span>
                                                        )}
                                                    </div>
                                                </td>
                                                <td className="px-6 py-4 whitespace-nowrap">
                                                    {(member.checkinTime || member.checkInTime || member.checkedIn) ? (
                                                        <div>
<span className="inline-flex px-2 py-1 text-xs font-semibold rounded-full bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200">
Checked In
</span>
                                                            <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                                                                {formatAucklandTime(member.checkinTime || member.checkInTime || '')}
                                                            </div>
                                                            {member.checkInMethod && (
                                                                <div className="text-xs text-blue-600 dark:text-blue-400 mt-1">
                                                                    Method: {member.checkInMethod}
                                                                </div>
                                                            )}
                                                        </div>
                                                    ) : (
                                                        <span className="inline-flex px-2 py-1 text-xs font-semibold rounded-full bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200">
Not Checked In
</span>
                                                    )}
                                                </td>
                                                <td className="px-6 py-4 whitespace-nowrap">
                                                    {(member.checkinTime || member.checkInTime || member.checkedIn) ? (
                                                        <div className="text-sm">
                                                            {member.checkInAdminName && (
                                                                <div className="text-gray-900 dark:text-white">
                                                                    👤 {member.checkInAdminName}
                                                                    {member.checkInAdminUsername && (
                                                                        <span className="text-xs text-gray-500 ml-1">({member.checkInAdminUsername})</span>
                                                                    )}
                                                                </div>
                                                            )}
                                                            {member.checkInVenue && (
                                                                <div className="text-gray-600 dark:text-gray-400 text-xs">
                                                                    📍 {member.checkInVenue}
                                                                </div>
                                                            )}
                                                            {member.checkInLocation && (
                                                                <div className="text-gray-500 dark:text-gray-500 text-xs">
                                                                    {member.checkInLocation}
                                                                </div>
                                                            )}
                                                        </div>
                                                    ) : (
                                                        <div className="text-sm">
                                                            <span className="text-gray-400 dark:text-gray-500">-</span>
                                                            {(member.forumDesc || member.assignedVenueFinal) && (
                                                                <div className="text-xs text-gray-400 mt-1">
                                                                    {member.forumDesc && (
                                                                        <div>📍 Forum: {member.forumDesc}</div>
                                                                    )}
                                                                    {member.assignedVenueFinal && member.assignedVenueFinal !== member.forumDesc && (
                                                                        <div>🏢 Venue: {member.assignedVenueFinal}</div>
                                                                    )}
                                                                </div>
                                                            )}
                                                        </div>
                                                    )}
                                                </td>
                                                <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                                                    {!member.checkinTime && !member.checkInTime && !member.checkedIn && (
                                                        <button
                                                            onClick={() => {
                                                                setSelectedMember(member);
                                                                setShowManualCheckinModal(true);
                                                            }}
                                                            className="text-blue-600 dark:text-blue-400 hover:text-blue-900 dark:hover:text-blue-300"
                                                        >
                                                            Manual Check-in
                                                        </button>
                                                    )}
                                                </td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </>
                    )}
                </div>

                {showQRModal && (
                    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                        <div className="bg-white dark:bg-gray-800 rounded-lg max-w-lg w-full">
                            <div className="p-6">
                                <div className="flex justify-between items-center mb-4">
                                    <h2 className="text-xl font-bold text-gray-900 dark:text-white">QR Code Scanner</h2>
                                    <button
                                        onClick={() => {
                                            setShowQRModal(false);
                                            stopQRScanner();
                                        }}
                                        className="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300"
                                    >
                                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                        </svg>
                                    </button>
                                </div>

                                <div className="mb-4">
                                    <div className="flex space-x-4 justify-center">
                                        <button
                                            onClick={() => {
                                                setScanMode('camera');
                                                stopQRScanner();
                                            }}
                                            className={`px-4 py-2 rounded-lg ${
                                                scanMode === 'camera'
                                                    ? 'bg-blue-600 text-white'
                                                    : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                                            }`}
                                        >
                                            📷 Camera Scan
                                        </button>
                                        <button
                                            onClick={() => {
                                                setScanMode('upload');
                                                stopQRScanner();
                                            }}
                                            className={`px-4 py-2 rounded-lg ${
                                                scanMode === 'upload'
                                                    ? 'bg-blue-600 text-white'
                                                    : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                                            }`}
                                        >
                                            📁 Upload Image
                                        </button>
                                    </div>
                                </div>

                                <div className="text-center">
                                    {scanMode === 'camera' ? (
                                        <div>
                                            {cameraPermissionDenied ? (
                                                <div className="text-center p-4">
                                                    <div className="text-red-500 text-4xl mb-4">⚠️</div>
                                                    <p className="text-red-600 dark:text-red-400 mb-4">
                                                        Unable to access camera, permission may be denied
                                                    </p>
                                                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                                                        Please check browser permission settings or use image upload
                                                    </p>
                                                    <div className="space-y-2">
                                                        <button
                                                            onClick={resetCameraPermission}
                                                            className="bg-green-600 hover:bg-green-700 text-white px-4 py-2 rounded-lg mr-2"
                                                        >
                                                            Try Camera Again
                                                        </button>
                                                        <button
                                                            onClick={() => setScanMode('upload')}
                                                            className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg"
                                                        >
                                                            Switch to Image Upload
                                                        </button>
                                                        <p className="text-xs text-gray-500 mt-2">
                                                            💡 Tip: Click the lock icon 🔒 in your address bar to reset camera permissions
                                                        </p>
                                                    </div>
                                                </div>
                                            ) : !qrScannerActive ? (
                                                <div>
                                                    <div className="text-gray-400 dark:text-gray-500 text-6xl mb-4">📷</div>
                                                    <p className="text-gray-600 dark:text-gray-400 mb-4">
                                                        Click the button below to start scanning QR codes
                                                    </p>
                                                    <button
                                                        onClick={startQRScanner}
                                                        className="bg-green-600 hover:bg-green-700 text-white px-6 py-2 rounded-lg"
                                                    >
                                                        Start Scanner
                                                    </button>
                                                </div>
                                            ) : (
                                                <div>
                                                    <div id="qr-reader" className="mb-4"></div>
                                                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                                                        Point QR code at camera to scan
                                                    </p>
                                                    <button
                                                        onClick={stopQRScanner}
                                                        className="bg-red-600 hover:bg-red-700 text-white px-6 py-2 rounded-lg"
                                                    >
                                                        Stop Scanner
                                                    </button>
                                                </div>
                                            )}
                                        </div>
                                    ) : (
                                        <div>
                                            <div className="text-gray-400 dark:text-gray-500 text-6xl mb-4">📁</div>
                                            <p className="text-gray-600 dark:text-gray-400 mb-4">
                                                Select an image containing QR code for upload and recognition
                                            </p>
                                            <div className="mb-4">
                                                <input
                                                    ref={fileInputRef}
                                                    type="file"
                                                    accept="image/*"
                                                    onChange={handleFileUpload}
                                                    className="hidden"
                                                    id="qr-file-input"
                                                />
                                                <label
                                                    htmlFor="qr-file-input"
                                                    className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-2 rounded-lg cursor-pointer inline-block"
                                                >
                                                    Select Image
                                                </label>
                                            </div>
                                            <p className="text-xs text-gray-500 dark:text-gray-400">
                                                Supports JPG, PNG, GIF and other image formats
                                            </p>
                                        </div>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {showManualCheckinModal && selectedMember && (
                    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                        <div className="bg-white dark:bg-gray-800 rounded-lg max-w-md w-full">
                            <div className="p-6">
                                <div className="flex justify-between items-center mb-4">
                                    <h2 className="text-xl font-bold text-gray-900 dark:text-white">Manual Check-in</h2>
                                    <button
                                        onClick={() => {
                                            setShowManualCheckinModal(false);
                                            setSelectedMember(null);
                                        }}
                                        className="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300"
                                    >
                                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                        </svg>
                                    </button>
                                </div>

                                <div className="mb-4">
                                    <h3 className="text-lg font-medium text-gray-900 dark:text-white">{selectedMember.name}</h3>
                                    <p className="text-sm text-gray-500 dark:text-gray-400">{selectedMember.membershipNumber}</p>
                                    <p className="text-sm text-gray-500 dark:text-gray-400">{selectedMember.primaryEmail}</p>
                                </div>

                                <div className="mb-4">
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                        Check-in Venue
                                    </label>
                                    <select
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                        id="checkin-venue"
                                        defaultValue=""
                                    >
                                        <option value="">Select a venue...</option>
                                        <optgroup label="Northern Region">
                                            <option value="Auckland Central">Auckland Central - Alexandra Park</option>
                                            <option value="Auckland North Shore">Auckland North Shore - Barfoot & Thompson Netball Centre</option>
                                            <option value="Auckland West">Auckland West - Trusts Arena</option>
                                            <option value="Hamilton 1">Hamilton 1 - Distinction Hotel</option>
                                            <option value="Hamilton 2">Hamilton 2 - Distinction Hotel</option>
                                            <option value="Manukau 1">Manukau 1 - Go Media Stadium</option>
                                            <option value="Manukau 2">Manukau 2 - Go Media Stadium</option>
                                            <option value="Manukau 3">Manukau 3 - Bruce Pulman Arena</option>
                                            <option value="Pukekohe">Pukekohe - Bruce Pulman Arena</option>
                                            <option value="Rotorua">Rotorua - Arawa Park Hotel</option>
                                            <option value="Tauranga">Tauranga - Papamoa Community Centre</option>
                                            <option value="Whangarei">Whangarei - The Barge Showgrounds</option>
                                        </optgroup>
                                        <optgroup label="Central Region">
                                            <option value="Gisborne">Gisborne - Gisborne Cosmopolitan Club</option>
                                            <option value="Napier">Napier - Hawkes Bay Cricket Association</option>
                                            <option value="New Plymouth">New Plymouth - Theatre Royal (TSB Showplace)</option>
                                            <option value="Palmerston North">Palmerston North - Palmy Conference & Function Centre</option>
                                            <option value="Wellington 1">Wellington 1 - Te Rauparaha Arena</option>
                                            <option value="Wellington 2">Wellington 2 - Silverstream Retreat</option>
                                        </optgroup>
                                        <optgroup label="Southern Region">
                                            <option value="Christchurch 1">Christchurch 1 - Woolston Club</option>
                                            <option value="Christchurch 2">Christchurch 2 - Addington Raceway</option>
                                            <option value="Dunedin">Dunedin - Harbourview Lounge Edgar Centre</option>
                                            <option value="Invercargill">Invercargill - Invercargill Workingmen's Club</option>
                                            <option value="Nelson">Nelson - Club Waimea</option>
                                            <option value="Timaru">Timaru - Caroline Bay Hall</option>
                                        </optgroup>
                                        <option value="Online/Virtual">Online/Virtual</option>
                                        <option value="Other">Other Location</option>
                                    </select>
                                </div>

                                <div className="mb-4">
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                        Additional Notes
                                    </label>
                                    <input
                                        type="text"
                                        placeholder="Optional: Additional location details"
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                        id="checkin-notes"
                                    />
                                </div>

                                <div className="flex justify-end space-x-3">
                                    <button
                                        onClick={() => {
                                            setShowManualCheckinModal(false);
                                            setSelectedMember(null);
                                        }}
                                        className="px-4 py-2 text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 rounded-md"
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        onClick={() => {
                                            const venue = (document.getElementById('checkin-venue') as HTMLSelectElement).value;
                                            const notes = (document.getElementById('checkin-notes') as HTMLInputElement).value;
                                            const location = venue + (notes ? ` - ${notes}` : '');
                                            handleManualCheckin(selectedMember.membershipNumber, location, venue);
                                        }}
                                        className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white rounded-md"
                                    >
                                        Check In
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {/* Barcode Scanner Status Indicator */}
                {scannerInput && (
                    <div className="fixed bottom-4 right-4 bg-blue-600 text-white px-4 py-2 rounded-lg shadow-lg z-40 flex items-center">
                        <svg className="w-5 h-5 mr-2 animate-pulse" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" />
                        </svg>
                        <span className="text-sm">Scanning...</span>
                    </div>
                )}

                {/* Processing Scanner Indicator */}
                {isProcessingScanner && (
                    <div className="fixed bottom-4 right-4 bg-green-600 text-white px-4 py-2 rounded-lg shadow-lg z-40 flex items-center">
                        <div className="animate-spin rounded-full h-4 w-4 border-t-2 border-b-2 border-white mr-2"></div>
                        <span className="text-sm">Processing check-in...</span>
                    </div>
                )}
            </div>
        </Layout>
    );
}