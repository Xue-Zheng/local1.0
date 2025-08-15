import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/common/Layout';
import { getMemberByToken } from '@/services/registrationService';
import { EventMember } from '@/services/registrationService';
import api from '@/services/api';

interface BMMMeeting {
    venue: string;
    address: string;
    date: string;
    time: string;
    region: string;
    maxMembers: number;
    googleMapUrl?: string;
}

export default function ConfirmAttendance() {
    const router = useRouter();
    const { token, region } = router.query;
    const [memberData, setMemberData] = useState<EventMember | null>(null);
    const [loading, setLoading] = useState(true);
    const [meetingDetails, setMeetingDetails] = useState<BMMMeeting | null>(null);
    const [confirming, setConfirming] = useState(false);

    // BMM Meeting details based on region and venue
    const getMeetingDetails = (region: string): BMMMeeting[] => {
        switch (region) {
            case 'Northern Region':
                return [
                    {
                        venue: 'Barfoot & Thompson Netball Centre',
                        address: '44 Northcote Road, Northcote - Gibson Room',
                        date: 'Monday, 1 September 2025',
                        time: '2:00 PM – 4:00 PM',
                        region: 'Northern Region',
                        maxMembers: 300,
                        googleMapUrl: 'https://maps.app.goo.gl/example1'
                    },
                    {
                        venue: 'Trusts Arena',
                        address: '65 Central Park Drive, Henderson - Genesis Lounge',
                        date: 'Tuesday, 2 September 2025',
                        time: '2:00 PM – 4:00 PM',
                        region: 'Northern Region',
                        maxMembers: 600,
                        googleMapUrl: 'https://maps.app.goo.gl/example2'
                    }
                ];
            case 'Central Region':
                return [
                    {
                        venue: 'Te Rauparaha Arena',
                        address: 'Small Stadium, 17 Parumoana St, Porirua 5022',
                        date: 'Wednesday, 3 September 2025',
                        time: '2:00 PM – 4:00 PM',
                        region: 'Central Region',
                        maxMembers: 500,
                        googleMapUrl: 'https://maps.app.goo.gl/example3'
                    },
                    {
                        venue: 'Palmy Conference + Function Centre',
                        address: '354 Main Street, Palmerston North 4410',
                        date: 'Monday, 22 September 2025',
                        time: '2:00 PM – 4:00 PM',
                        region: 'Central Region',
                        maxMembers: 600,
                        googleMapUrl: 'https://venuespn.co.nz/conference-function-centre/'
                    }
                ];
            case 'Southern Region':
                return [
                    {
                        venue: 'Woolston Club',
                        address: '43 Hargood Street Christchurch 8062',
                        date: 'Friday, 19 September 2025',
                        time: '2:00 PM – 4:00 PM',
                        region: 'Southern Region',
                        maxMembers: 470,
                        googleMapUrl: 'https://maps.app.goo.gl/G5bGBeHgW6PSN6sNA'
                    },
                    {
                        venue: 'Caroline Bay Hall',
                        address: 'Caroline Bay Hall, Timaru 7910',
                        date: 'Wednesday, 17 September 2025',
                        time: '2:00 PM – 4:00 PM',
                        region: 'Southern Region',
                        maxMembers: 100, // Only 100 chairs available
                        googleMapUrl: 'https://www.timaru.govt.nz/community/facilities/community-centres-and-halls/caroline-bay-hall'
                    }
                ];
            default:
                return [];
        }
    };

    useEffect(() => {
        if (token && typeof token === 'string') {
            fetchMemberData(token);
        }
    }, [token]);

    const fetchMemberData = async (memberToken: string) => {
        try {
            const response = await getMemberByToken(memberToken);
            if (response.status === 'success' && response.data) {
                setMemberData(response.data);

                // Set meeting details based on member's region
                const memberRegion = response.data.regionDesc || region as string;
                const meetings = getMeetingDetails(memberRegion);
                if (meetings.length > 0) {
                    setMeetingDetails(meetings[0]); // Default to first meeting
                }
            }
        } catch (error) {
            console.error('Failed to fetch member data:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleConfirmAttendance = async () => {
        if (!token || !memberData) return;

        setConfirming(true);
        try {
            const response = await api.post('/bmm/confirm-attendance', {
                memberToken: token,
                isAttending: true,
                financialForm: {
                    name: memberData.name,
                    primaryEmail: memberData.primaryEmail,
                    telephoneMobile: memberData.telephoneMobile,
                    address: memberData.address || '',
                    dob: memberData.dob || '',
                    phoneHome: memberData.phoneHome || '',
                    phoneWork: memberData.phoneWork || '',
                    employer: memberData.employer || '',
                    payrollNumber: memberData.payrollNumber || '',
                    siteCode: memberData.siteCode || '',
                    employmentStatus: memberData.employmentStatus || '',
                    department: memberData.department || '',
                    jobTitle: memberData.jobTitle || '',
                    location: memberData.location || ''
                }
            });

            if (response.data.status === 'success') {
                // Redirect to ticket page after successful confirmation
                router.push(`/ticket?token=${token}&confirmed=true`);
            } else {
                console.error('Confirmation failed:', response.data.message);
                alert('Failed to confirm attendance. Please try again.');
                setConfirming(false);
            }
        } catch (error) {
            console.error('Failed to confirm attendance:', error);
            alert('An error occurred. Please try again later.');
            setConfirming(false);
        }
    };

    const getRegionColor = (region: string) => {
        switch (region) {
            case 'Northern Region': return 'blue';
            case 'Central Region': return 'green';
            case 'Southern Region': return 'pink';
            default: return 'blue';
        }
    };

    const regionColor = getRegionColor(memberData?.regionDesc || region as string || '');
    const isSouthernRegion = (memberData?.regionDesc || region) === 'Southern Region';

    if (loading) {
        return (
            <Layout>
                <div className="min-h-screen flex flex-col items-center justify-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mb-6"></div>
                    <p className="text-lg text-gray-600 text-center max-w-md">
                        We're now asking you to confirm your attendance at the following meeting - Confirming your attendance will take two minutes of your time.
                    </p>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className={`min-h-screen py-12 px-4 sm:px-6 lg:px-8 ${
                regionColor === 'blue' ? 'bg-gradient-to-br from-blue-50 to-blue-100' :
                    regionColor === 'green' ? 'bg-gradient-to-br from-green-50 to-green-100' :
                        'bg-gradient-to-br from-pink-50 to-pink-100'
            }`}>
                <div className="max-w-4xl mx-auto">
                    {/* Header */}
                    <div className="text-center mb-8">
                        <h1 className={`text-4xl font-bold mb-4 ${
                            regionColor === 'blue' ? 'text-blue-900' :
                                regionColor === 'green' ? 'text-green-900' :
                                    'text-pink-900'
                        }`}>
                            Confirm your attendance
                        </h1>
                        <h2 className="text-2xl text-gray-700 mb-6">
                            2025 E tū Biennial Membership Meeting
                        </h2>
                        <p className="text-lg text-gray-600 mb-4">
                            Thank you for pre-registering your interest to attend the 2025 Biennial Membership Meetings (BMMs).
                        </p>
                        <p className="text-xl font-semibold text-gray-800">
                            You're just one step away from securing your spot!
                        </p>
                    </div>

                    {/* Meeting Details Card */}
                    {meetingDetails && (
                        <div className={`bg-white rounded-lg shadow-lg p-8 mb-8 border-l-4 ${
                            regionColor === 'blue' ? 'border-blue-500' :
                                regionColor === 'green' ? 'border-green-500' :
                                    'border-pink-500'
                        }`}>
                            <h3 className="text-2xl font-bold text-gray-900 mb-6">Your Meeting Details</h3>
                            <p className="text-gray-700 mb-4">Please confirm your attendance at the BMM listed below:</p>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                <div>
                                    <div className="mb-4">
                                        <strong className="text-gray-900">📍 Location:</strong>
                                        <p className="text-gray-700">
                                            {meetingDetails.venue}
                                            {meetingDetails.address && `, ${meetingDetails.address.match(/^[^,]+/)?.[0].trim() || meetingDetails.address}`}
                                        </p>
                                    </div>

                                    <div className="mb-4">
                                        <strong className="text-gray-900">📅 Date:</strong>
                                        <p className="text-gray-700">{meetingDetails.date}</p>
                                    </div>
                                </div>

                                <div>
                                    <div className="mb-4">
                                        <strong className="text-gray-900">🌏 Region:</strong>
                                        <p className="text-gray-700">{meetingDetails.region}</p>
                                    </div>

                                    <div className="mb-4">
                                        <strong className="text-gray-900">👥 Capacity:</strong>
                                        <p className="text-gray-700">{meetingDetails.maxMembers} members</p>
                                    </div>

                                    {meetingDetails.googleMapUrl && (
                                        <div className="mb-4">
                                            <a
                                                href={meetingDetails.googleMapUrl}
                                                target="_blank"
                                                rel="noopener noreferrer"
                                                className={`inline-flex items-center px-4 py-2 text-sm font-medium rounded-md ${
                                                    regionColor === 'blue' ? 'text-blue-700 bg-blue-100 hover:bg-blue-200' :
                                                        regionColor === 'green' ? 'text-green-700 bg-green-100 hover:bg-green-200' :
                                                            'text-pink-700 bg-pink-100 hover:bg-pink-200'
                                                }`}
                                            >
                                                🗺️ View on Map
                                            </a>
                                        </div>
                                    )}
                                </div>
                            </div>

                            <div className={`mt-6 p-4 rounded-lg ${
                                regionColor === 'blue' ? 'bg-blue-50 border border-blue-200' :
                                    regionColor === 'green' ? 'bg-green-50 border border-green-200' :
                                        'bg-pink-50 border border-pink-200'
                            }`}>
                                <p className={`text-sm ${
                                    regionColor === 'blue' ? 'text-blue-800' :
                                        regionColor === 'green' ? 'text-green-800' :
                                            'text-pink-800'
                                }`}>
                                    <strong>💼 Official Paid Union Meeting:</strong> These meetings are official paid union meetings under Section 26 of the Employment Relations Act, and your attendance is fully supported by your union.
                                </p>
                            </div>
                        </div>
                    )}

                    {/* Ticket Information */}
                    <div className="bg-white rounded-lg shadow-lg p-8 mb-8">
                        <h3 className="text-2xl font-bold text-gray-900 mb-4">Your Attendance Ticket</h3>
                        <div className="space-y-4">
                            <p className="text-gray-700">
                                Once you confirm your attendance, you will receive a <strong>personalised ticket</strong> for entry to your BMM.
                            </p>
                            <ul className="list-disc list-inside text-gray-700 space-y-2 ml-4">
                                <li>This ticket will be sent to you by email (and/or available for download)</li>
                                <li>You must bring this ticket with you to the meeting – either printed or on your phone</li>
                                <li>Your ticket will be used to register your attendance on the day</li>
                                <li>Without a ticket, you may not be able to enter the meeting</li>
                            </ul>
                        </div>
                    </div>

                    {/* Southern Region Voting Information */}
                    {isSouthernRegion && (
                        <div className="bg-pink-50 border border-pink-200 rounded-lg p-8 mb-8">
                            <h3 className="text-2xl font-bold text-pink-900 mb-4 flex items-center">
                                🗳️ Voting Rights – Southern Region Members Only
                            </h3>
                            <div className="space-y-4 text-pink-800">
                                <p>
                                    If your selected meeting is in the Southern Region, your ticket will also serve as your <strong>voting pass</strong> for the election of the National Executive Southern Region Representative.
                                </p>
                                <p className="font-semibold">
                                    Only members who bring their ticket and are present at the meeting will be eligible to vote.
                                </p>

                                <div className="bg-white border border-pink-300 rounded-lg p-4 mt-4">
                                    <h4 className="font-bold text-pink-900 mb-2">Southern Region Election Voting Process</h4>
                                    <ol className="list-decimal list-inside text-pink-700 space-y-2">
                                        <li>Attend your Biennial Membership Meeting (BMM) with your ticket</li>
                                        <li>Present your attendance ticket at the meeting</li>
                                        <li>Receive a secure voting link via email</li>
                                        <li>You will have <strong>72 hours</strong> to cast your vote for your preferred candidate</li>
                                    </ol>
                                    <p className="text-sm text-pink-600 mt-3">
                                        Please ensure your contact details are up to date to receive the voting link promptly after the meeting.
                                    </p>
                                </div>

                                {/* Special Vote Application */}
                                <div className="bg-purple-50 border border-purple-300 rounded-lg p-4 mt-4">
                                    <h4 className="font-bold text-purple-900 mb-2">Special Vote Application</h4>
                                    <p className="text-purple-800 mb-2">
                                        If you are unable to attend the BMM in your area but wish to vote in the Regional Representative election, you may be eligible to apply for a special vote.
                                    </p>
                                    <p className="text-sm text-purple-700 mb-2">To qualify, one of the following must apply to you:</p>
                                    <ul className="text-sm text-purple-700 list-disc list-inside space-y-1 ml-4">
                                        <li>You have a disability that prevents you from fully participating in the meeting</li>
                                        <li>You are ill or infirm, making attendance impossible</li>
                                        <li>You live more than 32km from the meeting venue</li>
                                        <li>Your employer requires you to work during the time of the meeting</li>
                                        <li>Attending the meeting would cause you serious hardship or major inconvenience</li>
                                    </ul>
                                    <p className="text-sm font-semibold text-purple-800 mt-2">
                                        Special vote applications must be made at least 14 days before the start of the BMM.
                                    </p>
                                    <p className="text-sm text-purple-700 mt-2">
                                        If approved, a ballot paper will be issued to you by the Returning Officer.
                                        Contact: <a href="mailto:returningofficer@etu.nz" className="underline">returningofficer@etu.nz</a>
                                    </p>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Confirmation Button */}
                    <div className="text-center">
                        <button
                            onClick={handleConfirmAttendance}
                            disabled={confirming}
                            className={`inline-flex items-center px-8 py-4 text-lg font-semibold rounded-lg shadow-lg transform transition-all duration-200 hover:scale-105 disabled:opacity-50 disabled:cursor-not-allowed ${
                                regionColor === 'blue' ? 'bg-blue-600 hover:bg-blue-700 text-white' :
                                    regionColor === 'green' ? 'bg-green-600 hover:bg-green-700 text-white' :
                                        'bg-pink-600 hover:bg-pink-700 text-white'
                            }`}
                        >
                            {confirming ? (
                                <>
                                    <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white mr-3"></div>
                                    Confirming...
                                </>
                            ) : (
                                <>
                                    🎫 Confirm My Attendance & Get My Ticket
                                </>
                            )}
                        </button>
                    </div>

                    {/* Contact Information */}
                    <div className="text-center mt-8 text-gray-600">
                        <h4 className="font-semibold text-gray-800 mb-2">Need help?</h4>
                        <p className="text-sm">
                            If you have any questions about your meeting details, ticket, or need assistance, contact us at:
                        </p>
                        <p className="font-medium">
                            <a href="mailto:support@etu.nz" className="text-blue-600 hover:underline">support@etu.nz</a> |
                            <a href="tel:08001864666" className="text-blue-600 hover:underline ml-2">0800 1 UNION (0800 186 466)</a>
                        </p>
                        <p className="text-sm mt-2 font-medium text-gray-800">
                            We look forward to seeing you there and hearing your voice!<br/>
                            Together, we are E tū.
                        </p>
                    </div>
                </div>
            </div>
        </Layout>
    );
}