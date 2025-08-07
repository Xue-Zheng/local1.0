'use client';
import { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import { QRCodeSVG } from 'qrcode.react';
import { useReactToPrint } from 'react-to-print';
import html2canvas from 'html2canvas';
import api from '@/services/api';

interface TicketData {
    name: string;
    membershipNumber: string;
    region: string;
    assignedVenue: string;
    assignedDateTime: string;
    ticketToken: string;
    memberToken?: string; // Add member token for QR code
    eventName: string;
    ticketStatus: string;
    specialVoteEligible: boolean;
    specialVoteRequested?: boolean; // Whether member has applied for special vote
    // Stage 1 preferences
    preferredTimesJson?: string;
    workplaceInfo?: string;
    additionalComments?: string;
    suggestedVenue?: string;
    preferredAttending?: boolean;
    // Venue details from backend
    venueAddress?: string;
    assignedSession?: string; // Session time from venue config
    forumDesc?: string; // Forum description to identify special forums
    timeSpan?: string; // Time span including travel time
    assignedDate?: string; // Meeting date from venue config
}

interface MemberData {
    name: string;
    email: string;
    membershipNumber: string;
    isSpecialVote: boolean;
}

export default function TicketPage() {
    const router = useRouter();
    const token = router.query.token as string || '';

    const [ticketData, setTicketData] = useState<TicketData | null>(null);
    const [memberData, setMemberData] = useState<MemberData | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');
    const [ticketType, setTicketType] = useState('regular');
    const [isBMMTicket, setIsBMMTicket] = useState(false);

    const ticketRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const fetchTicketData = async () => {
            if (!token) {
                setError('Invalid token');
                setIsLoading(false);
                return;
            }

            try {
                // Use public API endpoint for ticket access
                try {
                    const bmmResponse = await api.get(`/admin/ticket-emails/bmm-ticket/${token}`);
                    if (bmmResponse.data.status === 'success') {
                        setTicketData(bmmResponse.data.data);
                        setIsBMMTicket(true);
                        setTicketType('bmm');
                        setIsLoading(false);
                        return;
                    }
                } catch (bmmError) {
                    console.log('Not a BMM ticket, trying regular member lookup');
                }

                // If BMM ticket lookup fails, try regular member lookup
                const response = await api.get(`/event-registration/member/${token}`);

                if (response.data.status === 'success') {
                    const memberInfo = response.data.data;
                    setMemberData({
                        name: memberInfo.name,
                        email: memberInfo.primaryEmail,
                        membershipNumber: memberInfo.membershipNumber,
                        isSpecialVote: memberInfo.isSpecialVote || false
                    });

                    setTicketType('regular');
                    setIsBMMTicket(false);
                } else {
                    throw new Error(response.data.message || 'Failed to load ticket data');
                }

            } catch (err: any) {
                console.error('Failed to fetch ticket data:', err);
                setError(err.response?.data?.message || 'Failed to fetch ticket data');
                toast.error('Failed to fetch ticket data');
            } finally {
                setIsLoading(false);
            }
        };

        if (token) {
            fetchTicketData();
        }
    }, [token]);

    const handlePrint = useReactToPrint({
        contentRef: ticketRef,
        documentTitle: isBMMTicket ? 'My BMM Ticket' : 'My E tu Event Ticket',
    });

    const handleCopyLink = async () => {
        const currentUrl = window.location.href;
        try {
            await navigator.clipboard.writeText(currentUrl);
            toast.success('Ticket link copied to clipboard!');
        } catch (err) {
            // Fallback for browsers that don't support clipboard API
            const textArea = document.createElement('textarea');
            textArea.value = currentUrl;
            document.body.appendChild(textArea);
            textArea.select();
            document.execCommand('copy');
            document.body.removeChild(textArea);
            toast.success('Ticket link copied to clipboard!');
        }
    };

    const handleSaveAsImage = async () => {
        if (!ticketRef.current) {
            toast.error('Ticket not ready for capture');
            return;
        }

        try {
            const canvas = await html2canvas(ticketRef.current, {
                scale: 2,
                useCORS: true,
                allowTaint: true,
                backgroundColor: '#ffffff'
            });

            const link = document.createElement('a');
            link.download = isBMMTicket ? 'BMM-Ticket.png' : 'Event-Ticket.png';
            link.href = canvas.toDataURL('image/png');
            link.click();

            toast.success('Ticket saved as image!');
        } catch (error) {
            console.error('Error capturing ticket:', error);
            toast.error('Failed to save ticket as image');
        }
    };

    const handleAddToCalendar = async () => {
        try {
            if (!ticketData) return;

            // For now, add to calendar as a simpler alternative
            // This creates an .ics file that can be added to any calendar app
            const eventTitle = ticketData.eventName || '2025 E tū Biennial Membership Meeting';
            const venue = ticketData.assignedVenue || 'Venue TBA';
            const address = ticketData.venueAddress || '';

            // Parse date and time
            let startDate = new Date();
            let endDate = new Date();

            if (ticketData.assignedDate && ticketData.assignedSession) {
                // Parse date like "Monday 1 September"
                const dateParts = ticketData.assignedDate.match(/(\d+)\s+(\w+)/);
                if (dateParts) {
                    const day = parseInt(dateParts[1]);
                    const month = dateParts[2];
                    const year = 2025;
                    startDate = new Date(`${month} ${day}, ${year} ${ticketData.assignedSession}`);
                    endDate = new Date(startDate.getTime() + 2 * 60 * 60 * 1000); // 2 hours later
                }
            }

            // Create calendar event data
            const icsContent = [
                'BEGIN:VCALENDAR',
                'VERSION:2.0',
                'PRODID:-//E tū Events//BMM Ticket//EN',
                'BEGIN:VEVENT',
                `UID:${ticketData.ticketToken}@events.etu.nz`,
                `DTSTAMP:${formatICSDate(new Date())}`,
                `DTSTART:${formatICSDate(startDate)}`,
                `DTEND:${formatICSDate(endDate)}`,
                `SUMMARY:${eventTitle}`,
                `DESCRIPTION:Your ticket: https://events.etu.nz/ticket?token=${ticketData.ticketToken}\\n\\nMembership: ${ticketData.membershipNumber}`,
                `LOCATION:${venue}${address ? ', ' + address : ''}`,
                'STATUS:CONFIRMED',
                'END:VEVENT',
                'END:VCALENDAR'
            ].join('\r\n');

            // Download .ics file
            const blob = new Blob([icsContent], { type: 'text/calendar;charset=utf-8' });
            const link = document.createElement('a');
            link.href = URL.createObjectURL(blob);
            link.download = 'BMM-Event.ics';
            link.click();

            toast.success('Calendar event downloaded! Open it to add to your calendar.');
        } catch (error) {
            console.error('Error creating calendar event:', error);
            toast.error('Failed to create calendar event. Please save the ticket image instead.');
        }
    };

    // Helper function to format date for ICS
    const formatICSDate = (date: Date) => {
        return date.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
    };

    const formatDateTime = (dateTimeString: string) => {
        if (!dateTimeString || dateTimeString === 'TBA') return 'TBA';
        try {
            const date = new Date(dateTimeString);
            return date.toLocaleString('en-NZ', {
                weekday: 'long',
                year: 'numeric',
                month: 'long',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch {
            return dateTimeString;
        }
    };

    // Helper function to format venue display with address
    const formatVenueDisplay = (venue: string | undefined, address: string | undefined) => {
        if (!venue) return 'Venue to be confirmed';
        if (!address) return venue;
        // Show full address
        return `${venue}, ${address}`;
    };

    if (isLoading) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12 text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-orange-500"></div>
                    <p className="mt-4 text-gray-700 dark:text-gray-300">Loading your ticket...</p>
                </div>
            </Layout>
        );
    }

    if (error || (!ticketData && !memberData)) {
        // Special handling for attendance not confirmed error
        if (error && error.includes('attendance not confirmed')) {
            return (
                <Layout>
                    <div className="container mx-auto px-4 py-12">
                        <div className="max-w-md mx-auto p-6 bg-yellow-50 dark:bg-yellow-900/30 rounded-lg text-center">
                            <h2 className="text-2xl font-bold text-yellow-600 dark:text-yellow-400 mb-4">Ticket Not Yet Available</h2>
                            <p className="mb-4 text-gray-800 dark:text-gray-200">
                                Your BMM ticket will be available after you confirm your attendance.
                            </p>
                            <p className="mb-6 text-sm text-gray-600 dark:text-gray-400">
                                Please complete the attendance confirmation process first using the link sent to your email.
                            </p>
                            <button
                                onClick={() => {
                                    // Try to redirect to confirmation page with the same token
                                    const urlParams = new URLSearchParams(window.location.search);
                                    const currentToken = urlParams.get('token');
                                    if (currentToken) {
                                        router.push(`/bmm/confirmation?token=${currentToken}`);
                                    } else {
                                        router.push('/');
                                    }
                                }}
                                className="bg-orange-500 hover:bg-orange-600 text-white font-medium py-2 px-4 rounded transition-colors"
                            >
                                Go to Confirmation Page
                            </button>
                        </div>
                    </div>
                </Layout>
            );
        }

        return (
            <Layout>
                <div className="container mx-auto px-4 py-12">
                    <div className="max-w-md mx-auto p-6 bg-red-50 dark:bg-red-900/30 rounded-lg text-center">
                        <h2 className="text-2xl font-bold text-red-600 dark:text-red-400 mb-4">Ticket Not Found</h2>
                        <p className="mb-4 text-gray-800 dark:text-gray-200">{error || 'Unable to retrieve ticket data'}</p>
                        <button
                            onClick={() => router.push('/')}
                            className="bg-black hover:bg-gray-800 dark:bg-gray-700 dark:hover:bg-gray-600 text-white font-medium py-2 px-4 rounded transition-colors"
                        >
                            Return to Home
                        </button>
                    </div>
                </div>
            </Layout>
        );
    }

    // BMM Ticket Display
    if (isBMMTicket && ticketData) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12">
                    <div className="max-w-md mx-auto">
                        <h1 className="text-3xl font-bold text-black dark:text-white mb-6 text-center">
                            My BMM Ticket
                        </h1>

                        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg overflow-hidden mb-6" ref={ticketRef}>
                            <div className="bg-gradient-to-r from-blue-900 to-blue-700 text-white p-6 text-center">
                                <div className="mb-2">
                                    <h1 className="text-2xl font-bold">E tū Event System</h1>
                                    <p className="text-lg">Member Event Ticket</p>
                                </div>
                                <div className="mt-4 border-t border-blue-600 pt-4">
                                    <h2 className="text-xl font-semibold mb-2">{ticketData.eventName || '2025 E tū Biennial Membership Meeting'}</h2>
                                    {/* Check if this is a bulk ticket (TICKET_ONLY status) */}
                                    {ticketData.ticketStatus === "TICKET_ONLY" ? (
                                        <div className="text-base">
                                            <p className="font-medium mt-2">Valid for all BMM venues and sessions</p>
                                            <p className="text-sm mt-1">Time and venue details will be sent separately</p>
                                        </div>
                                    ) : ticketData.assignedSession && ticketData.assignedSession.includes("Multiple venues") ? (
                                        <p className="text-base font-medium mt-2">See venue options below</p>
                                    ) : (
                                        <>
                                            {/* Venue and Address */}
                                            {ticketData.assignedVenue && (
                                                <div className="mb-2">
                                                    <p className="text-base font-medium">{ticketData.assignedVenue}</p>
                                                    {ticketData.venueAddress && (
                                                        <p className="text-sm">{ticketData.venueAddress}</p>
                                                    )}
                                                </div>
                                            )}

                                            {/* Date */}
                                            {ticketData.assignedDate && (
                                                <p className="text-base mb-1">{ticketData.assignedDate}</p>
                                            )}

                                            {/* Session Time */}
                                            {ticketData.assignedSession && (
                                                <p className="text-lg font-medium mb-1">Meeting starts: {ticketData.assignedSession}</p>
                                            )}

                                            {/* Travel Time Span */}
                                            {ticketData.timeSpan && (
                                                <p className="text-sm">Travel time span: {ticketData.timeSpan}</p>
                                            )}
                                        </>
                                    )}
                                </div>
                            </div>

                            <div className="p-6">
                                <div className="mb-4">
                                    <p className="text-gray-600 dark:text-gray-400 text-sm">Name:</p>
                                    <p className="text-xl font-bold text-gray-900 dark:text-white">{ticketData.name}</p>
                                </div>

                                <div className="mb-4">
                                    <p className="text-gray-600 dark:text-gray-400 text-sm">Membership Number:</p>
                                    <p className="text-xl font-bold text-gray-900 dark:text-white">{ticketData.membershipNumber}</p>
                                </div>

                                <div className="mb-6">
                                    <p className="text-gray-600 dark:text-gray-400 text-sm">Region:</p>
                                    <p className="text-lg font-semibold text-gray-900 dark:text-white">{ticketData.region}</p>
                                </div>

                                {/* Stage 1 Registration Information */}
                                {(ticketData.preferredTimesJson || ticketData.workplaceInfo) && (
                                    <div className="mb-6 p-4 bg-gray-50 dark:bg-gray-700 rounded-lg">
                                        <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">Your Registration Details</h3>

                                        {ticketData.preferredTimesJson && (
                                            <div className="mb-2">
                                                <p className="text-xs text-gray-600 dark:text-gray-400">Preferred Times:</p>
                                                <p className="text-sm text-gray-900 dark:text-white">
                                                    {(() => {
                                                        try {
                                                            const times = JSON.parse(ticketData.preferredTimesJson);
                                                            const timeLabels: {[key: string]: string} = {
                                                                'morning': 'Morning',
                                                                'lunchtime': 'Lunchtime',
                                                                'afternoon': 'Afternoon',
                                                                'after_work': 'After Work',
                                                                'night_shift': 'Night Shift'
                                                            };
                                                            return times.map((time: string) => timeLabels[time] || time).join(', ');
                                                        } catch {
                                                            return 'Not specified';
                                                        }
                                                    })()}
                                                </p>
                                            </div>
                                        )}

                                        {ticketData.workplaceInfo && (
                                            <div className="mb-2">
                                                <p className="text-xs text-gray-600 dark:text-gray-400">Workplace:</p>
                                                <p className="text-sm text-gray-900 dark:text-white">{ticketData.workplaceInfo}</p>
                                            </div>
                                        )}
                                    </div>
                                )}

                                {/* Show venue options for forumVenueMapping members */}
                                {ticketData.assignedSession && ticketData.assignedSession.includes("Multiple venues") && (
                                    <div className="mb-6 p-4 bg-blue-50 dark:bg-blue-900/30 rounded-lg">
                                        <h3 className="text-base font-semibold text-blue-900 dark:text-blue-200 mb-3">📍 Available Venue Options</h3>
                                        {(() => {
                                            // Determine session time based on preferences
                                            let showMorning = false;
                                            let showLunchtime = false;

                                            if (ticketData.preferredTimesJson) {
                                                try {
                                                    const times = JSON.parse(ticketData.preferredTimesJson);
                                                    if (times.includes('morning')) {
                                                        showMorning = true;
                                                    } else if (times.includes('lunchtime')) {
                                                        showLunchtime = true;
                                                    } else {
                                                        showMorning = true;
                                                        showLunchtime = true;
                                                    }
                                                } catch {
                                                    showMorning = true;
                                                    showLunchtime = true;
                                                }
                                            } else {
                                                showMorning = true;
                                                showLunchtime = true;
                                            }

                                            const sessionText = showMorning && showLunchtime ? "10:30 AM or 12:30 PM" :
                                                showMorning ? "10:30 AM" : "12:30 PM";

                                            // Determine which forum this member belongs to
                                            if (ticketData.forumDesc === "Greymouth") {
                                                return (
                                                    <div className="space-y-3 text-sm">
                                                        <div className="pl-4">
                                                            <p className="font-medium text-blue-800 dark:text-blue-300">Option 1: HOKITIKA</p>
                                                            <p className="text-gray-700 dark:text-gray-300">St John Hokitika, 134 Stafford Street, Hokitika 7882</p>
                                                            <p className="text-gray-600 dark:text-gray-400">Wednesday 10 September • {sessionText}</p>
                                                        </div>
                                                        <div className="pl-4">
                                                            <p className="font-medium text-blue-800 dark:text-blue-300">Option 2: REEFTON</p>
                                                            <p className="text-gray-700 dark:text-gray-300">Reefton Cinema, Shiel Street, Reefton 7830</p>
                                                            <p className="text-gray-600 dark:text-gray-400">Thursday 11 September • {sessionText}</p>
                                                        </div>
                                                        <div className="pl-4">
                                                            <p className="font-medium text-blue-800 dark:text-blue-300">Option 3: GREYMOUTH</p>
                                                            <p className="text-gray-700 dark:text-gray-300">Regent Greymouth, 2/6 MacKay Street, Greymouth 7805</p>
                                                            <p className="text-gray-600 dark:text-gray-400">Friday 12 September • {sessionText}</p>
                                                        </div>
                                                    </div>
                                                );
                                            }
                                            return null;
                                        })()}
                                        <p className="text-xs text-gray-600 dark:text-gray-400 mt-3 italic">
                                            {(() => {
                                                // Check preferences again for appropriate message
                                                if (ticketData.preferredTimesJson) {
                                                    try {
                                                        const times = JSON.parse(ticketData.preferredTimesJson);
                                                        if (times.includes('morning')) {
                                                            return "✓ You can choose any venue above for your 10:30 AM session";
                                                        } else if (times.includes('lunchtime')) {
                                                            return "✓ You can choose any venue above for your 12:30 PM session";
                                                        }
                                                    } catch {
                                                        // Fall through to default
                                                    }
                                                }
                                                return "✓ You can choose any venue and session time above";
                                            })()}
                                        </p>
                                    </div>
                                )}

                                {ticketData.specialVoteRequested && (
                                    <div className="mb-6 p-3 bg-green-50 dark:bg-green-900/30 rounded-lg">
                                        <p className="text-sm font-medium text-green-800 dark:text-green-200">
                                            ✓ Special Vote Applied - {ticketData.region}
                                        </p>
                                    </div>
                                )}

                                <div className="flex justify-center mb-6">
                                    <QRCodeSVG
                                        value={JSON.stringify({
                                            token: ticketData.memberToken || token,
                                            membershipNumber: ticketData.membershipNumber,
                                            name: ticketData.name,
                                            type: 'event_checkin',
                                            checkinUrl: `https://events.etu.nz/api/checkin/${ticketData.memberToken || token}`
                                        })}
                                        size={200}
                                        level="H"
                                        marginSize={4}
                                    />
                                </div>

                                <div className="text-center">
                                    <p className="text-sm font-medium text-gray-900 dark:text-white">Scan this QR code for quick check-in at the venue</p>
                                    <p className="text-xs mt-2 text-gray-600 dark:text-gray-400">Ticket ID: {ticketData.ticketToken}</p>
                                </div>
                            </div>
                        </div>

                        <div className="flex flex-wrap justify-center gap-3 mb-6">
                            <button
                                onClick={handlePrint}
                                className="bg-orange-500 hover:bg-orange-600 text-white flex items-center py-2 px-4 rounded transition-colors text-sm"
                            >
                                <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
                                </svg>
                                Print
                            </button>

                            <button
                                onClick={handleSaveAsImage}
                                className="bg-blue-500 hover:bg-blue-600 text-white flex items-center py-2 px-4 rounded transition-colors text-sm"
                            >
                                <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                </svg>
                                Save Image
                            </button>

                            <button
                                onClick={handleCopyLink}
                                className="bg-green-500 hover:bg-green-600 text-white flex items-center py-2 px-4 rounded transition-colors text-sm"
                            >
                                <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
                                </svg>
                                Copy Link
                            </button>

                            <button
                                onClick={handleAddToCalendar}
                                className="bg-orange-500 hover:bg-orange-600 text-white flex items-center py-2 px-4 rounded transition-colors text-sm"
                            >
                                <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                </svg>
                                Add to Calendar
                            </button>
                        </div>

                        <div className="bg-green-50 dark:bg-green-900/30 rounded-lg p-4 mb-4">
                            <h3 className="font-semibold text-green-900 dark:text-green-200 mb-3 text-center">✅ Your BMM Ticket Has Been Generated!</h3>
                            <p className="text-sm text-green-800 dark:text-green-300 mb-3">
                                <strong>Important:</strong> Please save your ticket now using one of these methods:
                            </p>
                            <ul className="text-sm text-green-700 dark:text-green-300 space-y-2 ml-4">
                                <li>📱 <strong>Save Image to Phone:</strong> Click "Save Image" to download the ticket to your phone's photo gallery for offline access</li>
                                <li>🖨️ <strong>Print Physical Copy:</strong> Click "Print" to print a hard copy of your ticket (recommended as backup)</li>
                                <li>🔗 <strong>Save Link:</strong> Click "Copy Link" to save the ticket URL - bookmark it or save in notes</li>
                                <li>📧 <strong>Check Email:</strong> A copy has been sent to your registered email address</li>
                            </ul>
                            <p className="text-xs text-green-600 dark:text-green-400 mt-3 italic text-center">
                                💡 Tip: Take a screenshot now or save the image to ensure you have your ticket for the meeting
                            </p>
                        </div>

                        <div className="bg-blue-50 dark:bg-blue-900/30 rounded-lg p-4 text-center">
                            <h3 className="font-semibold text-blue-900 dark:text-blue-200 mb-2">📋 Check-in Instructions</h3>
                            <ul className="text-sm text-blue-800 dark:text-blue-300 space-y-1">
                                <li>• Arrive 15 minutes before the meeting time</li>
                                <li>• Present this ticket at the venue registration desk</li>
                                <li>• Show the QR code on your phone or printed ticket</li>
                                <li>• Staff will scan your QR code to confirm attendance</li>
                                <li>• Bring valid ID if requested</li>
                            </ul>
                            <p className="text-xs text-blue-600 dark:text-blue-400 mt-3 italic">
                                Present at venue for scan to confirm attendance
                            </p>
                        </div>
                    </div>
                </div>
            </Layout>
        );
    }

    // Regular Member Display (fallback)
    return (
        <Layout>
            <div className="container mx-auto px-4 py-12">
                <div className="max-w-md mx-auto">
                    <h1 className="text-3xl font-bold text-black dark:text-white mb-6 text-center">
                        My Event Ticket
                    </h1>

                    <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg overflow-hidden mb-6" ref={ticketRef}>
                        <div className="bg-purple-900 text-white p-4 text-center">
                            <h2 className="text-xl font-bold">E tu Event System</h2>
                            <p>Member Event Ticket</p>
                        </div>

                        <div className="p-6">
                            <div className="mb-6">
                                <p className="text-gray-600 dark:text-gray-400 mb-1">Name:</p>
                                <p className="text-lg font-semibold text-gray-900 dark:text-white">{memberData?.name}</p>
                            </div>

                            <div className="mb-6">
                                <p className="text-gray-600 dark:text-gray-400 mb-1">Membership Number:</p>
                                <p className="text-lg font-semibold text-gray-900 dark:text-white">{memberData?.membershipNumber}</p>
                            </div>

                            <div className="flex justify-center mb-6">
                                <QRCodeSVG
                                    value={JSON.stringify({
                                        token: token,
                                        membershipNumber: memberData?.membershipNumber,
                                        name: memberData?.name,
                                        type: 'event_checkin',
                                        checkinUrl: `https://events.etu.nz/api/checkin/${token}`
                                    })}
                                    size={200}
                                    level="H"
                                    marginSize={4}
                                />
                            </div>

                            <div className="text-center text-sm text-gray-600 dark:text-gray-400">
                                <p>This ticket is for your reference</p>
                                <p>Scan the QR code to confirm your attendance</p>
                            </div>
                        </div>
                    </div>

                    <div className="flex flex-wrap justify-center gap-3 mb-6">
                        <button
                            onClick={handlePrint}
                            className="bg-orange-500 hover:bg-orange-600 text-white flex items-center py-2 px-4 rounded transition-colors text-sm"
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
                            </svg>
                            Print
                        </button>

                        <button
                            onClick={handleSaveAsImage}
                            className="bg-blue-500 hover:bg-blue-600 text-white flex items-center py-2 px-4 rounded transition-colors text-sm"
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                            </svg>
                            Save Image
                        </button>

                        <button
                            onClick={handleCopyLink}
                            className="bg-green-500 hover:bg-green-600 text-white flex items-center py-2 px-4 rounded transition-colors text-sm"
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
                            </svg>
                            Copy Link
                        </button>
                    </div>
                </div>
            </div>
        </Layout>
    );
}