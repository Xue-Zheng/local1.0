'use client';
import { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import api from '@/services/api';

interface MemberData {
    id: number;
    name: string;
    primaryEmail: string;
    membershipNumber: string;
    regionDesc: string;
    telephoneMobile?: string;
    bmmStage: string;
    isAttending?: boolean;
    absenceReason?: string;
    specialVoteEligible: boolean;
    specialVoteRequested?: boolean;
    specialVoteApplicationReason?: string;
    bmmSpecialVoteStatus?: string;
}

interface SpecialVoteApplication {
    eligibilityReason: string;
    supportingEvidence: string;
    additionalInfo: string;
    contactPhone: string;
    preferredDeliveryMethod: string;
    returnAddress: string;
    declarationAgreed: boolean;
}

export default function SpecialVotePage() {
    const router = useRouter();
    const [token, setToken] = useState<string>('');
    const [memberData, setMemberData] = useState<MemberData | null>(null);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [application, setApplication] = useState<SpecialVoteApplication>({
        eligibilityReason: '',
        supportingEvidence: '',
        additionalInfo: '',
        contactPhone: '',
        preferredDeliveryMethod: 'email',
        returnAddress: '',
        declarationAgreed: false
    });

    const eligibilityReasons = [
        { value: 'disability', label: 'Disability preventing full participation' },
        { value: 'illness', label: 'Illness or infirmity' },
        { value: 'distance', label: 'Living more than 32km from meeting venue' },
        { value: 'work', label: 'Required to work during meeting time' },
        { value: 'hardship', label: 'Serious hardship or major inconvenience' }
    ];

    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search);
        const tokenParam = urlParams.get('token');
        if (tokenParam) {
            setToken(tokenParam);
        }
    }, []);

    useEffect(() => {
        if (!token) return;

        const fetchMemberData = async () => {
            try {
                const response = await api.get(`/event-registration/member/${token}`);
                if (response.data.status === 'success') {
                    const member = response.data.data;
                    setMemberData(member);

                    // Check if member is eligible for special vote
                    if (!member.specialVoteEligible) {
                        toast.error('Special vote applications are only available for Southern Region members');
                        router.push(`/bmm?token=${token}`);
                        return;
                    }

                    // Check if member is not attending
                    if (member.isAttending === true) {
                        toast.error('Special vote applications are only for members who cannot attend the meeting');
                        router.push(`/bmm?token=${token}`);
                        return;
                    }

                    // Check if member has confirmed non-attendance
                    if (member.isAttending === null || member.isAttending === undefined) {
                        toast.error('Please confirm your attendance status first');
                        router.push(`/bmm?token=${token}`);
                        return;
                    }

                    // Check if already applied for special vote
                    if (member.specialVoteRequested) {
                        toast.info('You have already applied for a special vote. Check your BMM status for updates.');
                        router.push(`/bmm?token=${token}`);
                        return;
                    }

                    // Pre-populate contact phone if available
                    if (member.telephoneMobile) {
                        setApplication(prev => ({
                            ...prev,
                            contactPhone: member.telephoneMobile
                        }));
                    }
                } else {
                    toast.error('Invalid token or member not found');
                    router.push('/');
                }
            } catch (error) {
                console.error('Error fetching member data:', error);
                toast.error('Failed to load member information');
                router.push('/');
            } finally {
                setLoading(false);
            }
        };

        fetchMemberData();
    }, [token, router]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!application.eligibilityReason) {
            toast.error('Please select an eligibility reason');
            return;
        }

        if (!application.supportingEvidence.trim()) {
            toast.error('Please provide supporting evidence');
            return;
        }

        if (!application.contactPhone.trim()) {
            toast.error('Please provide a contact phone number');
            return;
        }

        if (!application.declarationAgreed) {
            toast.error('Please agree to the declaration');
            return;
        }

        setSubmitting(true);
        try {
            const response = await api.post(`/event-members/special-vote/${token}`, {
                eligibilityReason: application.eligibilityReason,
                supportingEvidence: application.supportingEvidence,
                additionalInfo: application.additionalInfo,
                contactPhone: application.contactPhone,
                preferredDeliveryMethod: application.preferredDeliveryMethod,
                returnAddress: application.returnAddress
            });

            if (response.data.status === 'success') {
                toast.success('Special vote application submitted successfully!');
                setTimeout(() => {
                    router.push(`/bmm?token=${token}`);
                }, 2000);
            } else {
                toast.error(response.data.message || 'Failed to submit application');
            }
        } catch (error: any) {
            console.error('Error submitting application:', error);
            toast.error('Failed to submit application. Please try again.');
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) {
        return (
            <Layout>
                <div className="min-h-screen flex items-center justify-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-purple-600"></div>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="container mx-auto px-4 py-8">
                <div className="max-w-2xl mx-auto">
                    <div className="bg-white rounded-lg shadow-lg p-8">
                        <div className="text-center mb-8">
                            <h1 className="text-3xl font-bold text-purple-900 mb-2">
                                🗳️ Special Vote Application
                            </h1>
                            <p className="text-gray-600">
                                BMM 2025 Special Vote Application
                            </p>
                        </div>

                        {/* Member Information */}
                        <div className="bg-purple-50 rounded-lg p-6 mb-6">
                            <h2 className="text-lg font-semibold text-purple-900 mb-4">Member Information</h2>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div>
                                    <p className="text-sm text-gray-600">Name</p>
                                    <p className="font-medium">{memberData?.name}</p>
                                </div>
                                <div>
                                    <p className="text-sm text-gray-600">Membership Number</p>
                                    <p className="font-medium">{memberData?.membershipNumber}</p>
                                </div>
                                <div>
                                    <p className="text-sm text-gray-600">Region</p>
                                    <p className="font-medium">{memberData?.regionDesc}</p>
                                </div>
                                <div>
                                    <p className="text-sm text-gray-600">Attendance Status</p>
                                    <p className="font-medium text-red-600">Cannot Attend</p>
                                </div>
                            </div>
                            <div className="mt-4">
                                <p className="text-sm text-gray-600">Reason for Non-Attendance</p>
                                <p className="font-medium">{memberData?.absenceReason}</p>
                            </div>
                        </div>

                        {/* Application Form */}
                        <form onSubmit={handleSubmit} className="space-y-6">
                            {/* Eligibility Reason */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-3">
                                    Eligibility Reason for Special Vote <span className="text-red-500">*</span>
                                </label>
                                <div className="space-y-2">
                                    {eligibilityReasons.map((reason) => (
                                        <div key={reason.value} className="flex items-center">
                                            <input
                                                type="radio"
                                                id={reason.value}
                                                name="eligibilityReason"
                                                value={reason.value}
                                                checked={application.eligibilityReason === reason.value}
                                                onChange={(e) => setApplication(prev => ({...prev, eligibilityReason: e.target.value}))}
                                                className="h-4 w-4 text-purple-600 focus:ring-purple-500 border-gray-300"
                                            />
                                            <label htmlFor={reason.value} className="ml-2 text-sm text-gray-700">
                                                {reason.label}
                                            </label>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            {/* Supporting Evidence */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Supporting Evidence <span className="text-red-500">*</span>
                                </label>
                                <textarea
                                    value={application.supportingEvidence}
                                    onChange={(e) => setApplication(prev => ({...prev, supportingEvidence: e.target.value}))}
                                    rows={4}
                                    className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500"
                                    placeholder="Please provide detailed information supporting your eligibility for a special vote..."
                                />
                            </div>

                            {/* Contact Phone */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Contact Phone Number <span className="text-red-500">*</span>
                                </label>
                                <input
                                    type="tel"
                                    value={application.contactPhone}
                                    onChange={(e) => setApplication(prev => ({...prev, contactPhone: e.target.value}))}
                                    className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500"
                                    placeholder="Phone number where you can be reached"
                                />
                            </div>

                            {/* Additional Information */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Additional Information (Optional)
                                </label>
                                <textarea
                                    value={application.additionalInfo}
                                    onChange={(e) => setApplication(prev => ({...prev, additionalInfo: e.target.value}))}
                                    rows={3}
                                    className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500"
                                    placeholder="Any additional information you'd like to provide..."
                                />
                            </div>

                            {/* Return Address */}
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">
                                    Return Address for Ballot (if different from member record)
                                </label>
                                <textarea
                                    value={application.returnAddress}
                                    onChange={(e) => setApplication(prev => ({...prev, returnAddress: e.target.value}))}
                                    rows={2}
                                    className="w-full border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-purple-500"
                                    placeholder="Leave blank to use address on file"
                                />
                            </div>

                            {/* Declaration */}
                            <div className="bg-gray-50 rounded-lg p-4">
                                <div className="flex items-start">
                                    <input
                                        type="checkbox"
                                        id="declaration"
                                        checked={application.declarationAgreed}
                                        onChange={(e) => setApplication(prev => ({...prev, declarationAgreed: e.target.checked}))}
                                        className="h-4 w-4 text-purple-600 focus:ring-purple-500 border-gray-300 rounded mt-1"
                                    />
                                    <label htmlFor="declaration" className="ml-2 text-sm text-gray-700">
                                        <strong>Declaration:</strong> I declare that the information provided is true and accurate. I understand that special vote applications must be submitted at least 14 days before the meeting. I acknowledge that the decision to grant a special vote is at the discretion of the Returning Officer.
                                    </label>
                                </div>
                            </div>

                            {/* Submit Button */}
                            <div className="text-center">
                                <button
                                    type="submit"
                                    disabled={submitting}
                                    className="bg-purple-600 hover:bg-purple-700 text-white font-medium py-3 px-8 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                                >
                                    {submitting ? 'Submitting...' : 'Submit Special Vote Application'}
                                </button>
                            </div>
                        </form>

                        {/* Contact Information */}
                        <div className="mt-8 p-4 bg-blue-50 rounded-lg">
                            <h3 className="font-semibold text-blue-900 mb-2">Questions about your application?</h3>
                            <p className="text-sm text-blue-700">
                                <strong>Returning Officer:</strong> returningofficer@etu.nz<br />
                                <strong>General Support:</strong> support@etu.nz | 0800 1 UNION (0800 186 466)
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </Layout>
    );
}