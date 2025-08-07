import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/common/Layout';
import { getMemberByToken } from '@/services/registrationService';
import { EventMember } from '@/services/registrationService';

export default function ConfirmSouthernAttendance() {
    const router = useRouter();
    const { token } = router.query;
    const [memberData, setMemberData] = useState<EventMember | null>(null);
    const [loading, setLoading] = useState(true);
    const [confirming, setConfirming] = useState(false);

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
            }
        } catch (error) {
            console.error('Failed to fetch member data:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleConfirmAttendance = async () => {
        setConfirming(true);
        // TODO: Implement confirmation API call
        setTimeout(() => {
            setConfirming(false);
            router.push(`/ticket?token=${token}&confirmed=true`);
        }, 2000);
    };

    if (loading) {
        return (
            <Layout>
                <div className="min-h-screen flex flex-col items-center justify-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-pink-500 mb-6"></div>
                    <p className="text-lg text-gray-600 text-center max-w-md">
                        We're now asking you to confirm your attendance at the following meeting.
                    </p>
                    <p className="text-lg text-gray-600 text-center max-w-md mt-2">
                        Confirming your attendance will take two minutes of your time.
                    </p>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="min-h-screen py-12 px-4 sm:px-6 lg:px-8 bg-gradient-to-br from-pink-50 to-pink-100">
                <div className="max-w-4xl mx-auto">
                    {/* Header */}
                    <div className="text-center mb-8">
                        <h1 className="text-4xl font-bold mb-4 text-pink-900">
                            Confirm your attendance – 2025 E tū Biennial Membership Meetings
                        </h1>
                        <p className="text-lg text-gray-600 mb-4">
                            E tū is proud to welcome all members to attend the 2025 Biennial Membership Meetings (BMMs).
                        </p>
                        <p className="text-lg text-gray-600 mb-4">
                            Even if you didn't preregister your interest, you are warmly invited to participate in this important moment of union democracy. By confirming your attendance below, you'll help us plan your meeting and ensure we can communicate your participation to your employer.
                        </p>
                        <p className="text-xl font-semibold text-pink-800">
                            You're just one step away from securing your place at your local BMM.
                        </p>
                    </div>

                    {/* Meeting Details Card */}
                    <div className="bg-white rounded-lg shadow-lg p-8 mb-8 border-l-4 border-pink-500">
                        <h3 className="text-2xl font-bold text-gray-900 mb-6">Your Meeting Details</h3>
                        <p className="text-gray-700 mb-4">Please confirm your attendance at the BMM listed below:</p>

                        <div className="space-y-4">
                            <div>
                                <strong className="text-gray-900">📍 Location:</strong>
                                <p className="text-gray-700">[Insert Venue Name and Address]</p>
                            </div>

                            <div>
                                <strong className="text-gray-900">📅 Date:</strong>
                                <p className="text-gray-700">[Insert Day, Date]</p>
                            </div>

                            <div>
                                <strong className="text-gray-900">🕐 Starting Meeting Time:</strong>
                                <p className="text-gray-700">(Insert starting time)</p>
                            </div>

                            <div>
                                <strong className="text-gray-900">⏱️ Time span, including travel time:</strong>
                                <p className="text-gray-700">[Insert Time, e.g. 2:00 PM – 4:00 PM]</p>
                            </div>

                            <div>
                                <strong className="text-gray-900">🌏 Region:</strong>
                                <p className="text-gray-700">[Insert Region Name]</p>
                            </div>
                        </div>

                        <div className="mt-6 p-4 rounded-lg bg-pink-50 border border-pink-200">
                            <p className="text-sm text-pink-800">
                                These meetings are official paid union meetings under Section 26 of the Employment Relations Act, and your attendance is fully supported by your union.
                            </p>
                            <p className="text-sm text-pink-800 mt-2">
                                The time span shown includes the two hours provided for in your collective agreement or under Section 26, inclusive of reasonable travel time. Members are also entitled to all their paid and unpaid breaks and may be away from the site for a slightly longer period.
                            </p>
                        </div>
                    </div>

                    {/* Ticket Information */}
                    <div className="bg-white rounded-lg shadow-lg p-8 mb-8">
                        <h3 className="text-2xl font-bold text-gray-900 mb-4">Your Attendance Ticket</h3>
                        <div className="space-y-4">
                            <p className="text-gray-700">
                                Once you confirm your attendance, you will get access to your personalised ticket for entry to your BMM.
                            </p>
                            <ul className="list-disc list-inside text-gray-700 space-y-2 ml-4">
                                <li>This ticket will be sent to you by email (and/or available for download)</li>
                                <li>You must bring this ticket with you to the meeting – either printed or on your phone</li>
                                <li>Your ticket will be used to register your attendance on the day</li>
                                <li>Your ticket will be your voting pass</li>
                            </ul>
                        </div>
                    </div>

                    {/* Southern Region Voting Information */}
                    <div className="bg-pink-50 border border-pink-200 rounded-lg p-8 mb-8">
                        <h3 className="text-2xl font-bold text-pink-900 mb-4">
                            Voting rights – Southern Region members only
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
                                <p className="text-pink-700 mb-2">
                                    If you are a member from the Southern Region, attending your Biennial Membership Meeting (BMM) will give you the right to vote for the Southern Region representative on the National Executive. After you present your attendance ticket at the meeting, you will receive a secure voting link via email. You will then have 72 hours to cast your vote for your preferred candidate. Please ensure your contact details are up to date to receive the voting link promptly after the meeting.
                                </p>
                            </div>

                            {/* Special Vote Application */}
                            <div className="bg-purple-50 border border-purple-300 rounded-lg p-4 mt-4">
                                <h4 className="font-bold text-purple-900 mb-2">Special Vote Application (Important Voting Info only for the Southern Region members)</h4>
                                <p className="text-purple-800 mb-2">
                                    If you are unable to attend the BMM in your area but wish to vote in the Regional Representative election, you may be eligible to apply for a special vote.
                                </p>
                                <p className="text-sm text-purple-700 mb-2">To qualify, one of the following must apply to you:</p>
                                <ul className="text-sm text-purple-700 list-disc list-inside space-y-1 ml-4">
                                    <li>You have a disability that prevents you from fully participating in the meeting</li>
                                    <li>You are ill or infirm, making attendance impossible</li>
                                    <li>You live more than 32km from the meeting venue (as per list above)</li>
                                    <li>Your employer requires you to work during the time of the meeting</li>
                                    <li>Attending the meeting would cause you serious hardship or major inconvenience</li>
                                </ul>
                                <p className="text-sm font-semibold text-purple-800 mt-2">
                                    Special vote applications must be made at least 14 days before the start of the BMM at which the secret ballot is to be held.
                                </p>
                                <p className="text-sm text-purple-700 mt-2">
                                    If approved, a ballot paper will be issued to you by the Returning Officer.
                                </p>
                                <p className="text-sm text-purple-700 mt-2">
                                    If you have any questions about the voting process, please contact our Returning Officer at <a href="mailto:returningofficer@etu.nz" className="underline">returningofficer@etu.nz</a>.
                                </p>
                            </div>
                        </div>
                    </div>


                    {/* Confirmation Button */}
                    <div className="text-center">
                        <button
                            onClick={handleConfirmAttendance}
                            disabled={confirming}
                            className="inline-flex items-center px-8 py-4 text-lg font-semibold rounded-lg shadow-lg transform transition-all duration-200 hover:scale-105 disabled:opacity-50 disabled:cursor-not-allowed bg-pink-600 hover:bg-pink-700 text-white"
                        >
                            {confirming ? (
                                <>
                                    <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white mr-3"></div>
                                    Confirming...
                                </>
                            ) : (
                                <>
                                    Confirm my attendance and get my ticket
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
                            <a href="mailto:support@etu.nz" className="text-pink-600 hover:underline">support@etu.nz</a> |
                            <a href="tel:08001864666" className="text-pink-600 hover:underline ml-2">0800 1 UNION (0800 186 466)</a>
                        </p>
                        <p className="text-base mt-4 font-medium text-gray-800">
                            We look forward to seeing you there and hearing your voice!
                        </p>
                        <p className="text-base font-medium text-gray-800">
                            Together, we are E tū.
                        </p>
                    </div>
                </div>
            </div>
        </Layout>
    );
}