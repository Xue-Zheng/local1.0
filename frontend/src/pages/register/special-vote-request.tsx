'use client';
import { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import api from '@/services/api';

interface FinancialFormData {
    name: string;
    postalAddress: string;
    employerName: string;
    phoneHome: string;
    phoneWork: string;
    telephoneMobile: string;
    primaryEmail: string;
    dob: string;
    payrollNumber: string;
    siteCode: string;
    employmentStatus: string;
    department: string;
    jobTitle: string;
    location: string;
    employer: string;
    membershipNumber: string;
}

interface MemberData {
    name: string;
    membershipNumber: string;
    region: string;
    email: string;
    forum: string;
    financialFormData: FinancialFormData;
}

export default function SpecialVoteRequestPage() {
    const router = useRouter();
    const { token } = router.query;
    const [memberData, setMemberData] = useState<MemberData | null>(null);
    const [financialForm, setFinancialForm] = useState<FinancialFormData>({
        name: '',
        postalAddress: '',
        employerName: '',
        phoneHome: '',
        phoneWork: '',
        telephoneMobile: '',
        primaryEmail: '',
        dob: '',
        payrollNumber: '',
        siteCode: '',
        employmentStatus: '',
        department: '',
        jobTitle: '',
        location: '',
        employer: '',
        membershipNumber: ''
    });
    const [specialVoteChoice, setSpecialVoteChoice] = useState<boolean | null>(null);
    const [specialVoteReason, setSpecialVoteReason] = useState('');
    const [isLoading, setIsLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [showSuccessPage, setShowSuccessPage] = useState(false);

    useEffect(() => {
        if (token) {
            fetchMemberData();
        }
    }, [token]);

    const fetchMemberData = async () => {
        try {
            const response = await api.get(`/event-registration/member/${token}`);
            if (response.data.status === 'success') {
                const member = response.data.data;

                // Transform member data to expected format
                const data = {
                    name: member.name,
                    membershipNumber: member.membershipNumber,
                    region: member.regionDesc,
                    email: member.primaryEmail || member.email,
                    forum: member.forumDesc,
                    financialFormData: {
                        name: member.name || '',
                        postalAddress: member.postalAddress || member.address || member.homeAddress || '',
                        employerName: member.employerName || member.employer || member.workplaceInfo || '',
                        phoneHome: member.phoneHome || member.homeTelephone || '',
                        phoneWork: member.phoneWork || member.workTelephone || '',
                        telephoneMobile: member.telephoneMobile || member.mobile || '',
                        primaryEmail: member.primaryEmail || member.email || '',
                        dob: member.dob || member.dateOfBirth || '',
                        payrollNumber: member.payrollNumber || member.payrollNo || '',
                        siteCode: member.siteCode || member.siteNumber || '',
                        employmentStatus: member.employmentStatus || '',
                        department: member.department || member.departmentDesc || '',
                        jobTitle: member.jobTitle || member.occupation || '',
                        location: member.location || member.workplace || '',
                        employer: member.employerName || member.employer || member.workplaceInfo || '',
                        membershipNumber: member.membershipNumber
                    }
                };

                setMemberData(data);

                // Pre-fill financial form
                setFinancialForm(data.financialFormData);
            }
        } catch (error) {
            console.error('Error fetching member data:', error);
            toast.error('Failed to load member information');
        } finally {
            setIsLoading(false);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (specialVoteChoice === null) {
            toast.error('Please select whether you would like to request a special vote');
            return;
        }

        // No need to check for special vote reason anymore

        setIsSubmitting(true);

        try {
            // First update financial form
            await api.post('/bmm/update-financial-form', {
                memberToken: token,
                financialForm
            });

            // Then submit special vote decision
            const response = await api.post('/bmm/cancelled-venue-response', {
                memberToken: token,
                isSpecialVote: specialVoteChoice,
                specialVoteReason: specialVoteChoice ? 'Venue cancelled - West Coast' : null
            });

            if (response.data.status === 'success') {
                setShowSuccessPage(true);
            }
        } catch (error) {
            console.error('Error submitting form:', error);
            toast.error('Failed to submit your response. Please try again.');
        } finally {
            setIsSubmitting(false);
        }
    };

    if (isLoading) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12 text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-orange-500"></div>
                    <p className="mt-4 text-gray-700 dark:text-gray-300">Loading your information...</p>
                </div>
            </Layout>
        );
    }

    if (!memberData) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12">
                    <div className="max-w-md mx-auto p-6 bg-red-50 dark:bg-red-900/30 rounded-lg text-center">
                        <h2 className="text-2xl font-bold text-red-600 dark:text-red-400 mb-4">Invalid Link</h2>
                        <p className="text-gray-800 dark:text-gray-200">This link is invalid or has expired.</p>
                    </div>
                </div>
            </Layout>
        );
    }

    if (showSuccessPage) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12">
                    <div className="max-w-2xl mx-auto">
                        <div className="bg-green-50 dark:bg-green-900/30 p-8 rounded-lg text-center">
                            <h1 className="text-3xl font-bold text-green-800 dark:text-green-200 mb-4">
                                Thank You!
                            </h1>
                            <p className="text-lg text-gray-700 dark:text-gray-300 mb-4">
                                Your information has been updated successfully.
                            </p>
                            {specialVoteChoice && (
                                <div className="space-y-3">
                                    <p className="text-gray-700 dark:text-gray-300">
                                        Your special vote request for the 2025 E tū Biennial Membership Meeting has been submitted.
                                    </p>
                                    <p className="text-gray-700 dark:text-gray-300">
                                        It will be processed according to the E tū constitution, and we'll be in touch with you shortly.
                                    </p>
                                </div>
                            )}

                            <div className="mt-8 pt-6 border-t border-gray-200 dark:border-gray-700">
                                <h3 className="font-bold text-lg mb-3 flex items-center justify-center">
                                    <span className="mr-2">ℹ</span> Need help?
                                </h3>
                                <p className="text-gray-700 dark:text-gray-300 mb-4">
                                    If you have any questions about your meeting details, ticket, or need assistance, contact us at:
                                </p>
                                <div className="flex flex-col sm:flex-row gap-4 justify-center">
                                    <p className="flex items-center justify-center">
                                        <span className="mr-2">📧</span>
                                        <a href="mailto:support@etu.nz" className="text-blue-600 hover:underline font-semibold">support@etu.nz</a>
                                    </p>
                                    <p className="flex items-center justify-center">
                                        <span className="mr-2">📞</span>
                                        <strong>0800 1 UNION (0800 186 466)</strong>
                                    </p>
                                </div>
                                <p className="text-gray-700 dark:text-gray-300 mt-6 font-semibold">
                                    We look forward to seeing you there and hearing your voice!
                                </p>
                                <p className="text-gray-700 dark:text-gray-300 mt-2 font-bold">
                                    Together, we are E tū.
                                </p>
                            </div>
                        </div>
                    </div>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="container mx-auto px-4 py-8">
                <div className="max-w-3xl mx-auto">
                    {/* Important Notice */}
                    <div className="bg-yellow-50 dark:bg-yellow-900/20 border-2 border-yellow-300 dark:border-yellow-700 rounded-lg p-6 mb-8">
                        <h2 className="text-xl font-bold text-gray-800 dark:text-gray-200 mb-3">
                            Special Vote Request - 2025 E tū Biennial Membership Meetings –West Coast Members (Hokitika, Reefton & Greymouth)
                        </h2>
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
                            <h3 className="font-bold mb-2 flex items-center">
                                <span className="mr-2">🗳</span> But You Can Still Vote – Request a Special Vote
                            </h3>
                            <p className="text-gray-700 dark:text-gray-300 mb-3">
                                Although we won't be meeting in person in your area, you are still <strong>eligible to vote</strong> in the election of the National Executive Regional Representative for your region.
                            </p>
                            <p className="text-gray-700 dark:text-gray-300">
                                <strong>If you wish to participate in the election, you can request a special vote by submitting your request below! </strong>
                            </p>
                        </div>

                        <div className="mb-4">
                            <h3 className="font-bold mb-2 flex items-center">
                                <span className="mr-2">🗳</span> What Happens Next?
                            </h3>
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

                    <h1 className="text-3xl font-bold text-black dark:text-white mb-2">
                        Update Your Information
                    </h1>
                    <p className="text-gray-600 dark:text-gray-400 mb-8">
                        Please review and update your contact information, then indicate if you would like to request a special vote.
                    </p>

                    <form onSubmit={handleSubmit} className="space-y-8">
                        {/* Financial Information Form - Same layout as confirmation page */}
                        <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-sm">
                            <h2 className="text-xl font-semibold mb-4">Update Your Information</h2>
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
                                        onChange={(e) => setFinancialForm({...financialForm, name: e.target.value})}
                                        className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                    />
                                </div>

                                <div className="md:col-span-2">
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                        Current home address
                                    </label>
                                    <textarea
                                        value={financialForm.postalAddress}
                                        onChange={(e) => setFinancialForm({...financialForm, postalAddress: e.target.value})}
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
                                        onChange={(e) => setFinancialForm({...financialForm, phoneHome: e.target.value})}
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
                                        onChange={(e) => setFinancialForm({...financialForm, phoneWork: e.target.value})}
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
                                        onChange={(e) => setFinancialForm({...financialForm, telephoneMobile: e.target.value})}
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
                                        onChange={(e) => setFinancialForm({...financialForm, primaryEmail: e.target.value})}
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
                                        onChange={(e) => setFinancialForm({...financialForm, dob: e.target.value})}
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
                                        value={financialForm.membershipNumber}
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
                                        value={financialForm.payrollNumber || ''}
                                        onChange={(e) => setFinancialForm({...financialForm, payrollNumber: e.target.value})}
                                        className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                        placeholder="Enter if known"
                                    />
                                </div>
                            </div>

                            {/* Electronic Signature Notice */}
                            <div className="mt-6 p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-700 rounded-lg">
                                <p className="text-sm text-blue-800 dark:text-blue-300">
                                    <strong>📝 Electronic Signature:</strong> By updating your information and submitting this form,
                                    you are providing your electronic signature to confirm your details and special vote request.
                                    This serves as your official confirmation.
                                </p>
                            </div>
                        </div>

                        {/* Special Vote Section */}
                        <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-sm">
                            <h2 className="text-xl font-semibold mb-4">Special Vote Request</h2>

                            <p className="text-gray-700 dark:text-gray-300 mb-4">
                                As a member affected by the meeting cancellation, you are eligible to request a special vote
                                to participate in the BMM decisions.
                            </p>

                            <div className="space-y-4">
                                <div>
                                    <p className="font-medium mb-3">Would you like to request a special vote?*</p>
                                    <div className="space-y-2">
                                        <label className="flex items-center cursor-pointer">
                                            <input
                                                type="radio"
                                                name="specialVote"
                                                value="yes"
                                                checked={specialVoteChoice === true}
                                                onChange={() => setSpecialVoteChoice(true)}
                                                className="mr-3"
                                            />
                                            <span>Yes, I would like to request a special vote</span>
                                        </label>
                                        <label className="flex items-center cursor-pointer">
                                            <input
                                                type="radio"
                                                name="specialVote"
                                                value="no"
                                                checked={specialVoteChoice === false}
                                                onChange={() => {
                                                    setSpecialVoteChoice(false);
                                                    setSpecialVoteReason('');
                                                }}
                                                className="mr-3"
                                            />
                                            <span>No, I do not need a special vote</span>
                                        </label>
                                    </div>
                                </div>

                                {specialVoteChoice === true && (
                                    <div className="bg-blue-50 dark:bg-blue-900/20 p-4 rounded-lg">
                                        <p className="text-sm text-blue-800 dark:text-blue-300">
                                            ✓ You are eligible for a special vote due to the venue cancellation
                                        </p>
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* Confirmation Notice */}
                        <div className="bg-gray-50 dark:bg-gray-800/50 p-4 rounded-lg text-center">
                            <p className="text-sm text-gray-700 dark:text-gray-300">
                                By clicking submit, you confirm that the information provided is accurate and this constitutes your electronic signature.
                            </p>
                        </div>

                        {/* Submit Button */}
                        <div className="flex justify-center">
                            <button
                                type="submit"
                                disabled={isSubmitting}
                                className={`px-8 py-3 rounded-md font-medium transition-colors ${
                                    isSubmitting
                                        ? 'bg-gray-400 cursor-not-allowed'
                                        : 'bg-orange-500 hover:bg-orange-600 text-white'
                                }`}
                            >
                                {isSubmitting ? 'Submitting...' : 'Submit'}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </Layout>
    );
}