'use client';
import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import { Html5QrcodeScanner, Html5QrcodeScanType, Html5Qrcode } from 'html5-qrcode';
import api from '@/services/api';

interface ScanResult {
    memberName: string;
    membershipNumber: string;
    email: string;
    checkinTime: string;
    venue: string;
    status: string;
}

export default function BMMCheckinPage() {
    const router = useRouter();
    const { token, eventId, venue, timestamp, adminName, adminEmail } = router.query;

    const [isValidToken, setIsValidToken] = useState(false);
    const [loading, setLoading] = useState(true);
    const [eventInfo, setEventInfo] = useState<any>(null);
    const [scanResults, setScanResults] = useState<ScanResult[]>([]);
    const [qrScannerActive, setQrScannerActive] = useState(false);
    const [scanMode, setScanMode] = useState<'camera' | 'upload'>('camera');
    const [cameraPermissionDenied, setCameraPermissionDenied] = useState(false);
    const [stats, setStats] = useState({ total: 0, checkedIn: 0 });
    const [scannerInput, setScannerInput] = useState('');
    const [isProcessingScanner, setIsProcessingScanner] = useState(false);

    const qrScannerRef = useRef<Html5QrcodeScanner | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const scannerInputRef = useRef<string>('');
    const scannerTimeoutRef = useRef<NodeJS.Timeout | null>(null);

    const validateTokenAndLoadEvent = useCallback(async () => {
        try {
            const response = await api.get(`/venue/checkin/validate?token=${token}&eventId=${eventId}&venue=${encodeURIComponent(venue as string)}`);

            if (response.data.status === 'success') {
                setIsValidToken(true);
                setEventInfo(response.data.data.event);
                setStats(response.data.data.stats);
            } else {
                toast.error('Invalid or expired scan link');
                router.push('/');
            }
        } catch (error: any) {
            console.error('Token validation failed:', error);
            toast.error('Failed to validate scan link');
            router.push('/');
        } finally {
            setLoading(false);
        }
    }, [token, eventId, venue, router]);

    useEffect(() => {
        if (token && eventId && venue) {
            validateTokenAndLoadEvent();
        } else if (!token || !eventId || !venue) {
            // Missing required parameters
            setLoading(false);
            // Silently handle missing parameters without showing error to user
        }
    }, [token, eventId, venue, validateTokenAndLoadEvent]);

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

    // Handle barcode scanner input
    useEffect(() => {
        const handleBarcodeScannerInput = (event: KeyboardEvent) => {
            // Skip if not a valid token/event
            if (!isValidToken || !eventInfo) return;

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
                if (!isProcessingScanner) {
                    console.log('Starting QR scan processing...');
                    setIsProcessingScanner(true);
                    handleQRScanResult(scannedData).finally(() => {
                        console.log('QR scan processing completed');
                        setIsProcessingScanner(false);
                    });
                } else {
                    console.log('Still processing previous scan');
                    toast.warning('Still processing previous scan, please wait...');
                }
            } else if (event.key.length === 1 && event.key.match(/[\w\-{}":,\s]/)) {
                // Only accumulate valid characters (alphanumeric, JSON characters, etc.)
                scannerInputRef.current += event.key;
                setScannerInput(scannerInputRef.current);

                // Clear accumulated input after 200ms of no activity
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
    }, [isValidToken, eventInfo, isProcessingScanner]);

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
                (decodedText) => {
                    console.log(`QR Code detected: ${decodedText}`);
                    handleQRScanResult(decodedText);
                    // Stop scanner after successful scan to prevent multiple scans
                    stopQRScanner();
                },
                (errorMessage) => {
                    // Only log meaningful errors
                    if (errorMessage.includes('NotAllowedError') ||
                        errorMessage.includes('NotFoundError') ||
                        errorMessage.includes('Permission')) {
                        console.error('Camera permission error:', errorMessage);
                        setCameraPermissionDenied(true);
                        toast.error('Camera permission denied. Please enable camera access in your browser settings.');
                    }
                }
            );

            // State already set at the beginning of the function
            setCameraPermissionDenied(false);

        } catch (error) {
            console.error('Camera initialization failed:', error);
            setCameraPermissionDenied(true);
            toast.error('Unable to access camera. Please check permissions or use image upload.');
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
        try {
            // Debug: Log the parameters
            console.log('QR Scan Parameters:', {
                token,
                eventId,
                venue,
                adminName,
                adminEmail,
                qrData
            });

            if (!token) {
                toast.error('Missing admin token. Please use a valid scanner link.');
                return;
            }

            if (!qrData || qrData.trim() === '') {
                toast.error('Invalid QR code data');
                return;
            }

            // Ensure venue is URL encoded
            const encodedVenue = encodeURIComponent(venue as string);

            const response = await api.post(`/venue/checkin/scan/${eventId}?adminToken=${token}&venue=${encodedVenue}`, {
                qrData,
                location: `${venue} - BMM Venue Scanner`,
                adminName: adminName as string || 'Venue Admin',
                adminEmail: adminEmail as string || 'admin@bmm.com'
            });

            if (response.data.status === 'success') {
                const result: ScanResult = {
                    memberName: response.data.data.name,
                    membershipNumber: response.data.data.membershipNumber,
                    email: response.data.data.primaryEmail,
                    checkinTime: response.data.data.checkinTime,
                    venue: venue as string,
                    status: 'success'
                };
                setScanResults(prev => [result, ...prev]);
                setStats(prev => ({ ...prev, checkedIn: prev.checkedIn + 1 }));

                const adminInfo = response.data.data.adminName ? ` (Scanned by: ${response.data.data.adminName})` : '';
                toast.success(`${result.memberName} checked in successfully!${adminInfo}`);
            } else if (response.data.status === 'warning') {
                const memberName = response.data.data?.memberName || response.data.data?.name || 'Unknown Member';
                const previousLocation = response.data.data?.previousCheckinLocation || response.data.data?.checkInLocation || 'Unknown location';
                const result: ScanResult = {
                    memberName: memberName,
                    membershipNumber: response.data.data?.membershipNumber || 'Unknown',
                    email: response.data.data?.primaryEmail || response.data.data?.email || 'Unknown',
                    checkinTime: response.data.data?.previousCheckinTime || response.data.data?.checkInTime || new Date().toISOString(),
                    venue: previousLocation,
                    status: 'already_checked_in'
                };
                setScanResults(prev => [result, ...prev]);
                toast.warning(`⚠️ DUPLICATE SCAN: ${memberName} was already checked in at ${previousLocation}`);
            } else {
                toast.error(response.data.message || 'Check-in failed');
            }
        } catch (error: any) {
            console.error('Failed to process QR scan', error);
            const errorMessage = error.response?.data?.message || 'Failed to process QR code';
            toast.error(errorMessage);
        }
    };

    if (loading) {
        return (
            <div className="min-h-screen bg-gray-50 flex items-center justify-center">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
                    <p className="mt-4 text-gray-600">Validating scan link...</p>
                </div>
            </div>
        );
    }

    if (!isValidToken) {
        return (
            <div className="min-h-screen bg-gray-50 flex items-center justify-center">
                <div className="text-center">
                    <div className="text-red-500 text-6xl mb-4">⚠️</div>
                    <h1 className="text-2xl font-bold text-gray-900 mb-2">Invalid Scan Link</h1>
                    <p className="text-gray-600">This scan link is invalid or has expired.</p>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gray-50">
            {/* Header */}
            <div className="bg-white shadow">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="py-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <h1 className="text-3xl font-bold text-gray-900">
                                    BMM Check-in Scanner
                                </h1>
                                <p className="mt-1 text-sm text-gray-600">
                                    {eventInfo?.name} - {venue} Region
                                </p>
                            </div>
                            <div className="text-right">
                                <div className="text-sm text-gray-500">Check-in Stats</div>
                                <div className="text-2xl font-bold text-blue-600">
                                    {stats.checkedIn} / {stats.total}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                    {/* Scanner Section */}
                    <div className="lg:col-span-2">
                        <div className="bg-white rounded-lg shadow p-6">
                            <h2 className="text-xl font-semibold text-gray-900 mb-4">QR Code Scanner</h2>

                            {/* Scan Mode Selection */}
                            <div className="mb-6">
                                <div className="flex space-x-4 justify-center">
                                    <button
                                        onClick={() => {
                                            setScanMode('camera');
                                            stopQRScanner();
                                        }}
                                        className={`px-6 py-3 rounded-lg font-medium ${
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
                                        className={`px-6 py-3 rounded-lg font-medium ${
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
                                            <div className="text-center p-8">
                                                <div className="text-red-500 text-6xl mb-4">⚠️</div>
                                                <p className="text-red-600 mb-4">
                                                    Unable to access camera, permission may be denied
                                                </p>
                                                <p className="text-sm text-gray-600 mb-4">
                                                    Please check browser permission settings or use image upload
                                                </p>
                                                <button
                                                    onClick={() => setScanMode('upload')}
                                                    className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-3 rounded-lg"
                                                >
                                                    Switch to Image Upload
                                                </button>
                                            </div>
                                        ) : !qrScannerActive ? (
                                            <div>
                                                <div className="text-gray-400 text-8xl mb-6">📷</div>
                                                <p className="text-gray-600 mb-6 text-lg">
                                                    Click the button below to start scanning QR codes
                                                </p>
                                                <button
                                                    onClick={startQRScanner}
                                                    className="bg-green-600 hover:bg-green-700 text-white px-8 py-4 rounded-lg text-lg font-medium"
                                                >
                                                    Start Scanner
                                                </button>
                                            </div>
                                        ) : (
                                            <div>
                                                <div id="qr-reader" className="mb-6"></div>
                                                <p className="text-sm text-gray-600 mb-4">
                                                    Point QR code at camera to scan
                                                </p>
                                                <button
                                                    onClick={stopQRScanner}
                                                    className="bg-red-600 hover:bg-red-700 text-white px-6 py-3 rounded-lg"
                                                >
                                                    Stop Scanner
                                                </button>
                                            </div>
                                        )}
                                    </div>
                                ) : (
                                    <div>
                                        <div className="text-gray-400 text-8xl mb-6">📁</div>
                                        <p className="text-gray-600 mb-6 text-lg">
                                            Select an image containing QR code for upload and recognition
                                        </p>
                                        <div className="mb-6">
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
                                                className="bg-blue-600 hover:bg-blue-700 text-white px-8 py-4 rounded-lg cursor-pointer inline-block text-lg font-medium"
                                            >
                                                Select Image
                                            </label>
                                        </div>
                                        <p className="text-xs text-gray-500">
                                            Supports JPG, PNG, GIF and other image formats
                                        </p>
                                    </div>
                                )}
                            </div>

                            {/* Barcode Scanner Tip */}
                            {!qrScannerActive && scanMode === 'camera' && (
                                <div className="mt-6 p-4 bg-blue-50 border border-blue-200 rounded-lg">
                                    <div className="flex items-center text-blue-800">
                                        <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                                        </svg>
                                        <span className="text-sm font-medium">Tip: External barcode scanners are also supported!</span>
                                    </div>
                                    <p className="text-xs text-blue-600 mt-1 ml-7">
                                        Simply scan a QR code with your handheld scanner while this page is active.
                                    </p>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Results Section */}
                    <div className="lg:col-span-1">
                        <div className="bg-white rounded-lg shadow p-6">
                            <h3 className="text-lg font-semibold text-gray-900 mb-4">
                                Recent Check-ins
                            </h3>
                            <div className="space-y-3 max-h-96 overflow-y-auto">
                                {scanResults.length === 0 ? (
                                    <div className="text-center py-8">
                                        <p className="text-gray-400 text-sm">Ready to scan QR codes</p>
                                    </div>
                                ) : (
                                    scanResults.map((result, index) => (
                                        <div key={index} className={`border rounded-lg p-3 ${
                                            result.status === 'success' ? 'border-green-200 bg-green-50' :
                                                result.status === 'already_checked_in' ? 'border-yellow-200 bg-yellow-50' :
                                                    'border-gray-200'
                                        }`}>
                                            <div className="flex items-center justify-between">
                                                <div className="font-medium text-gray-900">
                                                    {result.memberName}
                                                </div>
                                                {result.status === 'success' && <span className="text-green-600 text-xs">✓</span>}
                                                {result.status === 'already_checked_in' && <span className="text-yellow-600 text-xs">⚠️</span>}
                                            </div>
                                            <div className="text-sm text-gray-600">
                                                {result.membershipNumber}
                                            </div>
                                            <div className="text-xs text-gray-500 mt-1">
                                                {new Date(result.checkinTime).toLocaleTimeString()} - {result.venue}
                                                {result.status === 'already_checked_in' && (
                                                    <div className="text-yellow-600 font-medium mt-1">
                                                        ⚠️ DUPLICATE SCAN - Already checked in
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>
                    </div>
                </div>

                {/* Barcode Scanner Status Indicator */}
                {scannerInput && (
                    <div className="fixed bottom-4 right-4 bg-blue-600 text-white px-4 py-2 rounded-lg shadow-lg z-40 flex items-center">
                        <svg className="w-5 h-5 mr-2 animate-pulse" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" />
                        </svg>
                        <span className="text-sm">Scanning: {scannerInput.substring(0, 20)}{scannerInput.length > 20 ? '...' : ''}</span>
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
        </div>
    );
}