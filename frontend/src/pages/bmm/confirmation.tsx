'use client';
import React, { useState, useEffect, useRef } from 'react';
import { useRouter } from 'next/router';
import Layout from '../../components/common/Layout';
import { toast } from 'react-toastify';
import api from '@/services/api';
import { QRCodeSVG } from 'qrcode.react';
import html2canvas from 'html2canvas';
import { useReactToPrint } from 'react-to-print';

// Venues with only specific time slots
const SINGLE_SESSION_VENUES: Record<string, string> = {
    'Gisborne': '12:30 PM',  // Only lunchtime
    'Nelson': '12:30 PM'     // Only lunchtime
};

// Venues with only morning + lunchtime (no afternoon)
const TWO_SESSION_VENUES_MORNING_LUNCH = [
    'Auckland North Shore', 'Auckland West', 'Manukau 3', 'Pukekohe', 'Whangarei',
    'Christchurch 2', 'Rotorua', 'Hamilton 1', 'Hamilton 2', 'Tauranga', 'Dunedin',
    'Invercargill', 'New Plymouth', 'Timaru', 'Christchurch 1', 'Wellington 1',
    'Palmerston North', 'Wellington 2'
];

// Venues with only morning + afternoon (no lunchtime)
const TWO_SESSION_VENUES_MORNING_AFTERNOON = ['Napier'];

// Helper function to get assigned time based on venue and preferences
const getAssignedTime = (forumDesc: string, preferredTimesJson?: string): string => {
    // Check if venue has only one session
    if (SINGLE_SESSION_VENUES[forumDesc]) {
        return SINGLE_SESSION_VENUES[forumDesc];
    }

    // Process preferences if available
    if (preferredTimesJson) {
        try {
            const times = JSON.parse(preferredTimesJson);

            // Check venue-specific constraints
            if (TWO_SESSION_VENUES_MORNING_AFTERNOON.includes(forumDesc)) {
                // Venues with only morning + afternoon (no lunchtime) - e.g., Napier
                if (times.includes('morning')) return '10:30 AM';
                if (times.includes('afternoon') || times.includes('after work') || times.includes('night shift')) return '2:30 PM';
                if (times.includes('lunchtime')) {
                    // Lunchtime preference but not available, default to morning
                    return '10:30 AM';
                }
                return '10:30 AM'; // Default to morning
            }
            
            if (TWO_SESSION_VENUES_MORNING_LUNCH.includes(forumDesc)) {
                // Venues with only morning + lunchtime (no afternoon) - e.g., Palmerston North
                if (times.includes('morning')) return '10:30 AM';
                if (times.includes('lunchtime')) return '12:30 PM';
                if (times.includes('afternoon') || times.includes('after work') || times.includes('night shift')) {
                    // Afternoon preference but not available, default to lunchtime
                    return '12:30 PM';
                }
                return '10:30 AM'; // Default to morning
            }

            // Three-session venues (Manukau 1, Manukau 2, Auckland Central) - standard mapping
            if (times.includes('morning')) return '10:30 AM';
            if (times.includes('lunchtime')) return '12:30 PM';
            if (times.includes('afternoon') || times.includes('after work') || times.includes('night shift')) return '2:30 PM';

            // If preference exists but doesn't match any time, default to 12:30 PM
            return '12:30 PM';
        } catch {
            // Error parsing preferences
        }
    }

    // No preference selected: default to 10:30 AM for multi-session venues
    return '10:30 AM';
};

// Helper function to calculate time span based on bmm-venues-config.json
const getTimeSpan = (sessionTime: string): string => {
    const timeMap: Record<string, string> = {
        '10:30 AM': '10:00 AM - 12:00 PM',
        '12:30 PM': '12:00 PM - 2:00 PM',
        '2:30 PM': '2:00 PM - 4:00 PM'
    };
    return timeMap[sessionTime] || '10:00 AM - 12:00 PM';
};

// Helper function to format ICS date
const formatICSDate = (date: Date) => {
    return date.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
};

interface MemberData {
    id: number;
    membershipNumber: string;
    name: string;
    email: string;
    primaryEmail?: string;
    telephoneMobile: string;
    mobile?: string;
    regionDesc: string;
    bmmStage: string;
    assignedVenueFinal?: string | any;
    assignedVenue?: string | any;
    assignedDatetimeFinal?: string;
    assignedDateTime?: string;
    venueAssignedAt?: string;
    venueAddress?: string;
    specialVoteEligible: boolean;
    isAttending?: boolean;
    absenceReason?: string;
    specialVoteRequested?: boolean;
    specialVoteApplicationReason?: string;
    // Stage 1 preferences
    preferredVenuesJson?: string;
    preferredTimesJson?: string;
    preferredAttending?: boolean;
    workplaceInfo?: string;
    suggestedVenue?: string;
    additionalComments?: string;
    forumDesc?: string;
    // Additional fields for form mapping
    postalAddress?: string;
    address?: string;
    homeAddress?: string;
    phoneHome?: string;
    homeTelephone?: string;
    phoneWork?: string;
    workTelephone?: string;
    dob?: string;
    dateOfBirth?: string;
    employerName?: string;
    employer?: string;
    workplaceDesc?: string;
    payrollNumber?: string;
    payrollNo?: string;
    siteCode?: string;
    siteNumber?: string;
    employmentStatus?: string;
    department?: string;
    departmentDesc?: string;
    jobTitle?: string;
    occupation?: string;
    location?: string;
    workplace?: string;
}

interface FinancialFormData {
    name: string;
    primaryEmail: string;
    telephoneMobile: string;
    address: string;
    phoneHome: string;
    phoneWork: string;
    dob: string;
    employer: string;
    payrollNumber: string;
    siteCode: string;
    employmentStatus: string;
    department: string;
    jobTitle: string;
    location: string;
}

