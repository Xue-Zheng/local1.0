'use client';
import { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import api from '@/services/api';

interface EventMember {
    id: number;
    membershipNumber: string;
    name: string;
    primaryEmail: string;
    dob: string;
    address: string;
    phoneHome: string;
    telephoneMobile: string;
    phoneWork: string;
    employer: string;
    payrollNumber: string;
    siteCode: string;
    employmentStatus: string;
    department: string;
    jobTitle: string;
    location: string;
    regionDesc: string;
    hasRegistered: boolean;
    isAttending: boolean;
    isSpecialVote: boolean;
    absenceReason?: string;
    hasEmail: boolean;
    hasMobile: boolean;
    eventId: number;
    eventName: string;
    eventType: string;
}

interface FinancialFormData {
    name: string;
    primaryEmail: string;
    dob?: string;
    address?: string;
    phoneHome?: string;
    telephoneMobile?: string;
    phoneWork?: string;
    employer?: string;
    payrollNumber?: string;
    siteCode?: string;
    employmentStatus?: string;
    department?: string;
    jobTitle?: string;
    location?: string;
    isConfirmed?: boolean;
}

interface AttendanceRequest {
    isAttending: boolean;
    isSpecialVote?: boolean;
    absenceReason?: string;
    specialVoteEligibilityReason?: string;
}

export default function FinancialFormPage() {
    const router = useRouter();
    const [token, setToken] = useState('');
    const [memberData, setMemberData] = useState<EventMember | null>(null);
    const [formData, setFormData] = useState({
        name: '',
        primaryEmail: '',
        dob: '',
        address: '',
        phoneHome: '',
        telephoneMobile: '',
        phoneWork: '',
        employer: '',
        payrollNumber: '',
        siteCode: '',
        employmentStatus: '',
        department: '',
        jobTitle: '',
        location: '',
    });
    const [isConfirmed, setIsConfirmed] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [isFetching, setIsFetching] = useState(true);
    const [error, setError] = useState('');
    const [formSubmitted, setFormSubmitted] = useState(false);
    const [currentStep, setCurrentStep] = useState(1);
    const totalSteps = 3;
    const [showAttendanceQuestion, setShowAttendanceQuestion] = useState(false);
    const [showSpecialVoteQuestion, setShowSpecialVoteQuestion] = useState(false);
    const [showThankYouMessage, setShowThankYouMessage] = useState(false);
    const [showSpecialVoteOption, setShowSpecialVoteOption] = useState(true);
    const [showReasonSelection, setShowReasonSelection] = useState(false);
    const [attendanceReason, setAttendanceReason] = useState('');
    const [customReason, setCustomReason] = useState('');
    const [showAbsenceForm, setShowAbsenceForm] = useState(false);
    const [wantsSpecialVote, setWantsSpecialVote] = useState<boolean | undefined>(undefined);
    const [isEligibleForSpecialVote, setIsEligibleForSpecialVote] = useState<boolean | undefined>(undefined);
    const [specialVoteEligibilityMessage, setSpecialVoteEligibilityMessage] = useState('');

    // 用于静态导出的客户端URL参数解析
    useEffect(() => {
        if (typeof window !== 'undefined') {
            const urlParams = new URLSearchParams(window.location.search);
            const tokenParam = urlParams.get('token') || '';
            setToken(tokenParam);
        }
    }, []);

    useEffect(() => {
        const fetchMemberData = async () => {
            if (!token) {
                setError('Invalid token. Please ensure you are using the complete link.');
                setIsFetching(false);
                return;
            }
            try {
                const response = await api.get(`/event-registration/member/${token}`);
                if (response.data.status === 'success') {
                    const member = response.data.data;
                    setMemberData(member);
                    setFormData({
                        name: member.name || '',
                        primaryEmail: member.primaryEmail || '',
                        dob: member.dob || '',
                        address: member.address || '',
                        phoneHome: member.phoneHome || '',
                        telephoneMobile: member.telephoneMobile || '',
                        phoneWork: member.phoneWork || '',
                        employer: member.employer || '',
                        payrollNumber: member.payrollNumber || '',
                        siteCode: member.siteCode || '',
                        employmentStatus: member.employmentStatus || '',
                        department: member.department || '',
                        jobTitle: member.jobTitle || '',
                        location: member.location || '',
                    });

                    // Check if already completed
                    if (member.isAttending) {
                        router.push(`/ticket?token=${token}`);
                    } else if (member.isSpecialVote) {
                        router.push('/success');
                    } else {
                        setShowThankYouMessage(true);
                    }
                } else {
                    setError('Failed to load member information');
                }
            } catch (err: any) {
                console.error('Failed to fetch member data:', err);
                setError(err.response?.data?.message || 'Failed to fetch member data');
                toast.error('Failed to fetch member data');
            } finally {
                setIsFetching(false);
            }
        };
        if (token) {
            fetchMemberData();
        }
    }, [token, router]);

    const submitFinancialForm = async (token: string, data: FinancialFormData) => {
        const response = await api.post(`/event-registration/update-form/${token}`, data);
        return response.data;
    };

    const updateAttendance = async (token: string, data: AttendanceRequest) => {
        const response = await api.post(`/event-registration/attendance/${token}`, data);
        return response.data;
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!isConfirmed) {
            setError('Please confirm that all information is correct and accurate.');
            return;
        }

        setIsLoading(true);
        setError('');

        try {
            const dataToSubmit: FinancialFormData = {
                ...formData,
                isConfirmed: true
            };

            await submitFinancialForm(token, dataToSubmit);
            toast.success('Information updated successfully! Please select your attendance below.');
            setFormSubmitted(true);
            setCurrentStep(2);

            // 单页面设计：平滑滚动到第二部分
            setTimeout(() => {
                const step2Element = document.querySelector('[data-step="2"]');
                if (step2Element) {
                    step2Element.scrollIntoView({
                        behavior: 'smooth',
                        block: 'start'
                    });
                }
            }, 100);

            setShowAttendanceQuestion(true);
        } catch (err: any) {
            console.error('Failed to submit financial form:', err);
            setError(err.response?.data?.message || 'Error submitting form');
            toast.error('Failed to submit form');
        } finally {
            setIsLoading(false);
        }
    };

    const handleAttendanceSelection = async (isAttending: boolean) => {
        setIsLoading(true);
        setError('');
        try {
            const requestData = {
                isAttending: true
            };
            await updateAttendance(token, requestData);
            toast.success('Attendance choice updated! Redirecting to your ticket...');
            setTimeout(() => {
                router.push(`/ticket?token=${token}`);
            }, 1500);
        } catch (err: any) {
            console.error('Failed to update attendance choice:', err);
            setError(err.response?.data?.message || 'Error processing your choice');
            toast.error('Failed to update attendance choice');
        } finally {
            setIsLoading(false);
        }
    };

    const handleSubmitAbsence = async () => {
        if (!attendanceReason || (attendanceReason === "custom" && !customReason)) {
            toast.error('Please complete all required fields');
            return;
        }

        if (isEligibleForSpecialVote && wantsSpecialVote === undefined) {
            toast.error('Please select whether you want to apply for special voting rights');
            return;
        }

        setIsLoading(true);
        setError('');
        try {
            const finalReason = attendanceReason === "custom" ? customReason : attendanceReason;

            const requestData = {
                isAttending: false,
                absenceReason: finalReason,
                isSpecialVote: isEligibleForSpecialVote ? (wantsSpecialVote ?? false) : false,
                specialVoteEligibilityReason: isEligibleForSpecialVote && wantsSpecialVote ? attendanceReason : undefined
            };

            await updateAttendance(token, requestData);

            if (isEligibleForSpecialVote && wantsSpecialVote) {
                toast.success('Special voting application submitted successfully!');
                router.push('/success');
            } else {
                toast.info('Your response has been recorded');
                setShowAbsenceForm(false);
                setShowThankYouMessage(true);
            }
        } catch (err: any) {
            console.error('Failed to update attendance choice:', err);
            setError(err.response?.data?.message || 'Error processing your choice');
            toast.error('Failed to update attendance choice');
        } finally {
            setIsLoading(false);
        }
    };

    // Check special vote eligibility based on attendance reason
    const checkSpecialVoteEligibility = (reason: string) => {
        // 首先检查是否是中区或南区 (支持两种格式: "Central Region"/"Central" 和 "Southern Region"/"Southern")
        const regionDesc = memberData?.regionDesc?.toLowerCase() || '';
        const isSpecialVoteRegion = regionDesc.includes('central') || regionDesc.includes('southern');
        const isNorthernRegion = regionDesc.includes('northern');

        if (!isSpecialVoteRegion || isNorthernRegion) {
            setIsEligibleForSpecialVote(false);
            setSpecialVoteEligibilityMessage(''); // 不显示给北区
            return;
        }

        // 根据不同的缺席原因判断是否有资格申请特殊投票
        switch (reason) {
            case 'sick':
                setIsEligibleForSpecialVote(true);
                setSpecialVoteEligibilityMessage('✅ You are eligible for special voting due to illness.');
                break;
            case 'distance':
                setIsEligibleForSpecialVote(true);
                setSpecialVoteEligibilityMessage('✅ You are eligible for special voting due to distance from the meeting location.');
                break;
            case 'work':
                setIsEligibleForSpecialVote(true);
                setSpecialVoteEligibilityMessage('✅ You are eligible for special voting due to work requirements.');
                break;
            case 'custom':
                setIsEligibleForSpecialVote(false);
                setSpecialVoteEligibilityMessage('❌ Other reasons do not qualify for special voting rights.');
                break;
            default:
                setIsEligibleForSpecialVote(false);
                setSpecialVoteEligibilityMessage('❌ This reason does not qualify for special voting rights.');
        }
    };

    const ProgressBar = () => (
        <div className="mb-8">
            <div className="flex items-center justify-between mb-4">
                <div className="flex items-center space-x-4">
                    <div className={`flex items-center justify-center w-10 h-10 rounded-full border-2 transition-all duration-300 ${
                        currentStep >= 1
                            ? 'bg-orange-500 border-orange-500 text-white'
                            : 'border-gray-300 text-gray-300'
                    }`}>
                        {currentStep > 1 ? '✓' : '1'}
                    </div>
                    <div className={`font-medium transition-colors duration-300 ${
                        currentStep >= 1 ? 'text-orange-600 dark:text-orange-400' : 'text-gray-400'
                    }`}>
                        Update Information
                    </div>
                </div>

                <div className={`flex-1 h-1 mx-4 rounded transition-all duration-500 ${
                    currentStep >= 2 ? 'bg-orange-500' : 'bg-gray-200 dark:bg-gray-700'
                }`}></div>

                <div className="flex items-center space-x-4">
                    <div className={`flex items-center justify-center w-10 h-10 rounded-full border-2 transition-all duration-300 ${
                        currentStep >= 2
                            ? 'bg-orange-500 border-orange-500 text-white'
                            : 'border-gray-300 text-gray-300'
                    }`}>
                        {currentStep > 2 ? '✓' : '2'}
                    </div>
                    <div className={`font-medium transition-colors duration-300 ${
                        currentStep >= 2 ? 'text-orange-600 dark:text-orange-400' : 'text-gray-400'
                    }`}>
                        Choose Attendance
                    </div>
                </div>

                <div className={`flex-1 h-1 mx-4 rounded transition-all duration-500 ${
                    currentStep >= 3 ? 'bg-orange-500' : 'bg-gray-200 dark:bg-gray-700'
                }`}></div>

                <div className="flex items-center space-x-4">
                    <div className={`flex items-center justify-center w-10 h-10 rounded-full border-2 transition-all duration-300 ${
                        currentStep >= 3
                            ? 'bg-orange-500 border-orange-500 text-white'
                            : 'border-gray-300 text-gray-300'
                    }`}>
                        {currentStep > 3 ? '✓' : '3'}
                    </div>
                    <div className={`font-medium transition-colors duration-300 ${
                        currentStep >= 3 ? 'text-orange-600 dark:text-orange-400' : 'text-gray-400'
                    }`}>
                        Complete
                    </div>
                </div>
            </div>
        </div>
    );

    if (isFetching) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12 text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-purple-500"></div>
                    <p className="mt-4 text-gray-700 dark:text-gray-300">Loading your information...</p>
                </div>
            </Layout>
        );
    }

    if (error) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12 text-center">
                    <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-700 rounded-lg p-6">
                        <h2 className="text-xl font-bold text-red-800 dark:text-red-300 mb-2">Error</h2>
                        <p className="text-red-700 dark:text-red-400">{error}</p>
                    </div>
                </div>
            </Layout>
        );
    }

    if (showThankYouMessage) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12">
                    <div className="max-w-md mx-auto text-center">
                        <div className="bg-white dark:bg-gray-800 p-8 rounded-lg shadow-md">
                            <h2 className="text-2xl font-bold text-black dark:text-white mb-4">Thank You!</h2>

                            <p className="mb-6 text-gray-700 dark:text-gray-300">
                                Thank you for updating your information. Your information has been successfully saved.
                            </p>
                            <p className="mb-6 text-gray-700 dark:text-gray-300">
                                You have chosen not to attend the meeting or request special voting, so you will not participate in this voting process.
                            </p>

                            {memberData?.absenceReason && (
                                <div className="p-4 bg-gray-50 dark:bg-gray-700 rounded-lg mb-6 text-left">
                                    <p className="font-medium text-gray-700 dark:text-gray-300 mb-1">Reason for not attending:</p>
                                    <p className="text-gray-600 dark:text-gray-400">
                                        {memberData?.absenceReason === 'sick' ? 'I am sick' :
                                            memberData?.absenceReason === 'distance' ? 'I live outside a 32-km radius from the meeting place' :
                                                memberData?.absenceReason === 'work' ? 'My employer requires me to work at the time of the meeting' :
                                                    memberData?.absenceReason}
                                    </p>
                                </div>
                            )}

                            <p className="text-sm text-gray-600 dark:text-gray-400 mb-6">
                                If you have any questions or need support, please contact us at:
                            </p>
                            <div className="space-y-4">
                                <div className="p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
                                    <div className="text-sm text-blue-700 dark:text-blue-300">
                                        <p className="font-medium mb-2">Contact Information:</p>
                                        <p>📧 Email: support@etu.nz</p>
                                        <p>📞 Phone: 0800 1 UNION (0800 186 466)</p>
                                        <p className="text-xs mt-2 opacity-75">We look forward to seeing you there.</p>
                                        <p className="text-xs opacity-75">In solidarity,</p>
                                        <p className="text-xs font-medium opacity-75">Rachel Mackintosh, National Secretary</p>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
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
                            <h1 className="text-3xl font-bold text-center text-gray-900 dark:text-white mb-8">
                                2025 E tu Biennial Membership Meeting
                            </h1>

                            <ProgressBar />

                            {/* BMM Meeting Information */}
                            <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-700 rounded-lg p-6 mb-6">
                                <h2 className="text-xl font-bold text-blue-900 dark:text-blue-100 mb-4">
                                    Confirm your attendance – 2025 E tu Biennial Membership Meeting
                                </h2>
                                <p className="text-blue-800 dark:text-blue-200 mb-4">
                                    Thank you for pre-registering your interest to attend the 2025 Biennial Membership Meetings (BMM).
                                    You're just one step away from securing your spot!
                                </p>

                                <div className="bg-white dark:bg-gray-800 rounded-lg p-4 mb-4">
                                    <h3 className="font-bold text-gray-900 dark:text-white mb-3">📍 Your Meeting Details</h3>
                                    <div className="space-y-2 text-sm">
                                        <div className="flex">
                                            <span className="font-medium w-20">📅 Date:</span>
                                            <span>September 2025 (exact date TBC)</span>
                                        </div>
                                        <div className="flex">
                                            <span className="font-medium w-20">🌏 Region:</span>
                                            <span>{memberData?.regionDesc || 'TBC'}</span>
                                        </div>
                                        <div className="flex">
                                            <span className="font-medium w-20">🏢 Venue:</span>
                                            <span>To be confirmed based on your region</span>
                                        </div>
                                        <div className="flex">
                                            <span className="font-medium w-20">⏰ Time:</span>
                                            <span>Full day event (detailed schedule will be provided)</span>
                                        </div>
                                    </div>
                                </div>

                                <div className="bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-700 rounded-lg p-4">
                                    <h4 className="font-bold text-orange-900 dark:text-orange-100 mb-2">🎫 Your Attendance Ticket</h4>
                                    <p className="text-orange-800 dark:text-orange-200 text-sm mb-2">
                                        Once you confirm your attendance, you will receive a <strong>personalized ticket</strong> for entry to your BMM meeting.
                                    </p>
                                    <ul className="text-orange-700 dark:text-orange-300 text-sm space-y-1">
                                        <li>• This ticket will be sent to you by email (and/or available for download)</li>
                                        <li>• You must bring this ticket with you to the meeting - either printed or on your phone</li>
                                        <li>• Your ticket will be used to register your attendance on the day</li>
                                        <li>• Without a ticket, you may not be able to enter the meeting</li>
                                    </ul>
                                </div>
                            </div>

                            {memberData && (
                                <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-6 mb-6">
                                    <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                                        🏷️ Member Details
                                    </h3>
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                        <div>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Name</p>
                                            <p className="font-medium text-gray-900 dark:text-white">{memberData.name}</p>
                                        </div>
                                        <div>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">🌏 Region</p>
                                            <p className="font-medium text-gray-900 dark:text-white">{memberData.regionDesc}</p>
                                        </div>
                                        <div>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Membership Number</p>
                                            <p className="font-medium text-gray-900 dark:text-white">{memberData.membershipNumber}</p>
                                        </div>
                                        <div>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">Email</p>
                                            <p className="font-medium text-gray-900 dark:text-white">{memberData.primaryEmail}</p>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Stage 1: Update Information */}
                            {!formSubmitted && (
                                <div data-step="1" className="mb-8">
                                    <form onSubmit={handleSubmit} className="space-y-6">
                                        <div className="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4 mb-6">
                                            <h2 className="text-lg font-semibold text-blue-800 dark:text-blue-200 mb-2">📝 Stage 1: Update Your Information</h2>
                                            <p className="text-blue-700 dark:text-blue-300 text-sm">
                                                Please review and update your contact and employment information below.
                                            </p>
                                        </div>

                                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                    Full Name *
                                                </label>
                                                <input
                                                    type="text"
                                                    name="name"
                                                    value={formData.name}
                                                    onChange={(e) => setFormData({...formData, name: e.target.value})}
                                                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-orange-500"
                                                    required
                                                />
                                            </div>

                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                    Email Address *
                                                </label>
                                                <input
                                                    type="email"
                                                    name="primaryEmail"
                                                    value={formData.primaryEmail}
                                                    onChange={(e) => setFormData({...formData, primaryEmail: e.target.value})}
                                                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-orange-500"
                                                    required
                                                />
                                            </div>

                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                    Date of Birth
                                                </label>
                                                <input
                                                    type="date"
                                                    name="dob"
                                                    value={formData.dob}
                                                    onChange={(e) => setFormData({...formData, dob: e.target.value})}
                                                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-orange-500"
                                                />
                                            </div>

                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                    Mobile Phone *
                                                </label>
                                                <input
                                                    type="tel"
                                                    name="telephoneMobile"
                                                    value={formData.telephoneMobile}
                                                    onChange={(e) => setFormData({...formData, telephoneMobile: e.target.value})}
                                                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-orange-500"
                                                    required
                                                />
                                            </div>

                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                    Home Phone
                                                </label>
                                                <input
                                                    type="tel"
                                                    name="phoneHome"
                                                    value={formData.phoneHome}
                                                    onChange={(e) => setFormData({...formData, phoneHome: e.target.value})}
                                                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-orange-500"
                                                />
                                            </div>

                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                    Work Phone
                                                </label>
                                                <input
                                                    type="tel"
                                                    name="phoneWork"
                                                    value={formData.phoneWork}
                                                    onChange={(e) => setFormData({...formData, phoneWork: e.target.value})}
                                                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-orange-500"
                                                />
                                            </div>
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                Home Address *
                                            </label>
                                            <textarea
                                                name="address"
                                                value={formData.address}
                                                onChange={(e) => setFormData({...formData, address: e.target.value})}
                                                className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-orange-500"
                                                rows={3}
                                                required
                                            />
                                        </div>

                                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                    Employer
                                                </label>
                                                <input
                                                    type="text"
                                                    name="employer"
                                                    value={formData.employer}
                                                    onChange={(e) => setFormData({...formData, employer: e.target.value})}
                                                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-orange-500"
                                                />
                                            </div>

                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                    Job Title
                                                </label>
                                                <input
                                                    type="text"
                                                    name="jobTitle"
                                                    value={formData.jobTitle}
                                                    onChange={(e) => setFormData({...formData, jobTitle: e.target.value})}
                                                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-orange-500"
                                                />
                                            </div>

                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                    Department
                                                </label>
                                                <input
                                                    type="text"
                                                    name="department"
                                                    value={formData.department}
                                                    onChange={(e) => setFormData({...formData, department: e.target.value})}
                                                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-orange-500"
                                                />
                                            </div>

                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                    Payroll Number
                                                </label>
                                                <input
                                                    type="text"
                                                    name="payrollNumber"
                                                    value={formData.payrollNumber}
                                                    onChange={(e) => setFormData({...formData, payrollNumber: e.target.value})}
                                                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-orange-500"
                                                />
                                            </div>
                                        </div>

                                        {/* Confirmation Checkbox */}
                                        <div className="border-t border-gray-200 dark:border-gray-600 pt-6">
                                            <label className="flex items-start space-x-3 cursor-pointer">
                                                <input
                                                    type="checkbox"
                                                    checked={isConfirmed}
                                                    onChange={(e) => setIsConfirmed(e.target.checked)}
                                                    className="mt-1 w-5 h-5 text-orange-600 focus:ring-orange-500 border-gray-300 rounded"
                                                    required
                                                />
                                                <div className="text-sm">
                                                    <p className="font-medium text-gray-900 dark:text-white">
                                                        ✅ I confirm that all information provided is correct and accurate
                                                    </p>
                                                    <p className="text-gray-600 dark:text-gray-400 mt-1">
                                                        By checking this box, I acknowledge that the information I have provided is true and complete to the best of my knowledge.
                                                    </p>
                                                </div>
                                            </label>
                                        </div>

                                        {error && (
                                            <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-700 rounded-lg p-4">
                                                <p className="text-red-800 dark:text-red-300">{error}</p>
                                            </div>
                                        )}

                                        <div className="text-center pt-6">
                                            <button
                                                type="submit"
                                                disabled={isLoading || !isConfirmed}
                                                className="bg-orange-600 hover:bg-orange-700 text-white font-bold py-3 px-8 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                                            >
                                                {isLoading ? 'Saving...' : '📤 Continue to Attendance Selection'}
                                            </button>
                                        </div>
                                    </form>
                                </div>
                            )}

                            {/* Stage 2: Attendance Selection */}
                            {formSubmitted && showAttendanceQuestion && (
                                <div data-step="2" className="space-y-8">
                                    <div className="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4 mb-6">
                                        <h2 className="text-lg font-semibold text-blue-800 dark:text-blue-200 mb-2">🗳️ Stage 2: Confirm Your Attendance</h2>
                                        <p className="text-blue-700 dark:text-blue-300 text-sm">
                                            Now please select whether you plan to attend the BMM meeting:
                                        </p>
                                    </div>

                                    <div className="flex flex-col gap-6 md:flex-row md:gap-8 mb-8">
                                        <button
                                            onClick={() => handleAttendanceSelection(true)}
                                            className="bg-orange-500 hover:bg-orange-600 dark:bg-orange-600 dark:hover:bg-orange-700 text-white flex-1 py-6 px-8 rounded-lg transition-colors transform hover:scale-105 font-semibold text-lg shadow-lg active:scale-95"
                                            disabled={isLoading}
                                        >
                                            <div className="text-center">
                                                <div className="text-2xl mb-2">✅</div>
                                                <div>Yes, I will attend the meeting</div>
                                                <div className="text-sm opacity-90 mt-1">I'll participate in person</div>
                                            </div>
                                        </button>
                                        <button
                                            onClick={() => setShowAbsenceForm(true)}
                                            className="border-2 border-gray-300 dark:border-gray-600 hover:border-orange-400 dark:hover:border-orange-500 text-gray-700 dark:text-gray-300 hover:text-orange-600 dark:hover:text-orange-400 flex-1 py-6 px-8 rounded-lg transition-colors transform hover:scale-105 font-semibold text-lg shadow-lg active:scale-95"
                                            disabled={isLoading}
                                        >
                                            <div className="text-center">
                                                <div className="text-2xl mb-2">❌</div>
                                                <div>No, I cannot attend the meeting</div>
                                                <div className="text-sm opacity-75 mt-1">I need to request special voting</div>
                                            </div>
                                        </button>
                                    </div>

                                    {/* Absence Form */}
                                    {showAbsenceForm && (
                                        <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-700 rounded-lg p-6">
                                            <h3 className="text-lg font-semibold text-yellow-800 dark:text-yellow-200 mb-4">
                                                📋 Please select your reason for not attending:
                                            </h3>

                                            <div className="space-y-4 mb-6">
                                                {/* Sick */}
                                                <div className={`border-2 rounded-lg p-4 cursor-pointer transition-all duration-200 hover:shadow-md ${
                                                    attendanceReason === "sick"
                                                        ? 'border-orange-500 bg-orange-50 dark:bg-orange-900/20'
                                                        : 'border-gray-200 dark:border-gray-600 hover:border-orange-300'
                                                }`}>
                                                    <label className="flex items-start cursor-pointer">
                                                        <input
                                                            type="radio"
                                                            name="attendanceReason"
                                                            value="sick"
                                                            className="mt-1 mr-4 w-4 h-4 text-orange-500 border-gray-300 focus:ring-orange-500"
                                                            checked={attendanceReason === "sick"}
                                                            onChange={() => {
                                                                setAttendanceReason("sick");
                                                                setCustomReason('');
                                                                checkSpecialVoteEligibility("sick");
                                                            }}
                                                        />
                                                        <div className="flex-1">
                                                            <div className="flex items-center mb-1">
                                                                <span className="text-2xl mr-3">🤒</span>
                                                                <span className="font-medium text-gray-900 dark:text-white">I am sick</span>
                                                            </div>
                                                            <p className="text-sm text-gray-600 dark:text-gray-400 ml-11">
                                                                You are unable to attend due to illness or health reasons
                                                            </p>
                                                        </div>
                                                    </label>
                                                </div>

                                                {/* Distance */}
                                                <div className={`border-2 rounded-lg p-4 cursor-pointer transition-all duration-200 hover:shadow-md ${
                                                    attendanceReason === "distance"
                                                        ? 'border-orange-500 bg-orange-50 dark:bg-orange-900/20'
                                                        : 'border-gray-200 dark:border-gray-600 hover:border-orange-300'
                                                }`}>
                                                    <label className="flex items-start cursor-pointer">
                                                        <input
                                                            type="radio"
                                                            name="attendanceReason"
                                                            value="distance"
                                                            className="mt-1 mr-4 w-4 h-4 text-orange-500 border-gray-300 focus:ring-orange-500"
                                                            checked={attendanceReason === "distance"}
                                                            onChange={() => {
                                                                setAttendanceReason("distance");
                                                                setCustomReason('');
                                                                checkSpecialVoteEligibility("distance");
                                                            }}
                                                        />
                                                        <div className="flex-1">
                                                            <div className="flex items-center mb-1">
                                                                <span className="text-2xl mr-3">📍</span>
                                                                <span className="font-medium text-gray-900 dark:text-white">Distance from meeting location</span>
                                                            </div>
                                                            <p className="text-sm text-gray-600 dark:text-gray-400 ml-11">
                                                                You live outside a 32-km radius from the meeting place
                                                            </p>
                                                        </div>
                                                    </label>
                                                </div>

                                                {/* Work */}
                                                <div className={`border-2 rounded-lg p-4 cursor-pointer transition-all duration-200 hover:shadow-md ${
                                                    attendanceReason === "work"
                                                        ? 'border-orange-500 bg-orange-50 dark:bg-orange-900/20'
                                                        : 'border-gray-200 dark:border-gray-600 hover:border-orange-300'
                                                }`}>
                                                    <label className="flex items-start cursor-pointer">
                                                        <input
                                                            type="radio"
                                                            name="attendanceReason"
                                                            value="work"
                                                            className="mt-1 mr-4 w-4 h-4 text-orange-500 border-gray-300 focus:ring-orange-500"
                                                            checked={attendanceReason === "work"}
                                                            onChange={() => {
                                                                setAttendanceReason("work");
                                                                setCustomReason('');
                                                                checkSpecialVoteEligibility("work");
                                                            }}
                                                        />
                                                        <div className="flex-1">
                                                            <div className="flex items-center mb-1">
                                                                <span className="text-2xl mr-3">💼</span>
                                                                <span className="font-medium text-gray-900 dark:text-white">Work commitment</span>
                                                            </div>
                                                            <p className="text-sm text-gray-600 dark:text-gray-400 ml-11">
                                                                Your employer requires you to work at the time of the meeting
                                                            </p>
                                                        </div>
                                                    </label>
                                                </div>

                                                {/* Other */}
                                                <div className={`border-2 rounded-lg p-4 cursor-pointer transition-all duration-200 hover:shadow-md ${
                                                    attendanceReason === "custom"
                                                        ? 'border-orange-500 bg-orange-50 dark:bg-orange-900/20'
                                                        : 'border-gray-200 dark:border-gray-600 hover:border-orange-300'
                                                }`}>
                                                    <label className="flex items-start cursor-pointer">
                                                        <input
                                                            type="radio"
                                                            name="attendanceReason"
                                                            value="custom"
                                                            className="mt-1 mr-4 w-4 h-4 text-orange-500 border-gray-300 focus:ring-orange-500"
                                                            checked={attendanceReason === "custom"}
                                                            onChange={() => {
                                                                setAttendanceReason("custom");
                                                                checkSpecialVoteEligibility("custom");
                                                            }}
                                                        />
                                                        <div className="flex-1">
                                                            <div className="flex items-center mb-1">
                                                                <span className="text-2xl mr-3">✏️</span>
                                                                <span className="font-medium text-gray-900 dark:text-white">Other reason</span>
                                                            </div>
                                                            <p className="text-sm text-gray-600 dark:text-gray-400 ml-11">
                                                                Please specify your reason below
                                                            </p>
                                                        </div>
                                                    </label>
                                                    {attendanceReason === "custom" && (
                                                        <div className="mt-3 ml-11">
                                                            <textarea
                                                                value={customReason}
                                                                onChange={(e) => setCustomReason(e.target.value)}
                                                                placeholder="Please specify your reason for not attending..."
                                                                className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-500"
                                                                rows={3}
                                                                required
                                                            />
                                                        </div>
                                                    )}
                                                </div>
                                            </div>

                                            {/* Special Vote Question - Only for Southern Region */}
                                            {attendanceReason && memberData?.regionDesc?.toLowerCase().includes('southern') && (
                                                <div className="border-t border-yellow-200 dark:border-yellow-700 pt-4">
                                                    {/* 显示特殊投票资格状态 */}
                                                    {specialVoteEligibilityMessage && (
                                                        <div className={`mb-4 p-3 border rounded-lg ${
                                                            isEligibleForSpecialVote
                                                                ? 'bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-700'
                                                                : 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-700'
                                                        }`}>
                                                            <p className={`text-sm font-medium ${
                                                                isEligibleForSpecialVote
                                                                    ? 'text-green-700 dark:text-green-300'
                                                                    : 'text-red-700 dark:text-red-300'
                                                            }`}>
                                                                {specialVoteEligibilityMessage}
                                                            </p>
                                                        </div>
                                                    )}

                                                    {/* 只有符合条件时才显示特殊投票申请选项 */}
                                                    {attendanceReason !== 'custom' && isEligibleForSpecialVote && (
                                                        <div>
                                                            <p className="font-medium text-yellow-800 dark:text-yellow-200 mb-4">
                                                                🗳️ Would you like to apply for special voting rights?
                                                            </p>
                                                            <div className="space-y-3">
                                                                <div className={`border-2 rounded-lg p-3 cursor-pointer transition-all duration-200 ${
                                                                    wantsSpecialVote === true
                                                                        ? 'border-green-500 bg-green-50 dark:bg-green-900/20'
                                                                        : 'border-gray-200 dark:border-gray-600 hover:border-green-300'
                                                                }`}>
                                                                    <label className="flex items-start cursor-pointer">
                                                                        <input
                                                                            type="radio"
                                                                            name="specialVote"
                                                                            value="yes"
                                                                            className="mt-1 mr-3 w-4 h-4 text-green-600 border-gray-300 focus:ring-green-500"
                                                                            checked={wantsSpecialVote === true}
                                                                            onChange={() => setWantsSpecialVote(true)}
                                                                        />
                                                                        <div className="flex-1">
                                                                            <div className="flex items-center mb-1">
                                                                                <span className="text-xl mr-2">✅</span>
                                                                                <span className="font-medium text-gray-900 dark:text-white">Yes, I want to apply for special voting rights</span>
                                                                            </div>
                                                                            <p className="text-sm text-gray-600 dark:text-gray-400 ml-6">
                                                                                You will be able to vote even though you cannot attend the meeting
                                                                            </p>
                                                                        </div>
                                                                    </label>
                                                                </div>

                                                                <div className={`border-2 rounded-lg p-3 cursor-pointer transition-all duration-200 ${
                                                                    wantsSpecialVote === false
                                                                        ? 'border-gray-500 bg-gray-50 dark:bg-gray-900/20'
                                                                        : 'border-gray-200 dark:border-gray-600 hover:border-gray-300'
                                                                }`}>
                                                                    <label className="flex items-start cursor-pointer">
                                                                        <input
                                                                            type="radio"
                                                                            name="specialVote"
                                                                            value="no"
                                                                            className="mt-1 mr-3 w-4 h-4 text-gray-600 border-gray-300 focus:ring-gray-500"
                                                                            checked={wantsSpecialVote === false}
                                                                            onChange={() => setWantsSpecialVote(false)}
                                                                        />
                                                                        <div className="flex-1">
                                                                            <div className="flex items-center mb-1">
                                                                                <span className="text-xl mr-2">❌</span>
                                                                                <span className="font-medium text-gray-900 dark:text-white">No, I do not want to apply for special voting rights</span>
                                                                            </div>
                                                                            <p className="text-sm text-gray-600 dark:text-gray-400 ml-6">
                                                                                You will not participate in the voting process for this meeting
                                                                            </p>
                                                                        </div>
                                                                    </label>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    )}
                                                </div>
                                            )}

                                            {/* Action Buttons */}
                                            <div className="flex justify-between pt-4">
                                                <button
                                                    onClick={() => {
                                                        setShowAbsenceForm(false);
                                                        setAttendanceReason('');
                                                        setCustomReason('');
                                                        setWantsSpecialVote(undefined);
                                                        setIsEligibleForSpecialVote(undefined);
                                                        setSpecialVoteEligibilityMessage('');
                                                    }}
                                                    className="text-gray-600 dark:text-gray-400 hover:text-black dark:hover:text-white"
                                                    disabled={isLoading}
                                                >
                                                    <span className="flex items-center">
                                                        <svg className="w-5 h-5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                                                        </svg>
                                                        Back to attendance choice
                                                    </span>
                                                </button>

                                                <button
                                                    onClick={handleSubmitAbsence}
                                                    className="bg-orange-500 hover:bg-orange-600 text-white px-6 py-2 rounded transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                                                    disabled={
                                                        isLoading ||
                                                        !attendanceReason ||
                                                        (attendanceReason === "custom" && !customReason) ||
                                                        (isEligibleForSpecialVote && wantsSpecialVote === undefined)
                                                    }
                                                >
                                                    {isLoading ? 'Processing...' : '📤 Submit Absence Request'}
                                                </button>
                                            </div>
                                        </div>
                                    )}

                                    {!showAbsenceForm && (
                                        <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-6">
                                            <div className="flex items-start">
                                                <div className="flex-shrink-0">
                                                    <svg className="w-6 h-6 text-blue-600 dark:text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                                                    </svg>
                                                </div>
                                                <div className="ml-3">
                                                    <h3 className="text-sm font-medium text-blue-800 dark:text-blue-300">Important: Your choice determines voting access</h3>
                                                    <div className="mt-2 text-sm text-blue-700 dark:text-blue-300">
                                                        <p>• <strong>Attend:</strong> You'll receive your meeting ticket and can vote during the meeting</p>
                                                        <p>• <strong>Cannot attend:</strong> You may be eligible for special voting rights depending on your reason</p>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    )}

                                    {isLoading && (
                                        <div className="mt-6 text-center">
                                            <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-orange-500"></div>
                                            <p className="mt-2 text-gray-700 dark:text-gray-300 font-medium">Processing your choice...</p>
                                        </div>
                                    )}
                                    {error && (
                                        <div className="mt-4 p-4 bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-400 rounded-lg border border-red-200 dark:border-red-800">
                                            <div className="flex items-center">
                                                <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.732-.833-2.464 0L3.34 16.5c-.77.833.192 2.5 1.732 2.5z" />
                                                </svg>
                                                {error}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </Layout>
    );
}