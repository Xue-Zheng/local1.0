import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/common/Layout';
import { getMemberByToken } from '@/services/registrationService';
import { EventMember } from '@/services/registrationService';

export default function ConfirmCentralAttendance() {
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
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-green-500 mb-6"></div>
                    <p className="text-lg text-gray-600 text-center max-w-md">
                        We're now asking you to confirm your attendance at the following meeting - Confirming your attendance will take two minutes of your time.
                    </p>

                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="min-h-screen py-12 px-4 sm:px-6 lg:px-8 bg-gradient-to-br from-green-50 to-green-100">
                <div className="max-w-4xl mx-auto">
                    {/* Header */}
                    <div className="text-center mb-8">
                        <h1 className="text-4xl font-bold mb-4 text-green-900">
                            Confirm your attendance – 2025 E tū Biennial Membership Meetings
                        </h1>
                        <p className="text-lg text-gray-600 mb-4">
                            E tū is proud to welcome all members to attend the 2025 Biennial Membership Meetings (BMMs).
                        </p>
                        <p className="text-lg text-gray-600 mb-4">
                            Even if you didn't preregister your interest, you are warmly invited to participate in this important moment of union democracy. By confirming your attendance below, you'll help us plan your meeting and ensure we can communicate your participation to your employer.
                        </p>
                        <p className="text-xl font-semibold text-green-800">
                            You're just one step away from securing your place at your local BMM.
                        </p>
                    </div>

                    {/* Meeting Details Card */}
                    <div className="bg-white rounded-lg shadow-lg p-8 mb-8 border-l-4 border-green-500">
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

                        <div className="mt-6 p-4 rounded-lg bg-green-50 border border-green-200">
                            <p className="text-sm text-green-800">
                                These meetings are official paid union meetings under Section 26 of the Employment Relations Act, and your attendance is fully supported by your union.
                            </p>
                            <p className="text-sm text-green-800 mt-2">
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
                                <li>Your ticket will be available for download and/or print</li>
                                <li>Please bring your ticket with you – printed or on your phone</li>
                                <li>Your ticket will be used to register your attendance on the day</li>
                            </ul>
                        </div>
                    </div>


                    {/* Confirmation Button */}
                    <div className="text-center">
                        <button
                            onClick={handleConfirmAttendance}
                            disabled={confirming}
                            className="inline-flex items-center px-8 py-4 text-lg font-semibold rounded-lg shadow-lg transform transition-all duration-200 hover:scale-105 disabled:opacity-50 disabled:cursor-not-allowed bg-green-600 hover:bg-green-700 text-white"
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
                            <a href="mailto:support@etu.nz" className="text-green-600 hover:underline">support@etu.nz</a> |
                            <a href="tel:08001864666" className="text-green-600 hover:underline ml-2">0800 1 UNION (0800 186 466)</a>
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