export default function BMMConfirmationPage() {
    const router = useRouter();
    const token = router.query.token as string || '';
    const [memberData, setMemberData] = useState<MemberData | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [error, setError] = useState<string>('');
    const [attendanceChoice, setAttendanceChoice] = useState<'attending' | 'not_attending' | ''>('');
    const [absenceReason, setAbsenceReason] = useState('');
    const [specialVoteReason, setSpecialVoteReason] = useState('');
    const [otherReason, setOtherReason] = useState('');
    const [currentStep, setCurrentStep] = useState(1); // 1: Financial Form, 2: Attendance Choice, 3: Special Vote (if applicable)
    const [formSubmitted, setFormSubmitted] = useState(false);
    const [showThankYou, setShowThankYou] = useState(false);
    const [ticketData, setTicketData] = useState<any>(null);
    const ticketRef = useRef<HTMLDivElement>(null);

    // Financial form state
    const [financialForm, setFinancialForm] = useState<FinancialFormData>({
        name: '',
        primaryEmail: '',
        telephoneMobile: '',
        address: '',
        phoneHome: '',
        phoneWork: '',
        dob: '',
        employer: '',
        payrollNumber: '',
        siteCode: '',
        employmentStatus: '',
        department: '',
        jobTitle: '',
        location: ''
    });

    useEffect(() => {
        if (token) {
            fetchMemberData(token);
        } else if (router.isReady) {
            toast.error('Invalid access. Please use the link from your email.');
            router.push('/');
        }
    }, [token, router]);

    const fetchMemberData = async (memberToken: string) => {
        try {
            setIsLoading(true);
            const response = await api.get(`/event-registration/member/${memberToken}`);

            if (response.data.status === 'success') {
                const member = response.data.data;

                // Log member data to debug venue fields
                console.log('Member data from API:', member);

                // Check if member is from Southern region for special vote eligibility
                const eligibleRegions = ['Southern Region', 'Southern'];
                const isSpecialVoteEligible = eligibleRegions.includes(member.regionDesc);

                // Set special vote eligibility based on region
                setMemberData({
                    ...member,
                    // Ensure venue fields are properly set
                    assignedVenue: member.assignedVenue || member.venue || member.forumDesc,
                    assignedVenueFinal: member.assignedVenueFinal || member.assignedVenue || member.venue,
                    venueAddress: member.venueAddress || member.address || '',
                    assignedDateTime: member.assignedDateTime || member.assignedDatetimeFinal || '',
                    specialVoteEligible: isSpecialVoteEligible
                });

                // Check if member is from cancelled West Coast venues
                const cancelledVenues = ['Greymouth', 'Hokitika', 'Reefton'];
                if (member.forumDesc && cancelledVenues.includes(member.forumDesc)) {
                    // Redirect to special vote request page for cancelled venues
                    router.replace(`/register/special-vote-request?token=${memberToken}`);
                    return;
                }

                // No stage check - allow access anytime with valid token

                // Pre-populate financial form with member data
                setFinancialForm({
                    name: member.name || '',
                    primaryEmail: member.primaryEmail || member.email || '',
                    telephoneMobile: member.telephoneMobile || member.mobile || '',
                    address: member.postalAddress || member.address || member.homeAddress || '',
                    phoneHome: member.phoneHome || member.homeTelephone || '',
                    phoneWork: member.phoneWork || member.workTelephone || '',
                    dob: member.dob || member.dateOfBirth || '',
                    employer: member.employerName || member.employer || member.workplaceInfo || member.workplaceDesc || '',
                    payrollNumber: member.payrollNumber || member.payrollNo || '',
                    siteCode: member.siteCode || member.siteNumber || '',
                    employmentStatus: member.employmentStatus || '',
                    department: member.department || member.departmentDesc || '',
                    jobTitle: member.jobTitle || member.occupation || '',
                    location: member.location || member.workplace || ''
                });

            } else {
                toast.error('Failed to load member information');
                router.push('/');
            }
        } catch (error: any) {
            console.error('Error fetching member data:', error);
            toast.error('Failed to load member information');
            router.push('/');
        } finally {
            setIsLoading(false);
        }
    };

    const handleAttendanceChange = (choice: 'attending' | 'not_attending') => {
        setAttendanceChoice(choice);
        if (choice === 'attending') {
            setAbsenceReason('');
            setSpecialVoteReason('');
        }
    };

    const handleAttendanceConfirmation = async () => {
        setIsSubmitting(true);
        setError('');
        try {
            // Submit attendance confirmation (ticket already generated in Step 1)
            const confirmResponse = await api.post('/bmm/confirm-attendance', {
                memberToken: token,
                isAttending: true
            });

            if (confirmResponse.data.status === 'success') {
                // Send email only if member has valid email
                if (memberData?.primaryEmail &&
                    memberData.primaryEmail.trim() !== '' &&
                    !memberData.primaryEmail.includes('@temp-email.etu.nz')) {
                    try {
                        await api.post(`/admin/ticket-emails/member/${memberData?.id}/generate-and-send`);
                        toast.success('Attendance confirmed! Ticket sent to your email.');
                    } catch (emailError) {
                        console.log('Email sending failed:', emailError);
                        toast.success('Attendance confirmed! Please save your ticket.');
                    }
                } else {
                    toast.success('Attendance confirmed! Please save your ticket.');
                }

                // Show thank you message with complete ticket view
                setShowThankYou(true);
                setCurrentStep(3); // Move to confirmation step to show complete ticket
            }
        } catch (error) {
            console.error('Error confirming attendance:', error);
            toast.error('Failed to confirm attendance');
        } finally {
            setIsSubmitting(false);
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
            link.download = 'BMM-Ticket.png';
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
            if (!ticketData || !memberData) return;

            const eventTitle = ticketData.eventName || '2025 E tū Biennial Membership Meeting';
            const venue = ticketData.assignedVenue || memberData.assignedVenueFinal || memberData.assignedVenue || 'Venue TBA';
            const address = ticketData.venueAddress || memberData.venueAddress || '';
            const assignedTime = getAssignedTime(memberData.forumDesc || '', memberData.preferredTimesJson);

            // Parse date and time
            let startDate = new Date();
            let endDate = new Date();

            if (ticketData.assignedDate && assignedTime) {
                // Parse date like "Monday 1 September"
                const dateParts = ticketData.assignedDate.match(/(\d+)\s+(\w+)/);
                if (dateParts) {
                    const day = parseInt(dateParts[1]);
                    const month = dateParts[2];
                    const year = 2025;
                    startDate = new Date(`${month} ${day}, ${year} ${assignedTime}`);
                    endDate = new Date(startDate.getTime() + 2 * 60 * 60 * 1000); // 2 hours later
                }
            }

            // Create calendar event data
            const icsContent = [
                'BEGIN:VCALENDAR',
                'VERSION:2.0',
                'PRODID:-//E tū Events//BMM Ticket//EN',
                'BEGIN:VEVENT',
                `UID:${ticketData.ticketToken || token}@events.etu.nz`,
                `DTSTAMP:${formatICSDate(new Date())}`,
                `DTSTART:${formatICSDate(startDate)}`,
                `DTEND:${formatICSDate(endDate)}`,
                `SUMMARY:${eventTitle}`,
                `DESCRIPTION:Your ticket: https://events.etu.nz/ticket?token=${token}\\n\\nMembership: ${memberData.membershipNumber}`,
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

    const handlePrint = useReactToPrint({
        contentRef: ticketRef,
        documentTitle: 'My BMM Ticket',
    });

    const handleCopyLink = async () => {
        const ticketUrl = `${window.location.origin}/ticket?token=${ticketData?.ticketToken || token}`;
        try {
            await navigator.clipboard.writeText(ticketUrl);
            toast.success('Ticket link copied to clipboard!');
        } catch (err) {
            // Fallback for browsers that don't support clipboard API
            const textArea = document.createElement('textarea');
            textArea.value = ticketUrl;
            document.body.appendChild(textArea);
            textArea.select();
            document.execCommand('copy');
            document.body.removeChild(textArea);
            toast.success('Ticket link copied to clipboard!');
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (currentStep === 1) {
            // Step 1: Submit financial form first
            setIsSubmitting(true);
            setError('');
            try {
                // Prepare financial form data
                const formData = {
                    name: financialForm.name,
                    primaryEmail: financialForm.primaryEmail,
                    dob: financialForm.dob,
                    postalAddress: financialForm.address,
                    phoneHome: financialForm.phoneHome,
                    telephoneMobile: financialForm.telephoneMobile,
                    phoneWork: financialForm.phoneWork,
                    employer: financialForm.employer,
                    payrollNumber: financialForm.payrollNumber,
                    siteCode: financialForm.siteCode,
                    employmentStatus: financialForm.employmentStatus,
                    department: financialForm.department,
                    jobTitle: financialForm.jobTitle,
                    location: financialForm.location,
                };

                // Submit financial form data and generate ticket
                const response = await api.post('/bmm/update-financial-form', {
                    memberToken: token,
                    financialForm: formData
                });

                // Generate ticket after updating information
                if (response.data.status === 'success') {
                    // Generate ticket for the member (without sending email yet)
                    // Email will be sent after attendance confirmation in Step 2
                    let ticketGenerated = false;
                    try {
                        const generateResponse = await api.post(`/admin/ticket-emails/member/${memberData?.id}/generate-and-send`);
                        if (generateResponse.data.status === 'success') {
                            ticketGenerated = true;
                        }
                    } catch (error: any) {
                        console.log('Ticket generation response:', error.response?.data);
                        // If ticket already exists, that's OK
                        if (error.response?.status === 409 || error.response?.data?.message?.includes('already exists')) {
                            ticketGenerated = true;
                        }
                    }

                    // Only try to fetch ticket if generation was successful or ticket already exists
                    if (ticketGenerated) {
                        // Wait a bit for the ticket to be ready
                        await new Promise(resolve => setTimeout(resolve, 2000));

                        let retries = 3;
                        while (retries > 0) {
                            try {
                                const ticketResponse = await api.get(`/admin/ticket-emails/bmm-ticket/${token}`);
                                if (ticketResponse.data.status === 'success') {
                                    const ticket = ticketResponse.data.data;
                                    console.log('Ticket data from API:', ticket);

                                    // Extract venue information with better fallbacks
                                    let venue = memberData?.assignedVenueFinal || memberData?.assignedVenue || memberData?.forumDesc || '';
                                    let venueAddress = memberData?.venueAddress || '';
                                    let venueDate = '';

                                    // Map forum names to proper venue information from bmm-venues-config.json
                                    const venueMap: Record<string, {venue: string, address: string, date: string}> = {
                                        'Auckland North Shore': {venue: 'Barfoot & Thompson Netball Centre – Gibson Room', address: '44 Northcote Road, Northcote', date: 'Monday 1 September 2025'},
                                        'Auckland West': {venue: 'Trusts Arena – Genesis Lounge', address: '65 Central Park Drive, Henderson', date: 'Wednesday 3 September 2025'},
                                        'Manukau 1': {venue: 'Go Media Stadium – Mount Smart – East Lounge', address: 'Mount Smart Stadium, Penrose', date: 'Thursday 4 September 2025'},
                                        'Manukau 2': {venue: 'Go Media Stadium – Mount Smart – East Lounge', address: 'Mount Smart Stadium, Penrose', date: 'Thursday 4 September 2025'},
                                        'Auckland Central': {venue: 'Alexandra Park – Tasman Room', address: '233 Green Lane West, Epsom, Auckland', date: 'Friday 5 September 2025'},
                                        'Manukau 3': {venue: 'Bruce Pulman Arena', address: 'Pulman Park, 90 Walters Road, Takanini', date: 'Monday 8 September 2025'},
                                        'Pukekohe': {venue: 'Bruce Pulman Arena', address: 'Pulman Park, 90 Walters Road, Takanini', date: 'Monday 8 September 2025'},
                                        'Whangarei': {venue: 'The Barge Showgrounds', address: '474 Maunu Road, Maunu, Whangarei', date: 'Tuesday 9 September 2025'},
                                        'Christchurch 2': {venue: 'Addington Raceway', address: '75 Jack Hinton Drive, Addington, Christchurch', date: 'Tuesday 9 September 2025'},
                                        'Rotorua': {venue: 'Arawa Park Hotel', address: '272 Fenton Street, Rotorua', date: 'Wednesday 10 September 2025'},
                                        'Hamilton 1': {venue: 'Distinction Hamilton Hotel', address: '100 Garnett Avenue, Te Rapa, Hamilton', date: 'Thursday 11 September 2025'},
                                        'Hamilton 2': {venue: 'Distinction Hamilton Hotel', address: '100 Garnett Avenue, Te Rapa, Hamilton', date: 'Thursday 11 September 2025'},
                                        'Tauranga': {venue: 'Papamoa Community Centre – Aihe Room', address: '15 Gravatt Road, Papamoa', date: 'Friday 12 September 2025'},
                                        'Dunedin': {venue: 'Harbourview Lounge – Edgar Centre', address: '116 Portsmouth Drive, South Dunedin 9012', date: 'Monday 15 September 2025'},
                                        'Invercargill': {venue: 'Invercargill Workingmen\'s Club – North Lounge', address: '154 Esk Street, Invercargill 9810', date: 'Tuesday 16 September 2025'},
                                        'Timaru': {venue: 'Caroline Bay Hall', address: 'Timaru Port, Timaru 7910', date: 'Wednesday 17 September 2025'},
                                        'Christchurch 1': {venue: 'Woolston Club', address: '43 Hargood Street, Christchurch 8062', date: 'Friday 19 September 2025'},
                                        'Nelson': {venue: 'Club Waimea', address: '345 Lower Queen Street, Richmond 7020', date: 'Monday 22 September 2025'},
                                        'Wellington 1': {venue: 'Te Rauparaha Arena – Small Stadium', address: '17 Parumoana Street, Porirua 5022', date: 'Monday 22 September 2025'},
                                        'Palmerston North': {venue: 'Palmy Conference & Function Centre', address: '354 Main Street, Palmerston North Central, Palmerston North 4410', date: 'Tuesday 23 September 2025'},
                                        'Napier': {venue: 'Hawkes Bay Cricket Association', address: '35 Jull Street, Nelson Park, Napier', date: 'Thursday 25 September 2025'},
                                        'Gisborne': {venue: 'Gisborne Cosmopolitan Club', address: '190 Derby Street, Gisborne 4010', date: 'Friday 26 September 2025'},
                                        'Wellington 2': {venue: 'Silverstream Retreat – Boardwalk Complex', address: '3 Reynolds Bach Drive, Lower Hutt 5019', date: 'Monday 29 September 2025'},
                                        'New Plymouth': {venue: 'Theatre Royal (TSB Showplace)', address: '92-100 Devon Street West, New Plymouth', date: 'Tuesday 30 September 2025'}
                                    };

                                    // If venue is just a forum name, get full details from map
                                    if (venueMap[venue]) {
                                        const venueInfo = venueMap[venue];
                                        venue = venueInfo.venue;
                                        venueAddress = venueAddress || venueInfo.address;
                                        venueDate = venueDate || venueInfo.date;
                                    }

                                    // Handle if venue is an object
                                    if (venue && typeof venue === 'object') {
                                        const venueObj = venue as any;
                                        venue = venueObj['venue'] || venueObj['name'] || ticket.assignedVenue || '';
                                        venueAddress = venueObj['address'] || venueAddress || ticket.venueAddress || '';
                                        venueDate = venueObj['date'] || ticket.assignedDate || '';
                                    }

                                    // Add/update fields in ticket data with all available information
                                    const assignedTime = getAssignedTime(memberData?.forumDesc || '', memberData?.preferredTimesJson);
                                    ticket.name = ticket.name || ticket.memberName || memberData?.name;
                                    ticket.membershipNumber = ticket.membershipNumber || memberData?.membershipNumber;
                                    ticket.assignedVenue = ticket.assignedVenue || venue;
                                    ticket.venueAddress = ticket.venueAddress || venueAddress;
                                    ticket.assignedDate = ticket.assignedDate || venueDate;
                                    ticket.assignedSession = ticket.assignedSession || assignedTime;
                                    ticket.timeSpan = getTimeSpan(assignedTime);
                                    ticket.forumDesc = memberData?.forumDesc;
                                    ticket.regionDesc = memberData?.regionDesc || ticket.regionDesc;

                                    console.log('Final ticket data:', ticket);
                                    setTicketData(ticket);
                                    break;
                                }
                            } catch (error) {
                                console.error('Error fetching ticket:', error);
                                retries--;
                                if (retries === 0) {
                                    console.log('Could not fetch ticket data after retries, using memberData instead');
                                    // Create ticket data from memberData if API fails
                                    const assignedTime = getAssignedTime(memberData?.forumDesc || '', memberData?.preferredTimesJson);
                                    const fallbackTicket = {
                                        memberName: memberData?.name,
                                        membershipNumber: memberData?.membershipNumber,
                                        eventName: '2025 E tū Biennial Membership Meeting',
                                        assignedVenue: memberData?.forumDesc || 'Venue to be confirmed',
                                        venueAddress: '',
                                        assignedDate: '',
                                        assignedSession: assignedTime,
                                        timeSpan: getTimeSpan(assignedTime),
                                        ticketToken: token,
                                        forumDesc: memberData?.forumDesc,
                                        regionDesc: memberData?.regionDesc
                                    };

                                    // Use same venue map as above for consistency
                                    const venueMap: Record<string, {venue: string, address: string, date: string}> = {
                                        'Auckland North Shore': {venue: 'Barfoot & Thompson Netball Centre – Gibson Room', address: '44 Northcote Road, Northcote', date: 'Monday 1 September 2025'},
                                        'Auckland West': {venue: 'Trusts Arena – Genesis Lounge', address: '65 Central Park Drive, Henderson', date: 'Wednesday 3 September 2025'},
                                        'Manukau 1': {venue: 'Go Media Stadium – Mount Smart – East Lounge', address: 'Mount Smart Stadium, Penrose', date: 'Thursday 4 September 2025'},
                                        'Manukau 2': {venue: 'Go Media Stadium – Mount Smart – East Lounge', address: 'Mount Smart Stadium, Penrose', date: 'Thursday 4 September 2025'},
                                        'Auckland Central': {venue: 'Alexandra Park – Tasman Room', address: '233 Green Lane West, Epsom, Auckland', date: 'Friday 5 September 2025'},
                                        'Manukau 3': {venue: 'Bruce Pulman Arena', address: 'Pulman Park, 90 Walters Road, Takanini', date: 'Monday 8 September 2025'},
                                        'Pukekohe': {venue: 'Bruce Pulman Arena', address: 'Pulman Park, 90 Walters Road, Takanini', date: 'Monday 8 September 2025'},
                                        'Whangarei': {venue: 'The Barge Showgrounds', address: '474 Maunu Road, Maunu, Whangarei', date: 'Tuesday 9 September 2025'},
                                        'Christchurch 2': {venue: 'Addington Raceway', address: '75 Jack Hinton Drive, Addington, Christchurch', date: 'Tuesday 9 September 2025'},
                                        'Rotorua': {venue: 'Arawa Park Hotel', address: '272 Fenton Street, Rotorua', date: 'Wednesday 10 September 2025'},
                                        'Hamilton 1': {venue: 'Distinction Hamilton Hotel', address: '100 Garnett Avenue, Te Rapa, Hamilton', date: 'Thursday 11 September 2025'},
                                        'Hamilton 2': {venue: 'Distinction Hamilton Hotel', address: '100 Garnett Avenue, Te Rapa, Hamilton', date: 'Thursday 11 September 2025'},
                                        'Tauranga': {venue: 'Papamoa Community Centre – Aihe Room', address: '15 Gravatt Road, Papamoa', date: 'Friday 12 September 2025'},
                                        'Dunedin': {venue: 'Harbourview Lounge – Edgar Centre', address: '116 Portsmouth Drive, South Dunedin 9012', date: 'Monday 15 September 2025'},
                                        'Invercargill': {venue: 'Invercargill Workingmen\'s Club – North Lounge', address: '154 Esk Street, Invercargill 9810', date: 'Tuesday 16 September 2025'},
                                        'Timaru': {venue: 'Caroline Bay Hall', address: 'Timaru Port, Timaru 7910', date: 'Wednesday 17 September 2025'},
                                        'Christchurch 1': {venue: 'Woolston Club', address: '43 Hargood Street, Christchurch 8062', date: 'Friday 19 September 2025'},
                                        'Nelson': {venue: 'Club Waimea', address: '345 Lower Queen Street, Richmond 7020', date: 'Monday 22 September 2025'},
                                        'Wellington 1': {venue: 'Te Rauparaha Arena – Small Stadium', address: '17 Parumoana Street, Porirua 5022', date: 'Monday 22 September 2025'},
                                        'Palmerston North': {venue: 'Palmy Conference & Function Centre', address: '354 Main Street, Palmerston North Central, Palmerston North 4410', date: 'Tuesday 23 September 2025'},
                                        'Napier': {venue: 'Hawkes Bay Cricket Association', address: '35 Jull Street, Nelson Park, Napier', date: 'Thursday 25 September 2025'},
                                        'Gisborne': {venue: 'Gisborne Cosmopolitan Club', address: '190 Derby Street, Gisborne 4010', date: 'Friday 26 September 2025'},
                                        'Wellington 2': {venue: 'Silverstream Retreat – Boardwalk Complex', address: '3 Reynolds Bach Drive, Lower Hutt 5019', date: 'Monday 29 September 2025'},
                                        'New Plymouth': {venue: 'Theatre Royal (TSB Showplace)', address: '92-100 Devon Street West, New Plymouth', date: 'Tuesday 30 September 2025'}
                                    };

                                    if (memberData?.forumDesc && venueMap[memberData.forumDesc]) {
                                        const venueInfo = venueMap[memberData.forumDesc];
                                        fallbackTicket.assignedVenue = venueInfo.venue;
                                        fallbackTicket.venueAddress = venueInfo.address;
                                        fallbackTicket.assignedDate = venueInfo.date;
                                    }

                                    setTicketData(fallbackTicket);
                                }
                            }
                        }
                    }

                    if (!ticketGenerated) {
                        // If ticket generation failed completely, create a basic ticket structure
                        console.log('Creating fallback ticket data');
                        const assignedTime = getAssignedTime(memberData?.forumDesc || '', memberData?.preferredTimesJson);
                        const fallbackTicket = {
                            memberName: memberData?.name,
                            name: memberData?.name,
                            membershipNumber: memberData?.membershipNumber,
                            eventName: '2025 E tū Biennial Membership Meeting',
                            assignedVenue: memberData?.forumDesc || 'Venue to be confirmed',
                            venueAddress: '',
                            assignedDate: '',
                            assignedSession: assignedTime,
                            timeSpan: getTimeSpan(assignedTime),
                            ticketToken: token,
                            forumDesc: memberData?.forumDesc,
                            regionDesc: memberData?.regionDesc,
                            region: memberData?.regionDesc
                        };

                        // Use venue map to get full details
                        const venueMap: Record<string, {venue: string, address: string, date: string}> = {
                            'Auckland North Shore': {venue: 'Barfoot & Thompson Netball Centre – Gibson Room', address: '44 Northcote Road, Northcote', date: 'Monday 1 September 2025'},
                            'Auckland West': {venue: 'Trusts Arena – Genesis Lounge', address: '65 Central Park Drive, Henderson', date: 'Wednesday 3 September 2025'},
                            'Manukau 1': {venue: 'Go Media Stadium – Mount Smart – East Lounge', address: 'Mount Smart Stadium, Penrose', date: 'Thursday 4 September 2025'},
                            'Manukau 2': {venue: 'Go Media Stadium – Mount Smart – East Lounge', address: 'Mount Smart Stadium, Penrose', date: 'Thursday 4 September 2025'},
                            'Auckland Central': {venue: 'Alexandra Park – Tasman Room', address: '233 Green Lane West, Epsom, Auckland', date: 'Friday 5 September 2025'},
                            'Manukau 3': {venue: 'Bruce Pulman Arena', address: 'Pulman Park, 90 Walters Road, Takanini', date: 'Monday 8 September 2025'},
                            'Pukekohe': {venue: 'Bruce Pulman Arena', address: 'Pulman Park, 90 Walters Road, Takanini', date: 'Monday 8 September 2025'},
                            'Whangarei': {venue: 'The Barge Showgrounds', address: '474 Maunu Road, Maunu, Whangarei', date: 'Tuesday 9 September 2025'},
                            'Christchurch 2': {venue: 'Addington Raceway', address: '75 Jack Hinton Drive, Addington, Christchurch', date: 'Tuesday 9 September 2025'},
                            'Rotorua': {venue: 'Arawa Park Hotel', address: '272 Fenton Street, Rotorua', date: 'Wednesday 10 September 2025'},
                            'Hamilton 1': {venue: 'Distinction Hamilton Hotel', address: '100 Garnett Avenue, Te Rapa, Hamilton', date: 'Thursday 11 September 2025'},
                            'Hamilton 2': {venue: 'Distinction Hamilton Hotel', address: '100 Garnett Avenue, Te Rapa, Hamilton', date: 'Thursday 11 September 2025'},
                            'Tauranga': {venue: 'Papamoa Community Centre – Aihe Room', address: '15 Gravatt Road, Papamoa', date: 'Friday 12 September 2025'},
                            'Dunedin': {venue: 'Harbourview Lounge – Edgar Centre', address: '116 Portsmouth Drive, South Dunedin 9012', date: 'Monday 15 September 2025'},
                            'Invercargill': {venue: 'Invercargill Workingmen\'s Club – North Lounge', address: '154 Esk Street, Invercargill 9810', date: 'Tuesday 16 September 2025'},
                            'Timaru': {venue: 'Caroline Bay Hall', address: 'Timaru Port, Timaru 7910', date: 'Wednesday 17 September 2025'},
                            'Christchurch 1': {venue: 'Woolston Club', address: '43 Hargood Street, Christchurch 8062', date: 'Friday 19 September 2025'},
                            'Nelson': {venue: 'Club Waimea', address: '345 Lower Queen Street, Richmond 7020', date: 'Monday 22 September 2025'},
                            'Wellington 1': {venue: 'Te Rauparaha Arena – Small Stadium', address: '17 Parumoana Street, Porirua 5022', date: 'Monday 22 September 2025'},
                            'Palmerston North': {venue: 'Palmy Conference & Function Centre', address: '354 Main Street, Palmerston North Central, Palmerston North 4410', date: 'Tuesday 23 September 2025'},
                            'Napier': {venue: 'Hawkes Bay Cricket Association', address: '35 Jull Street, Nelson Park, Napier', date: 'Thursday 25 September 2025'},
                            'Gisborne': {venue: 'Gisborne Cosmopolitan Club', address: '190 Derby Street, Gisborne 4010', date: 'Friday 26 September 2025'},
                            'Wellington 2': {venue: 'Silverstream Retreat – Boardwalk Complex', address: '3 Reynolds Bach Drive, Lower Hutt 5019', date: 'Monday 29 September 2025'},
                            'New Plymouth': {venue: 'Theatre Royal (TSB Showplace)', address: '92-100 Devon Street West, New Plymouth', date: 'Tuesday 30 September 2025'}
                        };

                        if (memberData?.forumDesc && venueMap[memberData.forumDesc]) {
                            const venueInfo = venueMap[memberData.forumDesc];
                            fallbackTicket.assignedVenue = venueInfo.venue;
                            fallbackTicket.venueAddress = venueInfo.address;
                            fallbackTicket.assignedDate = venueInfo.date;
                        }

                        setTicketData(fallbackTicket);
                    }

                    toast.success('Information saved and ticket generated!');

                    // Move to Step 2 after form completion
                    setFormSubmitted(true);
                    setCurrentStep(2);

                    // Auto-scroll to ticket section
                    setTimeout(() => {
                        const ticketElement = document.querySelector('[data-ticket="display"]');
                        if (ticketElement) {
                            ticketElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
                        }
                    }, 100);
                }
            } catch (error) {
                console.error('Error updating form:', error);
                toast.error('Failed to update information');
            } finally {
                setIsSubmitting(false);
            }
            return;
        }


        if (currentStep === 3 && attendanceChoice === 'not_attending') {
            // Handle non-attendance with reason
            if (!absenceReason || (absenceReason === 'other' && !otherReason)) {
                toast.error('Please provide a reason for not attending');
                return;
            }

            // For Central/Southern regions, require special vote decision (but not for "other" reason)
            if (memberData?.specialVoteEligible && absenceReason !== 'other' && !specialVoteReason) {
                toast.error('Please indicate whether you want to request special voting rights');
                return;
            }

            // For "other" reason, automatically set specialVoteReason to 'no' since they can't apply
            if (absenceReason === 'other' && memberData?.specialVoteEligible) {
                setSpecialVoteReason('no');
            }

            setIsSubmitting(true);
            setError('');
            try {
                const requestData: any = {
                    isAttending: false,
                    attendanceChoice: 'not_attending',
                    absenceReason: absenceReason === 'other' ? otherReason : absenceReason,
                };

                // Only add special vote info for Central/Southern regions
                if (memberData?.specialVoteEligible) {
                    requestData.isSpecialVote = specialVoteReason === 'yes';
                }

                // Submit non-attendance (financial data already submitted in Step 1)
                await api.post('/bmm/non-attendance', {
                    memberToken: token,
                    ...requestData
                });

                if (memberData?.specialVoteEligible && specialVoteReason === 'yes') {
                    toast.success('Your special vote has been approved!');
                } else {
                    toast.success('Your response has been recorded.');
                }

                // Show thank you message instead of redirecting
                setShowThankYou(true);
                setIsSubmitting(false);
            } catch (error) {
                console.error('Error submitting response:', error);
                toast.error('Failed to submit response');
                setIsSubmitting(false);
            }
            return;
        }
    };

    if (isLoading && !memberData) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12 text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-purple-500"></div>
                    <p className="mt-4 text-gray-700 dark:text-gray-300">Loading your BMM information...</p>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="min-h-screen bg-gray-50 dark:bg-gray-900 py-8">
                <div className="container mx-auto px-4">
                    <div className="max-w-4xl mx-auto">
                        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-8">
                            {/* Show thank you message when submission is complete */}
                            {showThankYou && currentStep === 3 && attendanceChoice === 'attending' ? (
                                <div>
                                    <div className="text-center mb-8">
                                        <div className="mb-4">
                                            <svg className="mx-auto h-16 w-16 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                                            </svg>
                                        </div>
                                        <h2 className="text-3xl font-bold text-gray-900 dark:text-white mb-4">
                                            Thank You!
                                        </h2>
                                        <p className="text-lg text-gray-700 dark:text-gray-300 mb-2">
                                            Your attendance has been confirmed successfully.
                                        </p>
                                        <p className="text-base text-gray-600 dark:text-gray-400 mb-4">
                                            Here is your ticket for the 2025 E tū Biennial Membership Meeting:
                                        </p>
                                    </div>

                                    {/* Display the complete ticket */}
                                    <div className="max-w-md mx-auto mb-8">
                                        {ticketData ? (
                                            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg overflow-hidden" ref={ticketRef}>
                                                <div className="bg-gradient-to-r from-blue-900 to-blue-700 text-white p-6 text-center">
                                                    <div className="mb-2">
                                                        <h1 className="text-2xl font-bold">E tū Event System</h1>
                                                        <p className="text-lg">Member Event Ticket</p>
                                                    </div>
                                                    <div className="mt-4 border-t border-blue-600 pt-4">
                                                        <h2 className="text-xl font-semibold mb-2">{ticketData.eventName || '2025 E tū Biennial Membership Meeting'}</h2>

                                                        {/* Venue and Address */}
                                                        <div className="mb-2">
                                                            <p className="text-base font-medium">
                                                                {ticketData.assignedVenue || ticketData.forumDesc || 'Venue to be confirmed'}
                                                            </p>
                                                            {ticketData.venueAddress && (
                                                                <p className="text-sm">{ticketData.venueAddress}</p>
                                                            )}
                                                        </div>

                                                        {/* Date */}
                                                        <p className="text-base mb-1">
                                                            {ticketData.assignedDate || 'Date to be confirmed'}
                                                        </p>

                                                        {/* Session Time */}
                                                        <p className="text-lg font-medium mb-1">
                                                            Meeting starts: {ticketData.assignedSession || 'Time to be confirmed'}
                                                        </p>

                                                        {/* Travel Time Span */}
                                                        {ticketData.timeSpan && (
                                                            <p className="text-sm">Travel time span: {ticketData.timeSpan}</p>
                                                        )}
                                                    </div>
                                                </div>

                                                <div className="p-6">
                                                    <div className="mb-4">
                                                        <p className="text-gray-600 dark:text-gray-400 text-sm">Name:</p>
                                                        <p className="text-xl font-bold text-gray-900 dark:text-white">{ticketData.name || ticketData.memberName || memberData?.name}</p>
                                                    </div>

                                                    <div className="mb-4">
                                                        <p className="text-gray-600 dark:text-gray-400 text-sm">Membership Number:</p>
                                                        <p className="text-xl font-bold text-gray-900 dark:text-white">{ticketData.membershipNumber || memberData?.membershipNumber}</p>
                                                    </div>

                                                    <div className="mb-6">
                                                        <p className="text-gray-600 dark:text-gray-400 text-sm">Region:</p>
                                                        <p className="text-lg font-semibold text-gray-900 dark:text-white">{ticketData.region || ticketData.regionDesc || memberData?.regionDesc}</p>
                                                    </div>

                                                    <div className="flex justify-center mb-6">
                                                        <QRCodeSVG
                                                            value={JSON.stringify({
                                                                token: ticketData.ticketToken || ticketData.memberToken || token,
                                                                membershipNumber: ticketData.membershipNumber || memberData?.membershipNumber,
                                                                name: ticketData.name || ticketData.memberName || memberData?.name,
                                                                type: 'event_checkin',
                                                                checkinUrl: `https://events.etu.nz/api/checkin/${ticketData.ticketToken || ticketData.memberToken || token}`
                                                            })}
                                                            size={200}
                                                            level="H"
                                                            marginSize={4}
                                                        />
                                                    </div>

                                                    <div className="text-center">
                                                        <p className="text-sm font-medium text-gray-900 dark:text-white">Scan this QR code for quick check-in at the venue</p>
                                                        <p className="text-xs mt-2 text-gray-600 dark:text-gray-400">Ticket ID: {ticketData.ticketToken || token}</p>
                                                    </div>
                                                </div>
                                            </div>
                                        ) : (
                                            <div className="text-center py-8">
                                                <p className="text-gray-600">Loading ticket details...</p>
                                            </div>
                                        )}
                                    </div>

                                    {/* Save ticket options */}
                                    <div className="flex flex-wrap justify-center gap-4 mb-8">
                                        <button
                                            onClick={handlePrint}
                                            className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700"
                                        >
                                            🖨️ Print Ticket
                                        </button>
                                        <button
                                            onClick={handleSaveAsImage}
                                            className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-green-600 hover:bg-green-700"
                                        >
                                            📱 Save as Image
                                        </button>
                                        <button
                                            onClick={handleCopyLink}
                                            className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-purple-600 hover:bg-purple-700"
                                        >
                                            🔗 Copy Link
                                        </button>
                                        <button
                                            onClick={handleAddToCalendar}
                                            className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-orange-600 hover:bg-orange-700"
                                        >
                                            📅 Add to Calendar
                                        </button>
                                    </div>

                                    <div className="bg-green-50 dark:bg-green-900/30 rounded-lg p-4 text-center">
                                        <p className="text-sm text-green-800 dark:text-green-300">
                                            <strong>Important:</strong> Please save your ticket using one of the options above. You can also view your ticket anytime at{' '}
                                            <a href={`/ticket?token=${ticketData?.ticketToken || token}`} className="underline" target="_blank">
                                                this link
                                            </a>
                                        </p>
                                    </div>

                                    {/* Help Section */}
                                    <div className="max-w-2xl mx-auto mt-8 p-6 bg-gray-50 dark:bg-gray-800 rounded-lg">
                                        <h3 className="font-bold text-lg mb-3 flex items-center justify-center">
                                            <span className="mr-2">ℹ</span> Need help?
                                        </h3>
                                        <p className="text-gray-700 dark:text-gray-300 mb-4 text-center">
                                            If you have any questions about your meeting details, ticket, or need assistance, contact us at:
                                        </p>
                                        <div className="flex flex-col sm:flex-row gap-4 justify-center">
                                            <p className="flex items-center justify-center">
                                                <span className="mr-2">📧</span>
                                                <a href="mailto:support@etu.nz" className="text-blue-600 hover:underline font-semibold">support@etu.nz</a>
                                            </p>
                                            <span className="hidden sm:inline text-gray-400">|</span>
                                            <p className="flex items-center justify-center">
                                                <span className="mr-2">📞</span>
                                                <strong>0800 1 UNION (0800 186 466)</strong>
                                            </p>
                                        </div>
                                        <p className="text-gray-700 dark:text-gray-300 mt-6 text-center">
                                            We look forward to seeing you there and hearing your voice!
                                        </p>
                                        <p className="text-gray-700 dark:text-gray-300 mt-2 font-bold text-center">
                                            Together, we are E tū.
                                        </p>
                                    </div>
                                </div>
                            ) : showThankYou ? (
                                <div className="text-center py-12">
                                    <div className="mb-8">
                                        <svg className="mx-auto h-16 w-16 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                                        </svg>
                                    </div>
                                    <h2 className="text-3xl font-bold text-gray-900 dark:text-white mb-4">
                                        Thank You!
                                    </h2>
                                    <p className="text-lg text-gray-700 dark:text-gray-300 mb-6">
                                        Your response has been recorded successfully.
                                    </p>
                                    {memberData?.specialVoteEligible && specialVoteReason === 'yes' && (
                                        <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-700 rounded-lg p-4 mb-6 max-w-md mx-auto">
                                            <p className="text-sm text-blue-800 dark:text-blue-300">
                                                Your special voting application has been submitted. You will be contacted with further information.
                                            </p>
                                        </div>
                                    )}

                                    {/* Help Section */}
                                    <div className="max-w-2xl mx-auto mt-8 p-6 bg-gray-50 dark:bg-gray-800 rounded-lg">
                                        <h3 className="font-bold text-lg mb-3 flex items-center justify-center">
                                            <span className="mr-2">ℹ</span> Need help?
                                        </h3>
                                        <p className="text-gray-700 dark:text-gray-300 mb-4 text-center">
                                            If you have any questions about your meeting details, ticket, or need assistance, contact us at:
                                        </p>
                                        <div className="flex flex-col sm:flex-row gap-4 justify-center">
                                            <p className="flex items-center justify-center">
                                                <span className="mr-2">📧</span>
                                                <a href="mailto:support@etu.nz" className="text-blue-600 hover:underline font-semibold">support@etu.nz</a>
                                            </p>
                                            <span className="hidden sm:inline text-gray-400">|</span>
                                            <p className="flex items-center justify-center">
                                                <span className="mr-2">📞</span>
                                                <strong>0800 1 UNION (0800 186 466)</strong>
                                            </p>
                                        </div>
                                        <p className="text-gray-700 dark:text-gray-300 mt-6 text-center">
                                            We look forward to seeing you there and hearing your voice!
                                        </p>
                                        <p className="text-gray-700 dark:text-gray-300 mt-2 font-bold text-center">
                                            Together, we are E tū.
                                        </p>
                                    </div>

                                    <button
                                        onClick={() => router.push('/')}
                                        className="inline-flex items-center px-6 py-3 border border-transparent text-base font-medium rounded-md text-white bg-purple-600 hover:bg-purple-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-purple-500"
                                    >
                                        Return to Home
                                    </button>
                                </div>
                            ) : (
                                <>
                                    <h1 className="text-3xl font-bold text-center text-gray-900 dark:text-white mb-8">
                                        Confirm your attendance – 2025 E tū Biennial Membership Meeting
                                    </h1>

                                    {/* Progress Indicator */}
                                    <div className="mb-8">
                                        <div className="flex items-center justify-between">
                                            <div className={`flex items-center ${currentStep >= 1 ? 'text-purple-600' : 'text-gray-400'}`}>
                                                <div className={`w-10 h-10 rounded-full flex items-center justify-center border-2 ${
                                                    currentStep >= 1 ? 'border-purple-600 bg-purple-100' : 'border-gray-300'
                                                }`}>
                                                    {formSubmitted ? '✓' : '1'}
                                                </div>
                                                <span className="ml-2 font-medium">Update Info & Generate Ticket</span>
                                            </div>

                                            <div className={`h-1 flex-1 mx-4 ${currentStep >= 2 ? 'bg-purple-600' : 'bg-gray-300'}`}></div>

                                            <div className={`flex items-center ${currentStep >= 2 ? 'text-purple-600' : 'text-gray-400'}`}>
                                                <div className={`w-10 h-10 rounded-full flex items-center justify-center border-2 ${
                                                    currentStep >= 2 ? 'border-purple-600 bg-purple-100' : 'border-gray-300'
                                                }`}>
                                                    {currentStep > 2 ? '✓' : '2'}
                                                </div>
                                                <span className="ml-2 font-medium">Attendance</span>
                                            </div>

                                            <div className={`h-1 flex-1 mx-4 ${currentStep >= 3 ? 'bg-purple-600' : 'bg-gray-300'}`}></div>

                                            <div className={`flex items-center ${currentStep >= 3 ? 'text-purple-600' : 'text-gray-400'}`}>
                                                <div className={`w-10 h-10 rounded-full flex items-center justify-center border-2 ${
                                                    currentStep >= 3 ? 'border-purple-600 bg-purple-100' : 'border-gray-300'
                                                }`}>
                                                    3
                                                </div>
                                                <span className="ml-2 font-medium">Complete</span>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-6 mb-6">
                                        <p className="text-blue-700 dark:text-blue-300 mb-4">
                                            Thank you for pre-registering your interest in attending the 2025 E tū Biennial Membership Meeting (BMM). Even if you didn't preregister your interest, you are warmly invited to participate in this important moment of union democracy. By confirming your attendance below, you'll help us plan your meeting and ensure we can communicate your participation to your employer.
                                        </p>
                                        <p className="text-blue-700 dark:text-blue-300 font-semibold">
                                            We're now asking you to confirm your attendance at the following meeting - Confirming your attendance will take two minutes of your time.
                                        </p>
                                    </div>

                                    {memberData && (
                                        <div className="bg-orange-50 dark:bg-orange-900/20 rounded-lg p-6 mb-6 border-2 border-orange-200 dark:border-orange-700">
                                            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                                                📍 Meeting Assignment
                                            </h3>

                                            {/* Check if this is a forumVenueMapping member */}
                                            {memberData.forumDesc && memberData.forumDesc === "Greymouth" ? (
                                                <>
                                                    <div className="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4 mb-4">
                                                        {(() => {
                                                            // Determine session time based on preferences
                                                            let sessionTimeText = "";
                                                            let showMorning = false;
                                                            let showLunchtime = false;

                                                            if (memberData.preferredTimesJson) {
                                                                try {
                                                                    const times = JSON.parse(memberData.preferredTimesJson);
                                                                    if (times.includes('morning')) {
                                                                        showMorning = true;
                                                                        sessionTimeText = "Based on your morning preference, here are your venue options for the 10:30 AM session:";
                                                                    } else if (times.includes('lunchtime')) {
                                                                        showLunchtime = true;
                                                                        sessionTimeText = "Based on your lunchtime preference, here are your venue options for the 12:30 PM session:";
                                                                    } else {
                                                                        showMorning = true;
                                                                        showLunchtime = true;
                                                                        sessionTimeText = `As a member of the ${memberData.forumDesc} forum, you can choose from the following venues and session times:`;
                                                                    }
                                                                } catch {
                                                                    showMorning = true;
                                                                    showLunchtime = true;
                                                                    sessionTimeText = `As a member of the ${memberData.forumDesc} forum, you can choose from the following venues and session times:`;
                                                                }
                                                            } else {
                                                                showMorning = true;
                                                                showLunchtime = true;
                                                                sessionTimeText = `As a member of the ${memberData.forumDesc} forum, you can choose from the following venues and session times:`;
                                                            }

                                                            return (
                                                                <>
                                                                    <p className="text-blue-800 dark:text-blue-300 text-sm font-medium mb-3">
                                                                        {sessionTimeText}
                                                                    </p>

                                                                    {memberData.forumDesc === "Greymouth" && (
                                                                        <div className="space-y-3">
                                                                            <div className="bg-white dark:bg-gray-800 rounded-lg p-3">
                                                                                <p className="font-semibold text-gray-900 dark:text-white">Option 1: HOKITIKA</p>
                                                                                <p className="text-sm text-gray-700 dark:text-gray-300">St John Hokitika, 134 Stafford Street, Hokitika 7882</p>
                                                                                <p className="text-sm text-gray-600 dark:text-gray-400">
                                                                                    Wednesday 10 September •
                                                                                    {showMorning && showLunchtime ? "Sessions: 10:30 AM or 12:30 PM" :
                                                                                        showMorning ? "Session: 10:30 AM" : "Session: 12:30 PM"}
                                                                                </p>
                                                                            </div>
                                                                            <div className="bg-white dark:bg-gray-800 rounded-lg p-3">
                                                                                <p className="font-semibold text-gray-900 dark:text-white">Option 2: REEFTON</p>
                                                                                <p className="text-sm text-gray-700 dark:text-gray-300">Reefton Cinema, Shiel Street, Reefton 7830</p>
                                                                                <p className="text-sm text-gray-600 dark:text-gray-400">
                                                                                    Thursday 11 September •
                                                                                    {showMorning && showLunchtime ? "Sessions: 10:30 AM or 12:30 PM" :
                                                                                        showMorning ? "Session: 10:30 AM" : "Session: 12:30 PM"}
                                                                                </p>
                                                                            </div>
                                                                            <div className="bg-white dark:bg-gray-800 rounded-lg p-3">
                                                                                <p className="font-semibold text-gray-900 dark:text-white">Option 3: GREYMOUTH</p>
                                                                                <p className="text-sm text-gray-700 dark:text-gray-300">Regent Greymouth, 2/6 MacKay Street, Greymouth 7805</p>
                                                                                <p className="text-sm text-gray-600 dark:text-gray-400">
                                                                                    Friday 12 September •
                                                                                    {showMorning && showLunchtime ? "Sessions: 10:30 AM or 12:30 PM" :
                                                                                        showMorning ? "Session: 10:30 AM" : "Session: 12:30 PM"}
                                                                                </p>
                                                                            </div>
                                                                        </div>
                                                                    )}

                                                                    {memberData.forumDesc === "Greymouth" && (
                                                                        <p className="text-xs text-gray-600 dark:text-gray-400 mt-3 italic">
                                                                            {showMorning && showLunchtime ?
                                                                                "✓ You can attend ANY of the above venues and choose either session time" :
                                                                                showMorning ? "✓ You can attend ANY of the above venues at your assigned 10:30 AM session" :
                                                                                    "✓ You can attend ANY of the above venues at your assigned 12:30 PM session"}
                                                                        </p>
                                                                    )}
                                                                </>
                                                            );
                                                        })()}
                                                    </div>
                                                </>
                                            ) : (
                                                <div className="text-center mb-4 p-4 bg-white dark:bg-gray-800 rounded-lg">
                                                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">Venue</p>
                                                    <p className="font-bold text-lg text-gray-900 dark:text-white mb-3">
                                                        {(() => {
                                                            let venue = memberData.assignedVenueFinal || memberData.assignedVenue;
                                                            let address = memberData.venueAddress || '';

                                                            // Handle if venue is an object
                                                            if (venue && typeof venue === 'object') {
                                                                // Extract venue name and address from object using bracket notation
                                                                const venueObj = venue as any;
                                                                venue = venueObj['venue'] || venueObj['name'] || 'To be confirmed';
                                                                address = venueObj['address'] || address || '';
                                                            }

                                                            // Format venue display with full address
                                                            if (!venue || venue === 'To be confirmed') return 'To be confirmed';
                                                            if (!address) return venue;
                                                            return `${venue}, ${address}`;
                                                        })()}
                                                    </p>
                                                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">Date</p>
                                                    <p className="font-bold text-lg text-gray-900 dark:text-white mb-3">
                                                        {(() => {
                                                            let venue = memberData.assignedVenueFinal || memberData.assignedVenue;

                                                            // If venue is an object, get the date from it
                                                            if (venue && typeof venue === 'object') {
                                                                const venueObj = venue as any;
                                                                return venueObj.date || 'Date to be confirmed';
                                                            }

                                                            return 'Date to be confirmed';
                                                        })()}
                                                    </p>
                                                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">Session Time</p>
                                                    <p className="font-bold text-lg text-gray-900 dark:text-white">
                                                        {memberData.forumDesc ? getAssignedTime(memberData.forumDesc, memberData.preferredTimesJson) : 'Time not assigned'}
                                                    </p>
                                                </div>
                                            )}

                                            <div className="grid grid-cols-2 gap-4">
                                                <div>
                                                    <p className="text-sm text-gray-600 dark:text-gray-400">Member Name</p>
                                                    <p className="font-medium text-gray-900 dark:text-white">{memberData.name}</p>
                                                </div>
                                                <div>
                                                    <p className="text-sm text-gray-600 dark:text-gray-400">Region</p>
                                                    <p className="font-medium text-gray-900 dark:text-white">{memberData.regionDesc}</p>
                                                </div>
                                            </div>
                                        </div>
                                    )}

                                    {/* Display Stage 1 Preferences - HIDDEN (uncomment to re-enable) */}
                                    {/* {memberData && (memberData.preferredTimesJson || memberData.workplaceInfo || memberData.additionalComments) && (
                                        <div className="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-6 mb-6">
                                            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                                                📋 Your Stage 1 Preferences
                                            </h3>
                                            <div className="space-y-3">
                                                {memberData.preferredTimesJson && (
                                                    <div>
                                                        <p className="text-sm text-gray-600 dark:text-gray-400">Preferred Session Times</p>
                                                        <p className="font-medium text-gray-900 dark:text-white">
                                                            {(() => {
                                                                try {
                                                                    const times = JSON.parse(memberData.preferredTimesJson);
                                                                    const timeLabels: {[key: string]: string} = {
                                                                        'morning': 'Morning Session (9:00 AM - 12:00 PM)',
                                                                        'lunchtime': 'Lunchtime Session (12:00 PM - 2:00 PM)',
                                                                        'afternoon': 'Afternoon Session (2:00 PM - 5:00 PM)',
                                                                        'after_work': 'After Work Session (5:00 PM - 8:00 PM)',
                                                                        'night_shift': 'Night Shift Session'
                                                                    };
                                                                    return times.map((time: string) => timeLabels[time] || time).join(', ');
                                                                } catch {
                                                                    return 'Not specified';
                                                                }
                                                            })()}
                                                        </p>
                                                    </div>
                                                )}
                                                {memberData.preferredAttending !== null && memberData.preferredAttending !== undefined && (
                                                    <div>
                                                        <p className="text-sm text-gray-600 dark:text-gray-400">Initial Intention to Attend</p>
                                                        <p className="font-medium text-gray-900 dark:text-white">
                                                            {memberData.preferredAttending ? '✅ Yes, intended to attend' : '❌ Not sure / Unlikely'}
                                                        </p>
                                                    </div>
                                                )}
                                                {memberData.workplaceInfo && (
                                                    <div>
                                                        <p className="text-sm text-gray-600 dark:text-gray-400">Workplace Information</p>
                                                        <p className="font-medium text-gray-900 dark:text-white">{memberData.workplaceInfo}</p>
                                                    </div>
                                                )}
                                                {memberData.suggestedVenue && (
                                                    <div>
                                                        <p className="text-sm text-gray-600 dark:text-gray-400">Suggested Alternative Venue</p>
                                                        <p className="font-medium text-gray-900 dark:text-white">{memberData.suggestedVenue}</p>
                                                    </div>
                                                )}
                                                {memberData.additionalComments && (
                                                    <div>
                                                        <p className="text-sm text-gray-600 dark:text-gray-400">Additional Comments</p>
                                                        <p className="font-medium text-gray-900 dark:text-white">{memberData.additionalComments}</p>
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    )} */}

                                    {/* Region-specific information before Update Your Information */}
                                    {currentStep === 1 && memberData && (
                                        <div className="mb-8">
                                            {/* Southern Region Info */}
                                            {(memberData.regionDesc === 'Southern Region' || memberData.regionDesc === 'Southern') && (
                                                <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-6 space-y-6">
                                                    <p className="text-gray-700 dark:text-gray-300">
                                                        These meetings are official <strong>paid union meetings under Section 26 of the Employment Relations Act</strong>, and your attendance is fully supported by your union.
                                                    </p>
                                                    <p className="text-gray-600 dark:text-gray-400 italic">
                                                        The time span shown includes the two hours provided for in your collective agreement or under Section 26, inclusive of reasonable travel time. Members are also entitled to all their paid and unpaid breaks and may be away from the site for a slightly longer period.
                                                    </p>

                                                    <div>
                                                        <h4 className="font-bold text-lg mb-3 flex items-center">
                                                            <span className="mr-2">🎟</span> Your attendance ticket
                                                        </h4>
                                                        <p className="text-gray-700 dark:text-gray-300 mb-3">
                                                            Once you confirm your attendance, you will get access to your <strong>personalised ticket</strong> for entry to your BMM.
                                                        </p>
                                                        <ul className="list-disc list-inside space-y-2 text-gray-600 dark:text-gray-400 ml-4">
                                                            <li>This ticket will be sent to you by email (and/or available for download).</li>
                                                            <li><strong>You must bring this ticket with you to the meeting</strong> – either printed or on your phone.</li>
                                                            <li>Your ticket will be used to <strong>register your attendance on the day</strong></li>
                                                            {memberData.regionDesc === 'Southern Region' || memberData.regionDesc === 'Southern' ? (
                                                                <li><strong>Your ticket will be your voting pass.</strong></li>
                                                            ) : null}
                                                        </ul>
                                                    </div>


                                                    <div>
                                                        <h4 className="font-bold text-lg mb-3 flex items-center">
                                                            <span className="mr-2">🗳</span> Voting rights – Southern Region members only
                                                        </h4>
                                                        <p className="text-gray-700 dark:text-gray-300 mb-3">
                                                            If your selected meeting is in the <strong>Southern Region</strong>, your ticket will also serve as your <strong>voting pass</strong> for the election of the <strong>National Executive Southern Region Representative</strong>.
                                                        </p>
                                                        <p className="text-gray-700 dark:text-gray-300 mb-4">
                                                            Only members who <strong>bring their ticket and are present at the meeting</strong> will be eligible to vote.
                                                        </p>

                                                        <div className="bg-blue-50 dark:bg-blue-900/20 p-4 rounded-lg mb-4">
                                                            <h5 className="font-bold mb-2">Southern Region Election Voting Process</h5>
                                                            <p className="text-sm text-gray-700 dark:text-gray-300">
                                                                If you are a member from the Southern Region, attending your Biennial Membership Meeting (BMM) will give you the right to vote for the Southern Region representative on the National Executive. After you present your attendance ticket at the meeting, you will receive a secure voting link via email. You will then have 72 hours to cast your vote for your preferred candidate. Please ensure your contact details are up to date to receive the voting link promptly after the meeting.
                                                            </p>
                                                        </div>

                                                        <div className="bg-yellow-50 dark:bg-yellow-900/20 p-4 rounded-lg">
                                                            <h5 className="font-bold mb-2">Special Vote Application (Important Voting Info only for the Southern Region members)</h5>
                                                            <p className="text-sm text-gray-700 dark:text-gray-300 mb-3">
                                                                If you are <strong>unable to attend</strong> the BMM in your area but <strong>wish to vote in the Regional Representative election</strong>, you may be eligible to apply for a <strong>special vote</strong>.
                                                            </p>
                                                            <p className="text-sm text-gray-700 dark:text-gray-300 mb-2">
                                                                <strong>To qualify</strong>, one of the following must apply to you:
                                                            </p>
                                                            <ul className="list-disc list-inside space-y-1 text-sm text-gray-600 dark:text-gray-400 ml-4 mb-3">
                                                                <li>You have a <strong>disability</strong> that prevents you from fully participating in the meeting</li>
                                                                <li>You are <strong>ill or infirm</strong>, making attendance impossible</li>
                                                                <li>You <strong>live more than 32km</strong> from the meeting venue (as per list above)</li>
                                                                <li>Your <strong>employer requires you to work</strong> during the time of the meeting</li>
                                                                <li>Attending the meeting would cause you <strong>serious hardship or major inconvenience</strong></li>
                                                            </ul>
                                                            <p className="text-sm text-gray-700 dark:text-gray-300 mb-2">
                                                                <strong>Special vote applications must be made at least 14 days before</strong> the start of the BMM at which the secret ballot is to be held.
                                                            </p>
                                                            <p className="text-sm text-gray-700 dark:text-gray-300 mb-2">
                                                                If approved, a ballot paper will be issued to you by the Returning Officer.
                                                            </p>
                                                            <p className="text-sm text-gray-700 dark:text-gray-300">
                                                                <strong>If you have any questions about the voting process, please contact our Returning Officer at</strong> <a href="mailto:returningofficer@etu.nz" className="text-blue-600 hover:underline">returningofficer@etu.nz</a><strong>.</strong>
                                                            </p>
                                                        </div>
                                                    </div>
                                                </div>
                                            )}

                                            {/* Central Region Info - Same as Northern, no voting */}
                                            {(memberData.regionDesc === 'Central Region' || memberData.regionDesc === 'Central') && (
                                                <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-6 space-y-6">
                                                    <p className="text-gray-700 dark:text-gray-300">
                                                        These meetings are official <strong>paid union meetings under Section 26 of the Employment Relations Act</strong>, and your attendance is fully supported by your union.
                                                    </p>
                                                    <p className="text-gray-600 dark:text-gray-400 italic">
                                                        The time span shown includes the two hours provided for in your collective agreement or under Section 26, inclusive of reasonable travel time. Members are also entitled to all their paid and unpaid breaks and may be away from the site for a slightly longer period.
                                                    </p>

                                                    <div>
                                                        <h4 className="font-bold text-lg mb-3 flex items-center">
                                                            <span className="mr-2">🎟</span> Your attendance ticket
                                                        </h4>
                                                        <p className="text-gray-700 dark:text-gray-300 mb-3">
                                                            Once you confirm your attendance, you will get access to your <strong>personalised ticket</strong> for entry to your BMM.
                                                        </p>
                                                        <ul className="list-disc list-inside space-y-2 text-gray-600 dark:text-gray-400 ml-4">
                                                            <li>This ticket will be sent to you by email (and/or available for download).</li>
                                                            <li><strong>You must bring this ticket with you to the meeting</strong> – either printed or on your phone.</li>
                                                            <li>Your ticket will be used to <strong>register your attendance on the day</strong></li>
                                                        </ul>
                                                    </div>
                                                </div>
                                            )}

                                            {/* Greymouth Forum Special Info */}
                                            {memberData.forumDesc === "Greymouth" && (
                                                <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-6 space-y-6">
                                                    <h4 className="font-bold text-lg mb-3">Special Vote Request - 2025 E tū Biennial Membership Meetings –West Coast Members (Hokitika, Reefton & Greymouth)</h4>
                                                    <p className="text-gray-700 dark:text-gray-300 mb-3">
                                                        Due to not enough member interest in <strong>Hokitika</strong>, <strong>Reefton</strong>, or <strong>Greymouth, we are not holding local Biennial Membership Meetings (BMMs) in</strong> these areas in 2025.
                                                    </p>
                                                    <p className="text-gray-700 dark:text-gray-300 mb-4">
                                                        <strong>We deeply regret</strong> not being able to host meetings in your towns this year. However, we are committed to ensuring that every member still has the opportunity to participate in union democracy.
                                                    </p>
                                                    <p className="text-gray-700 dark:text-gray-300 mb-4">
                                                        If you live in these areas and wish to take part in the election of your Regional Representative to the E tū National Executive, you can apply for a <strong>special vote</strong>.
                                                    </p>

                                                    <div className="mb-4">
                                                        <h5 className="font-bold mb-2 flex items-center">
                                                            <span className="mr-2">🗳</span> But You Can Still Vote – Request a Special Vote
                                                        </h5>
                                                        <p className="text-gray-700 dark:text-gray-300 mb-3">
                                                            Although we won't be meeting in person in your area, you are still <strong>eligible to vote</strong> in the election of the National Executive Regional Representative for your region.
                                                        </p>
                                                        <p className="text-gray-700 dark:text-gray-300">
                                                            <strong>If you wish to participate in the election, you can request a special vote by submitting your request</strong> to:<br/>
                                                            <span className="inline-flex items-center mt-2">
                                                                <span className="mr-2">📧</span>
                                                                <a href="mailto:returningofficer@etu.nz" className="text-blue-600 hover:underline">returningofficer@etu.nz</a>
                                                            </span>
                                                        </p>
                                                    </div>

                                                    <div className="mb-4">
                                                        <h5 className="font-bold mb-2 flex items-center">
                                                            <span className="mr-2">🗳</span> What Happens Next?
                                                        </h5>
                                                        <ul className="space-y-2 text-gray-700 dark:text-gray-300">
                                                            <li className="flex items-start">
                                                                <span className="mr-2">✔️</span>
                                                                <span>If your request is approved, we will issue you a <strong>voting pass</strong></span>
                                                            </li>
                                                            <li className="flex items-start">
                                                                <span className="mr-2">✔️</span>
                                                                <span>You will then receive a <strong>secure electronic ballot</strong> via email</span>
                                                            </li>
                                                            <li className="flex items-start">
                                                                <span className="mr-2">✔️</span>
                                                                <span>You can cast your vote online for your <strong>preferred candidate</strong></span>
                                                            </li>
                                                            <li className="flex items-start">
                                                                <span className="mr-2">✔️</span>
                                                                <span>You'll have <strong>72 hours</strong> to complete your vote once it is issued</span>
                                                            </li>
                                                        </ul>
                                                    </div>

                                                    <p className="text-gray-700 dark:text-gray-300 text-sm">
                                                        Special vote applications must be received <strong>at least 14 days before the first BMM date</strong> in September 2025.
                                                    </p>
                                                </div>
                                            )}

                                            {/* Northern Region Info */}
                                            {(memberData.regionDesc === 'Northern Region' || memberData.regionDesc === 'Northern') && (
                                                <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-6 space-y-6">
                                                    <p className="text-gray-700 dark:text-gray-300">
                                                        These meetings are official <strong>paid union meetings</strong> under <strong>Section 26 of the Employment Relations Act 2000</strong>, and your attendance is fully supported by E tū.
                                                    </p>
                                                    <p className="text-gray-600 dark:text-gray-400 italic">
                                                        The time span shown includes the two hours provided for in your collective agreement or under Section 26, inclusive of reasonable travel time. Members are also entitled to all their paid and unpaid breaks and may be away from the site for a slightly longer period.
                                                    </p>

                                                    <div>
                                                        <h4 className="font-bold text-lg mb-3 flex items-center">
                                                            <span className="mr-2">🎟</span> Your Attendance Ticket
                                                        </h4>
                                                        <p className="text-gray-700 dark:text-gray-300 mb-3">
                                                            Once you confirm your attendance, you will get access to your <strong>personalised ticket</strong> for entry to your BMM.
                                                        </p>
                                                        <ul className="list-disc list-inside space-y-2 text-gray-600 dark:text-gray-400 ml-4">
                                                            <li>Your ticket will be available for download and/or print).</li>
                                                            <li>Please bring your ticket with you – printed or on your phone.</li>
                                                            <li>Your ticket will be used to register your attendance and support communication with your employer about your attendance.</li>
                                                        </ul>
                                                    </div>

                                                </div>
                                            )}
                                        </div>
                                    )}

                                    <form onSubmit={handleSubmit} className="space-y-8">
                                        {/* Financial Form - Show for all members in Stage 1 */}
                                        {currentStep === 1 && (
                                            <div>
                                                <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                                                    Update Your Information
                                                </h3>
                                                <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                                                    Please review and update your contact and workplace information:
                                                </p>

                                                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                                    <div className="md:col-span-2">
                                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                            Name
                                                        </label>
                                                        <input
                                                            type="text"
                                                            value={financialForm.name}
                                                            onChange={(e) => setFinancialForm(prev => ({...prev, name: e.target.value}))}
                                                            className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                        />
                                                    </div>

                                                    <div className="md:col-span-2">
                                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                            Current home address
                                                        </label>
                                                        <textarea
                                                            value={financialForm.address}
                                                            onChange={(e) => setFinancialForm(prev => ({...prev, address: e.target.value}))}
                                                            className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                            rows={2}
                                                        />
                                                    </div>

                                                    <div className="md:col-span-2">
                                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                            Employer / site name
                                                            <span className="text-xs text-gray-500 ml-2">(cannot be modified)</span>
                                                        </label>
                                                        <input
                                                            type="text"
                                                            value={financialForm.employer}
                                                            readOnly
                                                            className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none bg-gray-100 dark:bg-gray-800 cursor-not-allowed"
                                                        />
                                                    </div>

                                                    <div>
                                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                            Phone number (home)
                                                        </label>
                                                        <input
                                                            type="tel"
                                                            value={financialForm.phoneHome}
                                                            onChange={(e) => setFinancialForm(prev => ({...prev, phoneHome: e.target.value}))}
                                                            className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                        />
                                                    </div>

                                                    <div>
                                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                            Phone number (work)
                                                        </label>
                                                        <input
                                                            type="tel"
                                                            value={financialForm.phoneWork}
                                                            onChange={(e) => setFinancialForm(prev => ({...prev, phoneWork: e.target.value}))}
                                                            className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                        />
                                                    </div>

                                                    <div>
                                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                            Phone number (mobile)
                                                        </label>
                                                        <input
                                                            type="tel"
                                                            value={financialForm.telephoneMobile}
                                                            onChange={(e) => setFinancialForm(prev => ({...prev, telephoneMobile: e.target.value}))}
                                                            className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                        />
                                                    </div>

                                                    <div>
                                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                            Email address
                                                        </label>
                                                        <input
                                                            type="email"
                                                            value={financialForm.primaryEmail}
                                                            onChange={(e) => setFinancialForm(prev => ({...prev, primaryEmail: e.target.value}))}
                                                            className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                        />
                                                    </div>

                                                    <div>
                                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                            Date of birth
                                                        </label>
                                                        <input
                                                            type="date"
                                                            value={financialForm.dob}
                                                            onChange={(e) => setFinancialForm(prev => ({...prev, dob: e.target.value}))}
                                                            className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                        />
                                                    </div>

                                                    <div>
                                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                            Membership number
                                                            <span className="text-xs text-gray-500 ml-2">(cannot be modified)</span>
                                                        </label>
                                                        <input
                                                            type="text"
                                                            value={memberData?.membershipNumber || ''}
                                                            readOnly
                                                            className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none bg-gray-100 dark:bg-gray-800 cursor-not-allowed"
                                                        />
                                                    </div>

                                                    <div>
                                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                            Payroll number (if known)
                                                        </label>
                                                        <input
                                                            type="text"
                                                            value={financialForm.payrollNumber}
                                                            onChange={(e) => setFinancialForm(prev => ({...prev, payrollNumber: e.target.value}))}
                                                            className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                            placeholder="Enter if known"
                                                        />
                                                    </div>
                                                </div>

                                                {/* Electronic Signature Notice */}
                                                <div className="md:col-span-2 mt-6 p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-700 rounded-lg">
                                                    <p className="text-sm text-blue-800 dark:text-blue-300">
                                                        <strong>📝 Electronic Signature:</strong> By updating your information and submitting this form,
                                                        you are providing your electronic signature to confirm your attendance at the BMM.
                                                        This serves as your official confirmation and agreement to attend.
                                                    </p>
                                                </div>
                                            </div>
                                        )}

                                        {/* Submit Button for Stage 1 */}
                                        {currentStep === 1 && (
                                            <button
                                                type="submit"
                                                disabled={isSubmitting}
                                                className="w-full bg-orange-500 hover:bg-orange-600 disabled:bg-gray-300 text-white font-bold py-3 px-4 rounded transition-colors"
                                            >
                                                {isSubmitting ? 'Updating & Generating Ticket...' : 'Update Info & Generate Ticket'}
                                            </button>
                                        )}

                                    </form>

                                    {/* Ticket Display - Show after Step 1 completion */}
                                    {formSubmitted && currentStep >= 2 && (
                                        <div className="mt-8 border-t pt-8" data-ticket="display">
                                            <h3 className="text-xl font-bold mb-6 text-center">🎫 Your BMM Ticket Has Been Generated!</h3>

                                            <div className="max-w-md mx-auto mb-8">
                                                {ticketData ? (
                                                    <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg overflow-hidden" ref={ticketRef}>
                                                        <div className="bg-gradient-to-r from-blue-900 to-blue-700 text-white p-6 text-center">
                                                            <div className="mb-2">
                                                                <h1 className="text-2xl font-bold">E tū Event System</h1>
                                                                <p className="text-lg">Member Event Ticket</p>
                                                            </div>
                                                            <div className="mt-4 border-t border-blue-600 pt-4">
                                                                <h2 className="text-xl font-semibold mb-2">{ticketData.eventName || '2025 E tū Biennial Membership Meeting'}</h2>

                                                                {/* Venue and Address */}
                                                                <div className="mb-2">
                                                                    <p className="text-base font-medium">
                                                                        {ticketData.assignedVenue || ticketData.forumDesc || 'Venue to be confirmed'}
                                                                    </p>
                                                                    {ticketData.venueAddress && (
                                                                        <p className="text-sm">{ticketData.venueAddress}</p>
                                                                    )}
                                                                </div>

                                                                {/* Date */}
                                                                <p className="text-base mb-1">
                                                                    {ticketData.assignedDate || 'Date to be confirmed'}
                                                                </p>

                                                                {/* Session Time */}
                                                                <p className="text-lg font-medium mb-1">
                                                                    Meeting starts: {ticketData.assignedSession || 'Time to be confirmed'}
                                                                </p>

                                                                {/* Travel Time Span */}
                                                                {ticketData.timeSpan && (
                                                                    <p className="text-sm">Travel time span: {ticketData.timeSpan}</p>
                                                                )}
                                                            </div>
                                                        </div>

                                                        <div className="p-6">
                                                            <div className="mb-4">
                                                                <p className="text-gray-600 dark:text-gray-400 text-sm">Name:</p>
                                                                <p className="text-xl font-bold text-gray-900 dark:text-white">{ticketData.name || ticketData.memberName || memberData?.name}</p>
                                                            </div>

                                                            <div className="mb-4">
                                                                <p className="text-gray-600 dark:text-gray-400 text-sm">Membership Number:</p>
                                                                <p className="text-xl font-bold text-gray-900 dark:text-white">{ticketData.membershipNumber || memberData?.membershipNumber}</p>
                                                            </div>

                                                            <div className="mb-6">
                                                                <p className="text-gray-600 dark:text-gray-400 text-sm">Region:</p>
                                                                <p className="text-lg font-semibold text-gray-900 dark:text-white">{ticketData.region || ticketData.regionDesc || memberData?.regionDesc}</p>
                                                            </div>

                                                            <div className="flex justify-center mb-6">
                                                                <QRCodeSVG
                                                                    value={JSON.stringify({
                                                                        token: ticketData.ticketToken || ticketData.memberToken || token,
                                                                        membershipNumber: ticketData.membershipNumber || memberData?.membershipNumber,
                                                                        name: ticketData.name || ticketData.memberName || memberData?.name,
                                                                        type: 'event_checkin',
                                                                        checkinUrl: `https://events.etu.nz/api/checkin/${ticketData.ticketToken || ticketData.memberToken || token}`
                                                                    })}
                                                                    size={200}
                                                                    level="H"
                                                                    marginSize={4}
                                                                />
                                                            </div>

                                                            <div className="text-center">
                                                                <p className="text-sm font-medium text-gray-900 dark:text-white">Scan this QR code for quick check-in at the venue</p>
                                                                <p className="text-xs mt-2 text-gray-600 dark:text-gray-400">Ticket ID: {ticketData.ticketToken || token}</p>
                                                            </div>
                                                        </div>
                                                    </div>
                                                ) : (
                                                    <div className="bg-gray-100 dark:bg-gray-800 rounded-lg p-8 text-center">
                                                        <div className="animate-pulse">
                                                            <div className="h-8 bg-gray-300 dark:bg-gray-600 rounded w-3/4 mx-auto mb-4"></div>
                                                            <div className="h-32 bg-gray-300 dark:bg-gray-600 rounded mb-4"></div>
                                                            <div className="h-6 bg-gray-300 dark:bg-gray-600 rounded w-1/2 mx-auto"></div>
                                                        </div>
                                                        <p className="text-sm text-gray-600 dark:text-gray-400 mt-4">
                                                            Loading ticket details...
                                                        </p>
                                                    </div>
                                                )}

                                                {/* Action buttons */}
                                                {ticketData && (
                                                    <div className="flex flex-wrap justify-center gap-3 mt-4 mb-4">
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
                                                            className="bg-purple-500 hover:bg-purple-600 text-white flex items-center py-2 px-4 rounded transition-colors text-sm"
                                                        >
                                                            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                                                            </svg>
                                                            Add to Calendar
                                                        </button>
                                                    </div>
                                                )}

                                                <div className="mt-4 bg-green-50 dark:bg-green-900/30 rounded-lg p-4">
                                                    <h3 className="font-semibold text-green-900 dark:text-green-200 mb-3 text-center">✅ Your BMM Ticket Has Been Generated!</h3>
                                                    <p className="text-sm text-green-800 dark:text-green-300 mb-3">
                                                        <strong>Important:</strong> Please save your ticket now using one of these methods:
                                                    </p>
                                                    <ul className="text-sm text-green-700 dark:text-green-300 space-y-2 ml-4">
                                                        <li>📱 <strong>Save Image to Phone:</strong> Click "Save Image" to download the ticket to your phone's photo gallery</li>
                                                        <li>🖨️ <strong>Print Physical Copy:</strong> Click "Print" to print a hard copy (recommended as backup)</li>
                                                        <li>🔗 <strong>Save Link:</strong> Click "Copy Link" to save the ticket URL - bookmark it or save in notes</li>
                                                        <li>📅 <strong>Add to Calendar:</strong> Click "Add to Calendar" to add the event to your calendar</li>
                                                    </ul>
                                                    <p className="text-xs text-green-600 dark:text-green-400 mt-3 italic text-center">
                                                        💡 Tip: Take a screenshot now or save the image to ensure you have your ticket for the meeting
                                                    </p>
                                                    <p className="text-xs text-green-700 dark:text-green-300 mt-2 text-center">
                                                        You can also view your ticket anytime at
                                                        <a href={`/ticket?token=${ticketData?.ticketToken || token}`} className="underline ml-1" target="_blank">
                                                            this link
                                                        </a>
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                    )}

                                    {/* Stage 2: Attendance Choice */}
                                    {currentStep === 2 && formSubmitted && (
                                        <div className="mt-8 border-t pt-8" data-stage="2">
                                            <h3 className="text-xl font-bold mb-6">Stage 2: Confirm Your Attendance</h3>
                                            <p className="text-gray-600 dark:text-gray-400 mb-6">
                                                Your ticket has been generated. Now please confirm whether you will attend the meeting:
                                            </p>
                                            <div className="space-y-4">
                                                <button
                                                    onClick={async () => {
                                                        setAttendanceChoice('attending');
                                                        await handleAttendanceConfirmation();
                                                    }}
                                                    disabled={isSubmitting}
                                                    className="w-full p-6 text-left border-2 rounded-lg hover:border-orange-500 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                                                >
                                                    <div className="flex items-center">
                                                        <span className="text-2xl mr-4">✅</span>
                                                        <div>
                                                            <h4 className="font-bold text-lg">Yes, I will attend the meeting</h4>
                                                            <p className="text-gray-600 dark:text-gray-400">Confirm my attendance at the BMM</p>
                                                        </div>
                                                    </div>
                                                </button>

                                                <button
                                                    onClick={() => {
                                                        setAttendanceChoice('not_attending');
                                                        setCurrentStep(3);
                                                    }}
                                                    className="w-full p-6 text-left border-2 rounded-lg hover:border-orange-500 transition-all"
                                                >
                                                    <div className="flex items-center">
                                                        <span className="text-2xl mr-4">❌</span>
                                                        <div>
                                                            <h4 className="font-bold text-lg">No, I cannot attend the meeting</h4>
                                                            <p className="text-gray-600 dark:text-gray-400">I need to provide a reason</p>
                                                        </div>
                                                    </div>
                                                </button>
                                            </div>
                                        </div>
                                    )}

                                    {/* Stage 3: Absence Reason (if not attending) */}
                                    {currentStep === 3 && attendanceChoice === 'not_attending' && (
                                        <div className="mt-8 border-t pt-8">
                                            <h3 className="text-xl font-bold mb-6">Stage 3: Reason for Not Attending</h3>
                                            <div className="space-y-4">
                                                <div className="p-4 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-700 rounded-lg mb-4">
                                                    <p className="text-sm text-yellow-800 dark:text-yellow-300">
                                                        Please select your reason for not being able to attend the meeting:
                                                    </p>
                                                </div>

                                                <label className="block p-4 border-2 rounded-lg hover:border-orange-500 cursor-pointer">
                                                    <input
                                                        type="radio"
                                                        name="absenceReason"
                                                        value="sick"
                                                        onChange={(e) => setAbsenceReason(e.target.value)}
                                                        className="mr-3"
                                                    />
                                                    <span className="font-medium">I am sick</span>
                                                </label>

                                                <label className="block p-4 border-2 rounded-lg hover:border-orange-500 cursor-pointer">
                                                    <input
                                                        type="radio"
                                                        name="absenceReason"
                                                        value="distance"
                                                        onChange={(e) => setAbsenceReason(e.target.value)}
                                                        className="mr-3"
                                                    />
                                                    <span className="font-medium">I live outside a 32-km radius from the meeting place</span>
                                                </label>

                                                <label className="block p-4 border-2 rounded-lg hover:border-orange-500 cursor-pointer">
                                                    <input
                                                        type="radio"
                                                        name="absenceReason"
                                                        value="work"
                                                        onChange={(e) => setAbsenceReason(e.target.value)}
                                                        className="mr-3"
                                                    />
                                                    <span className="font-medium">My employer requires me to work at the time of the meeting</span>
                                                </label>

                                                <label className="block p-4 border-2 rounded-lg hover:border-orange-500 cursor-pointer">
                                                    <input
                                                        type="radio"
                                                        name="absenceReason"
                                                        value="other"
                                                        onChange={(e) => setAbsenceReason(e.target.value)}
                                                        className="mr-3"
                                                    />
                                                    <span className="font-medium">Other reason</span>
                                                </label>

                                                {absenceReason === 'other' && (
                                                    <textarea
                                                        value={otherReason}
                                                        onChange={(e) => setOtherReason(e.target.value)}
                                                        placeholder="Please specify your reason..."
                                                        className="w-full p-3 border rounded-lg"
                                                        rows={3}
                                                        required
                                                    />
                                                )}

                                                {/* Special Vote Question - Only for Central/Southern Regions and NOT for "other" reason */}
                                                {absenceReason && absenceReason !== 'other' && memberData?.specialVoteEligible && (
                                                    <div className="mt-6 p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-700 rounded-lg">
                                                        <h4 className="font-bold mb-3">Special Voting Rights</h4>
                                                        <p className="text-sm mb-4">
                                                            As a {memberData.regionDesc} member, you may be eligible for special voting rights based on your reason for not attending.
                                                        </p>
                                                        <div className="space-y-2">
                                                            <label className="flex items-center">
                                                                <input
                                                                    type="radio"
                                                                    name="specialVote"
                                                                    value="yes"
                                                                    onChange={() => setSpecialVoteReason('yes')}
                                                                    className="mr-3"
                                                                />
                                                                <span>Yes, I want to request a special vote</span>
                                                            </label>
                                                            <label className="flex items-center">
                                                                <input
                                                                    type="radio"
                                                                    name="specialVote"
                                                                    value="no"
                                                                    onChange={() => setSpecialVoteReason('no')}
                                                                    className="mr-3"
                                                                />
                                                                <span>No, I do not need special voting rights</span>
                                                            </label>
                                                        </div>
                                                    </div>
                                                )}

                                                {/* Submit Button */}
                                                <button
                                                    onClick={(e) => handleSubmit(e)}
                                                    disabled={!absenceReason || (absenceReason === 'other' && !otherReason) ||
                                                        (memberData?.specialVoteEligible && absenceReason !== 'other' && !specialVoteReason) ||
                                                        isSubmitting}
                                                    className="w-full bg-orange-500 hover:bg-orange-600 disabled:bg-gray-300 text-white font-bold py-3 px-4 rounded transition-colors mt-6"
                                                >
                                                    {isSubmitting ? 'Submitting...' : 'Submit'}
                                                </button>
                                            </div>
                                        </div>
                                    )}

                                    {error && (
                                        <div className="mt-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-700 rounded-lg p-4">
                                            <p className="text-red-800 dark:text-red-300">{error}</p>
                                        </div>
                                    )}
                                </>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </Layout>
    );
}