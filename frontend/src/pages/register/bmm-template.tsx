'use client';
import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import Layout from '../../components/common/Layout';
import { toast } from 'react-toastify';
import api from '@/services/api';
import ForumVenueSelector from '@/components/bmm/ForumVenueSelector';
import { hasMultipleVenues, forumVenueOptions } from '@/config/venueMapping';

interface MemberData {
    id: number;
    membershipNumber: string;
    name: string;
    email: string;
    telephoneMobile: string;
    regionDesc: string;
    bmmStage: string;
    preferredVenuesJson?: string;
    preferredDatesJson?: string;
    preferredTimesJson?: string;
    forumDesc?: string;
    assignedVenue?: any;
}

interface BMMPreferences {
    preferredVenue: string; // Only one venue - now automatically assigned
    preferredDates: string[];
    preferredTime: string; // Session time - 改为单选
    intendToAttend: boolean | null; // Initial intention (not final decision)
    workplaceInfo: string;
    additionalComments: string;
    suggestedVenue: string;
    preferenceSpecialVote: boolean | null; // 新增：pre-registration阶段的特殊投票意向
}

interface Venue {
    id?: number;
    name: string;
    venue: string;
    address: string;
    region: string;
    date: string;
    capacity?: number;
    notes?: string;
}

export default function BMMPreferencePage() {
    const router = useRouter();
    const [token, setToken] = useState<string>('');
    const [memberData, setMemberData] = useState<MemberData | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string>('');
    const [venueOptions, setVenueOptions] = useState<Venue[]>([]);
    const [loadingVenues, setLoadingVenues] = useState(false);
    const [showForm, setShowForm] = useState(false);
    const [showThankYou, setShowThankYou] = useState(false);

    const [bmmPreferences, setBmmPreferences] = useState<BMMPreferences>({
        preferredVenue: '',
        preferredDates: [],
        preferredTime: '', // 改为单选
        intendToAttend: null, // Initial intention
        workplaceInfo: '',
        additionalComments: '',
        suggestedVenue: '',
        preferenceSpecialVote: null // 特殊投票意向
    });

    // Session time options - comprehensive list
    const sessionTimes = [
        { value: 'morning', label: 'Morning Session (9:00 AM - 12:00 PM)' },
        { value: 'lunchtime', label: 'Lunchtime Session (12:00 PM - 2:00 PM)' },
        { value: 'afternoon', label: 'Afternoon Session (2:00 PM - 5:00 PM)' },
        { value: 'after_work', label: 'After Work Session (5:00 PM - 8:00 PM)' },
        { value: 'night_shift', label: 'Night Shift Session (for members working night shifts)' }
    ];

    useEffect(() => {
        const urlParams = new URLSearchParams(window.location.search);
        const tokenParam = urlParams.get('token') || '';
        setToken(tokenParam);

        // 简化：这个页面始终显示偏好收集表单
        if (tokenParam) {
            fetchMemberData(tokenParam);
        } else {
            toast.error('Invalid access. Please use the link from your email.');
            router.push('/');
        }
    }, [router]);

    const fetchMemberData = async (memberToken: string) => {
        try {
            setIsLoading(true);
            // 使用正确的API端点
            const response = await api.get(`/event-registration/member/${memberToken}`);

            if (response.data.status === 'success') {
                const member = response.data.data;
                setMemberData(member);
                console.log('Member data loaded:', member);
                console.log('Region:', member.regionDesc);

                // Remove bmmStage check - rely only on URL parameters
                // This ensures dev and prod environments behave identically

                // 1.3.2 解析场地信息 - 支持多venue选择
                if (member.forumDesc && hasMultipleVenues(member.forumDesc)) {
                    // Greymouth/Whangarei用户 - 需要显示多个venue选项
                    console.log(`Forum ${member.forumDesc} has multiple venue options`);
                    try {
                        // 调用后端API获取venue选项
                        // const venueResponse = await api.get(`/api/venues/options/${member.forumDesc}`);
                        const venueResponse = await api.get(`/event-registration/venues/options/${member.forumDesc}`);

                        const venues = venueResponse.data.data || []; // API响应格式是 {status, message, data}

                        const formattedVenues = venues.map((venue: any) => ({
                            id: venue.id || Math.random(), // 生成临时ID
                            name: venue.venueName, // 后端返回的是venueName
                            venue: venue.fullName, // 后端返回的是fullName
                            address: venue.address,
                            fullAddress: `${venue.fullName}, ${venue.address}`,
                            date: venue.date,
                            capacity: venue.capacity,
                            region: venue.region || member.regionDesc
                        }));

                        setVenueOptions(formattedVenues);
                        // 设置默认选择为forum同名venue
                        setBmmPreferences(prev => ({...prev, preferredVenue: member.forumDesc}));
                    } catch (error) {
                        console.error('Failed to load venue options:', error);
                        // 回退到单venue显示
                        if (member.assignedVenue) {
                            const venue = member.assignedVenue;
                            const formattedVenue = {
                                id: venue.id,
                                name: venue.name,
                                venue: venue.venue,
                                address: venue.address,
                                fullAddress: `${venue.venue}, ${venue.address}`,
                                date: venue.date,
                                capacity: venue.capacity,
                                region: venue.region || member.regionDesc
                            };
                            setVenueOptions([formattedVenue]);
                            setBmmPreferences(prev => ({...prev, preferredVenue: venue.name}));
                        }
                    }
                } else if (member.assignedVenue) {
                    // 单venue情况
                    const venue = member.assignedVenue;
                    const formattedVenue = {
                        id: venue.id,
                        name: venue.name,
                        venue: venue.venue,
                        address: venue.address,
                        fullAddress: `${venue.venue}, ${venue.address}`,
                        date: venue.date,
                        capacity: venue.capacity,
                        region: venue.region || member.regionDesc
                    };
                    setVenueOptions([formattedVenue]);
                    // 自动设置场地为已选中
                    setBmmPreferences(prev => ({...prev, preferredVenue: venue.name}));
                } else {
                    // 处理没有 forum_desc 或找不到对应场地的情况
                    console.warn('No venue assigned for member - forum_desc may be empty');
                    // 不显示警告，允许用户继续，但不设置默认场地
                    setVenueOptions([]);
                }

                // Load existing preferences if available
                if (member.preferredVenuesJson) {
                    try {
                        const venues = JSON.parse(member.preferredVenuesJson);
                        if (venues.length > 0) {
                            setBmmPreferences(prev => ({...prev, preferredVenue: venues[0]}));
                        }
                    } catch (e) {
                        console.warn('Failed to parse preferred venues JSON');
                    }
                }

                if (member.preferredDatesJson) {
                    try {
                        const dates = JSON.parse(member.preferredDatesJson);
                        setBmmPreferences(prev => ({...prev, preferredDates: dates}));
                    } catch (e) {
                        console.warn('Failed to parse preferred dates JSON');
                    }
                }

                if (member.preferredTimesJson) {
                    try {
                        const times = JSON.parse(member.preferredTimesJson);
                        // 如果之前是多选，取第一个作为默认值
                        setBmmPreferences(prev => ({...prev, preferredTime: times.length > 0 ? times[0] : ''}));
                    } catch (e) {
                        console.warn('Failed to parse preferred times JSON');
                    }
                }

                // Load preferred attending if available
                if (member.preferredAttending !== undefined && member.preferredAttending !== null) {
                    setBmmPreferences(prev => ({...prev, intendToAttend: member.preferredAttending}));
                }

                // Load preference special vote if available
                if (member.preferenceSpecialVote !== undefined && member.preferenceSpecialVote !== null) {
                    setBmmPreferences(prev => ({...prev, preferenceSpecialVote: member.preferenceSpecialVote}));
                }

                // Load other preferences if available
                if (member.workplaceInfo) {
                    setBmmPreferences(prev => ({...prev, workplaceInfo: member.workplaceInfo}));
                }
                if (member.suggestedVenue) {
                    setBmmPreferences(prev => ({...prev, suggestedVenue: member.suggestedVenue}));
                }
                if (member.additionalComments) {
                    setBmmPreferences(prev => ({...prev, additionalComments: member.additionalComments}));
                }

                // No need to fetch venues - already provided in assignedVenue
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


    // Handle venue selection for forums with multiple options
    const handleVenueChange = (selectedVenue: string) => {
        setBmmPreferences(prev => ({
            ...prev,
            preferredVenue: selectedVenue
        }));
    };

    const handleTimeSelection = (time: string) => {
        setBmmPreferences(prev => ({
            ...prev,
            preferredTime: time // 单选直接赋值
        }));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        // 如果没有分配场地，允许继续但提醒用户
        if (!bmmPreferences.preferredVenue && venueOptions.length === 0) {
            console.warn('No venue assigned - submitting without venue preference');
        }

        // Check if they indicated their intention
        if (bmmPreferences.intendToAttend === null) {
            toast.error('Please indicate whether you intend to attend');
            return;
        }

        setIsLoading(true);
        setError('');

        try {
            const formData = {
                memberToken: token, // 后端期望的是 memberToken 字段
                preferredVenues: bmmPreferences.preferredVenue ? [bmmPreferences.preferredVenue] : [], // Empty array if no venue
                preferredDates: [], // No date selection needed - fixed date
                preferredTimes: bmmPreferences.preferredTime ? [bmmPreferences.preferredTime] : [], // 单选时间转为数组格式
                intendToAttend: bmmPreferences.intendToAttend, // Initial intention
                workplaceInfo: bmmPreferences.workplaceInfo,
                additionalComments: bmmPreferences.additionalComments,
                suggestedVenue: bmmPreferences.suggestedVenue,
                preferenceSpecialVote: bmmPreferences.preferenceSpecialVote // 新增：特殊投票意向
            };

            // Fixed: Use correct API endpoint
            const response = await api.post(`/bmm/preferences`, formData);

            if (response.data.status === 'success') {
                toast.success('Your BMM preferences have been submitted successfully!');
                // Show thank you page
                setShowThankYou(true);
            } else {
                setError(response.data.message || 'Failed to submit preferences');
                toast.error(response.data.message || 'Failed to submit preferences');
            }
        } catch (error: any) {
            console.error('Error submitting BMM preferences:', error);
            const errorMessage = error.response?.data?.message || 'Failed to submit preferences';
            setError(errorMessage);
            toast.error(errorMessage);
        } finally {
            setIsLoading(false);
        }
    };

    if (isLoading && !memberData) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12 text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-orange-500"></div>
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
                            <h1 className="text-3xl font-bold text-center text-gray-900 dark:text-white mb-2">
                                Pre-Register Your Interest in E tū Biennial
                            </h1>
                            <h2 className="text-xl font-semibold text-center text-gray-800 dark:text-gray-200 mb-8">
                                Membership Meetings in September 2025
                            </h2>

                            {showThankYou ? (
                                // 感谢页面
                                <div className="text-center py-12">
                                    <div className="bg-green-50 dark:bg-green-900/20 rounded-lg p-8 mb-6">
                                        <h2 className="text-3xl font-bold text-green-800 dark:text-green-200 mb-4">
                                            ✅ Thank You!
                                        </h2>
                                        <p className="text-lg text-green-700 dark:text-green-300 mb-4">
                                            Your BMM preferences have been submitted successfully.
                                        </p>
                                        <p className="text-green-600 dark:text-green-400">
                                            We will send you confirmation details soon.
                                        </p>
                                    </div>
                                </div>
                            ) : !showForm ? (
                                // 介绍页面
                                <div>
                                    {/* Main Introduction */}
                                    <div className="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-6 mb-6">
                                        <h3 className="text-lg font-semibold text-blue-800 dark:text-blue-200 mb-3">
                                            ❖ What Are the Biennial Membership Meetings?
                                        </h3>
                                        <div className="space-y-3 text-blue-700 dark:text-blue-300">
                                            <p>
                                                Biennial Membership Meetings (BMMs) are a vital part of E tū's democratic life
                                                and union structure. Held every two years, these meetings provide our members
                                                across Aotearoa the opportunity to connect, listen and share ideas direction.
                                                These meetings are held under section 26 of the Employment Relations Act (ERA),
                                                meaning attendance is paid time for members, subject to employer agreement as
                                                required by the law.
                                            </p>
                                            <p>
                                                They provide members across Aotearoa the opportunity to:
                                            </p>
                                            <ul className="list-disc pl-6 space-y-1">
                                                <li>Stay informed on current union issues and campaigns</li>
                                                <li>Discuss and give feedback on the union's strategic direction</li>
                                                <li>Vote on matters referred to the E tū biennial conference</li>
                                                {memberData?.regionDesc?.includes('Northern') && (
                                                    <li>Get involved and make a difference</li>
                                                )}
                                                {memberData?.regionDesc?.includes('Central') && (
                                                    <li>Elect the Central Region representative to the National Executive</li>
                                                )}
                                                {memberData?.regionDesc?.includes('Southern') && (
                                                    <li>Elect the Southern Region representative to the National Executive</li>
                                                )}
                                            </ul>
                                        </div>
                                    </div>

                                    {/* Where and When Section */}
                                    <div className="bg-green-50 dark:bg-green-900/20 rounded-lg p-6 mb-6">
                                        <h3 className="text-lg font-semibold text-green-800 dark:text-green-200 mb-3">
                                            ❖ Where and When?
                                        </h3>
                                        <div className="space-y-3 text-green-700 dark:text-green-300">
                                            <p>
                                                We're planning to hold 27 in-person meetings across the country throughout
                                                September 2025. We want to know:
                                            </p>
                                            <div className="bg-white dark:bg-gray-800 rounded p-4 space-y-2">
                                                <p className="flex items-start">
                                                    <span className="text-green-600 mr-2">✓</span>
                                                    your interest in attending
                                                </p>
                                                <p className="flex items-start">
                                                    <span className="text-green-600 mr-2">✓</span>
                                                    the most suitable venue for you
                                                </p>
                                                <p className="flex items-start">
                                                    <span className="text-green-600 mr-2">✓</span>
                                                    the best time of day for you
                                                </p>
                                            </div>
                                            <p className="text-sm italic">
                                                This information helps us organise locations, times, and logistics in a way that works
                                                for you and your fellow members.
                                            </p>
                                        </div>
                                    </div>

                                    {memberData && (
                                        <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-6 mb-6">
                                            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                                                Member Details
                                            </h3>
                                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                                <div>
                                                    <p className="text-sm text-gray-600 dark:text-gray-400">Name</p>
                                                    <p className="font-medium text-gray-900 dark:text-white">{memberData.name}</p>
                                                </div>
                                                <div>
                                                    <p className="text-sm text-gray-600 dark:text-gray-400">Region</p>
                                                    <p className="font-medium text-gray-900 dark:text-white">{memberData.regionDesc}</p>
                                                </div>
                                                <div>
                                                    <p className="text-sm text-gray-600 dark:text-gray-400">Membership Number</p>
                                                    <p className="font-medium text-gray-900 dark:text-white">{memberData.membershipNumber}</p>
                                                </div>
                                            </div>
                                        </div>
                                    )}

                                    {/* Ready to Pre-register Button */}
                                    <div className="text-center py-8">
                                        <button
                                            onClick={() => setShowForm(true)}
                                            className="bg-orange-500 hover:bg-orange-600 text-white font-bold py-4 px-12 rounded-full shadow-lg transform transition-all duration-200 hover:scale-105 text-xl"
                                        >
                                            Ready to Pre-register?
                                        </button>
                                    </div>
                                </div>
                            ) : (
                                // 表单页面
                                <div>
                                    <form onSubmit={handleSubmit} className="space-y-8">
                                        {/* Pre-Registration Form Section */}
                                        <div className="border-2 border-gray-300 dark:border-gray-600 rounded-lg p-6">
                                            <h2 className="text-xl font-bold text-center text-red-600 dark:text-red-400 mb-6">
                                                ➤ Pre-Registration Form
                                            </h2>

                                            {/* Member Details */}
                                            <div className="mb-6 bg-indigo-50 dark:bg-indigo-900/20 rounded-lg p-6">
                                                <label className="block text-lg font-semibold text-indigo-800 dark:text-indigo-200 mb-4">
                                                    <span className="mr-2">❓</span>Question: Would you like to attend a BMM?
                                                </label>
                                                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                                    <div className="bg-white dark:bg-gray-800 rounded-lg p-4">
                                                        <p className="text-sm font-medium text-indigo-600 dark:text-indigo-400 mb-1">Your current branch:</p>
                                                        <p className="text-lg font-semibold text-gray-900 dark:text-white">{memberData?.regionDesc || 'Not specified'}</p>
                                                    </div>
                                                    <div>
                                                        <p className="text-sm font-medium text-indigo-600 dark:text-indigo-400 mb-2">If you can't make one of the listed meetings below, please let us know:</p>
                                                        <textarea
                                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                                                            rows={3}
                                                            value={bmmPreferences.additionalComments}
                                                            onChange={(e) => setBmmPreferences(prev => ({...prev, additionalComments: e.target.value}))}
                                                            placeholder="Your comments..."
                                                        />
                                                    </div>
                                                </div>
                                            </div>

                                            {/* Stay Informed Options */}
                                            <div className="mb-6">
                                                <p className="font-medium text-gray-700 dark:text-gray-300 mb-3">
                                                    ❖ Stay informed on current union issues and campaigns
                                                </p>
                                                <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">
                                                    ❖ Discuss and give feedback on the union's strategic direction
                                                </p>
                                                <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">
                                                    ❖ Vote on matters referred to the E tū biennial conference
                                                </p>
                                                {memberData?.regionDesc?.includes('Northern') && (
                                                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">
                                                        ❖ Get involved and make a difference
                                                    </p>
                                                )}
                                                {memberData?.regionDesc?.includes('Central') && (
                                                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">
                                                        ❖ Elect the Central Region representative to the National Executive
                                                    </p>
                                                )}
                                                {memberData?.regionDesc?.includes('Southern') && (
                                                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">
                                                        ❖ Elect the Southern Region representative to the National Executive
                                                    </p>
                                                )}
                                            </div>

                                            {/* These meetings are a powerful opportunity section */}
                                            <div className="bg-yellow-50 dark:bg-yellow-900/20 p-4 rounded-lg mb-6">
                                                <p className="text-sm">
                                                    <strong>If needed, and where there is sufficient interest from members working in the same area
                                                        or workplace, we will also try to arrange group transport to the meetings to support participation.</strong>
                                                </p>
                                                <p className="text-sm mt-2">
                                                    These meetings are a powerful opportunity to connect, shape your union's direction,
                                                    and have your voice heard.
                                                </p>
                                            </div>

                                            {/* Attendance Intention */}
                                            <div className="mb-6 bg-orange-50 dark:bg-orange-900/20 rounded-lg p-6">
                                                <label className="block text-lg font-semibold text-orange-800 dark:text-orange-200 mb-4">
                                                    <span className="mr-2">🗳️</span>Would you like to attend a BMM?
                                                </label>
                                                <div className="space-y-3">
                                                    <label className="flex items-center p-3 rounded-lg border-2 border-gray-200 dark:border-gray-600 hover:border-orange-400 dark:hover:border-orange-400 cursor-pointer transition-all">
                                                        <input
                                                            type="radio"
                                                            name="attendance"
                                                            checked={bmmPreferences.intendToAttend === true}
                                                            onChange={() => setBmmPreferences(prev => ({...prev, intendToAttend: true}))}
                                                            className="w-5 h-5 text-orange-600 focus:ring-orange-500"
                                                        />
                                                        <span className="ml-3 text-gray-700 dark:text-gray-300 font-medium">Yes</span>
                                                    </label>
                                                    <label className="flex items-center p-3 rounded-lg border-2 border-gray-200 dark:border-gray-600 hover:border-orange-400 dark:hover:border-orange-400 cursor-pointer transition-all">
                                                        <input
                                                            type="radio"
                                                            name="attendance"
                                                            checked={bmmPreferences.intendToAttend === false}
                                                            onChange={() => setBmmPreferences(prev => ({...prev, intendToAttend: false}))}
                                                            className="w-5 h-5 text-orange-600 focus:ring-orange-500"
                                                        />
                                                        <span className="ml-3 text-gray-700 dark:text-gray-300 font-medium">No</span>
                                                    </label>
                                                </div>
                                            </div>

                                            {/* Special Vote Question - Only for Southern Region */}
                                            {memberData && (memberData.regionDesc === 'Southern' || memberData.regionDesc === 'Southern Region') && (
                                                <div className="mb-6 bg-amber-50 dark:bg-amber-900/20 rounded-lg p-6">
                                                    <label className="block text-lg font-semibold text-amber-800 dark:text-amber-200 mb-4">
                                                        <span className="mr-2">📮</span>Do you believe you qualify for a special vote?
                                                    </label>
                                                    <div className="space-y-3">
                                                        <label className="flex items-center p-3 rounded-lg border-2 border-gray-200 dark:border-gray-600 hover:border-amber-400 dark:hover:border-amber-400 cursor-pointer transition-all">
                                                            <input
                                                                type="radio"
                                                                name="specialVote"
                                                                checked={bmmPreferences.preferenceSpecialVote === true}
                                                                onChange={() => setBmmPreferences(prev => ({...prev, preferenceSpecialVote: true}))}
                                                                className="w-5 h-5 text-amber-600 focus:ring-amber-500"
                                                            />
                                                            <span className="ml-3 text-gray-700 dark:text-gray-300 font-medium">Yes</span>
                                                        </label>
                                                        <label className="flex items-center p-3 rounded-lg border-2 border-gray-200 dark:border-gray-600 hover:border-amber-400 dark:hover:border-amber-400 cursor-pointer transition-all">
                                                            <input
                                                                type="radio"
                                                                name="specialVote"
                                                                checked={bmmPreferences.preferenceSpecialVote === false}
                                                                onChange={() => setBmmPreferences(prev => ({...prev, preferenceSpecialVote: false}))}
                                                                className="w-5 h-5 text-amber-600 focus:ring-amber-500"
                                                            />
                                                            <span className="ml-3 text-gray-700 dark:text-gray-300 font-medium">No</span>
                                                        </label>
                                                        <label className="flex items-center p-3 rounded-lg border-2 border-gray-200 dark:border-gray-600 hover:border-amber-400 dark:hover:border-amber-400 cursor-pointer transition-all">
                                                            <input
                                                                type="radio"
                                                                name="specialVote"
                                                                checked={bmmPreferences.preferenceSpecialVote === null}
                                                                onChange={() => setBmmPreferences(prev => ({...prev, preferenceSpecialVote: null}))}
                                                                className="w-5 h-5 text-amber-600 focus:ring-amber-500"
                                                            />
                                                            <span className="ml-3 text-gray-700 dark:text-gray-300 font-medium">Not sure</span>
                                                        </label>
                                                    </div>
                                                    <div className="mt-6 bg-amber-100 dark:bg-amber-800/20 rounded-lg p-4">
                                                        <p className="text-sm font-semibold text-amber-900 dark:text-amber-200 mb-3">
                                                            To qualify for a special vote, one of the following must apply to you:
                                                        </p>
                                                        <ul className="text-sm text-amber-800 dark:text-amber-300 space-y-2 ml-4">
                                                            <li className="flex items-start">
                                                                <span className="mr-2">•</span>
                                                                <span>You have a disability that prevents you from fully participating in the meeting</span>
                                                            </li>
                                                            <li className="flex items-start">
                                                                <span className="mr-2">•</span>
                                                                <span>You are ill or infirm, making attendance impossible</span>
                                                            </li>
                                                            <li className="flex items-start">
                                                                <span className="mr-2">•</span>
                                                                <span>You live more than 32km from the meeting venue</span>
                                                            </li>
                                                            <li className="flex items-start">
                                                                <span className="mr-2">•</span>
                                                                <span>Your employer requires you to work during the time of the meeting</span>
                                                            </li>
                                                            <li className="flex items-start">
                                                                <span className="mr-2">•</span>
                                                                <span>Attending the meeting would cause you serious hardship or major inconvenience</span>
                                                            </li>
                                                        </ul>
                                                        <p className="text-sm text-amber-700 dark:text-amber-400 mt-3 font-medium">
                                                            📅 Special vote applications must be made at least 14 days before the start of the BMM at which the secret ballot is to be held.
                                                        </p>
                                                    </div>
                                                </div>
                                            )}
                                        </div>

                                        {/* 2. Your BMM Venue */}
                                        <div className="mb-8">
                                            <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-4 flex items-center">
                                                <span className="bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200 w-8 h-8 rounded-full flex items-center justify-center mr-3 text-sm font-bold">2</span>
                                                {memberData?.forumDesc && hasMultipleVenues(memberData.forumDesc) ?
                                                    'Select Your Preferred BMM Venue' :
                                                    'Your Assigned BMM Venue'
                                                }
                                            </h3>
                                            <p className="text-base text-gray-600 dark:text-gray-400 mb-4">
                                                {memberData?.forumDesc && hasMultipleVenues(memberData.forumDesc) ?
                                                    '📍 Based on your location, you can choose from the following venue options:' :
                                                    '📍 Based on your location, you have been assigned to the following venue:'
                                                }
                                            </p>

                                            {venueOptions.length > 0 ? (
                                                memberData?.forumDesc && hasMultipleVenues(memberData.forumDesc) ? (
                                                    // Multiple venue selection for Greymouth/Whangarei
                                                    <div className="border-2 border-blue-500 bg-blue-50 dark:bg-blue-900/20 rounded-lg p-6">
                                                        <div className="space-y-4">
                                                            <div className="font-semibold text-lg text-gray-900 dark:text-white mb-3">
                                                                Choose your preferred venue:
                                                            </div>

                                                            <ForumVenueSelector
                                                                forumDesc={memberData.forumDesc}
                                                                selectedVenue={bmmPreferences.preferredVenue}
                                                                onVenueChange={handleVenueChange}
                                                                className="w-full p-3 text-lg font-medium bg-white dark:bg-gray-800 border-2 border-gray-300 dark:border-gray-600 rounded-lg"
                                                            />

                                                            {/* Show details of selected venue */}
                                                            {bmmPreferences.preferredVenue && venueOptions.find(v => v.name === bmmPreferences.preferredVenue) && (
                                                                <div className="mt-4 p-4 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700">
                                                                    <div className="text-sm text-gray-600 dark:text-gray-400 space-y-1">
                                                                        <div className="flex items-start">
                                                                            <span className="mr-2">🏢</span>
                                                                            <span className="font-medium">{venueOptions.find(v => v.name === bmmPreferences.preferredVenue)?.venue}</span>
                                                                        </div>
                                                                        <div className="flex items-start">
                                                                            <span className="mr-2">📍</span>
                                                                            <span>{venueOptions.find(v => v.name === bmmPreferences.preferredVenue)?.address}</span>
                                                                        </div>
                                                                        <div className="flex items-center">
                                                                            <span className="mr-2">📅</span>
                                                                            <span className="font-medium">{venueOptions.find(v => v.name === bmmPreferences.preferredVenue)?.date}</span>
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            )}
                                                        </div>
                                                    </div>
                                                ) : (
                                                    // Single venue display for other regions
                                                    <div className="border-2 border-green-500 bg-green-50 dark:bg-green-900/20 rounded-lg p-6">
                                                        <div className="space-y-2">
                                                            <div className="font-semibold text-lg text-gray-900 dark:text-white">
                                                                {venueOptions[0].name}
                                                            </div>
                                                            <div className="text-sm text-gray-600 dark:text-gray-400 space-y-1">
                                                                <div className="flex items-start">
                                                                    <span className="mr-2">🏢</span>
                                                                    <span className="font-medium">{venueOptions[0].venue}</span>
                                                                </div>
                                                                <div className="flex items-start">
                                                                    <span className="mr-2">📍</span>
                                                                    <span>{venueOptions[0].address}</span>
                                                                </div>
                                                                <div className="flex items-center">
                                                                    <span className="mr-2">📅</span>
                                                                    <span className="font-medium">{venueOptions[0].date}</span>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                )
                                            ) : (
                                                <div className="border-2 border-amber-500 bg-amber-50 dark:bg-amber-900/20 rounded-lg p-6">
                                                    <div className="text-amber-700 dark:text-amber-300">
                                                        <p className="font-medium">⚠️ No venue has been automatically assigned to your account.</p>
                                                        <p className="text-sm mt-2">Please continue to submit your time preferences and other information.</p>
                                                    </div>
                                                </div>
                                            )}
                                        </div>

                                        {/* 3. Preferred Session Time */}
                                        <div className="mb-8">
                                            <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-4 flex items-center">
                                                <span className="bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200 w-8 h-8 rounded-full flex items-center justify-center mr-3 text-sm font-bold">3</span>
                                                Select Your Preferred Session Time
                                            </h3>
                                            <p className="text-base text-gray-600 dark:text-gray-400 mb-4">
                                                Please select which session time works best for you.
                                            </p>

                                            <div className="space-y-3">
                                                {sessionTimes.map((time) => (
                                                    <label key={time.value} className="flex items-center space-x-3 cursor-pointer p-4 border border-gray-200 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700">
                                                        <input
                                                            type="radio"
                                                            name="sessionTime"
                                                            value={time.value}
                                                            checked={bmmPreferences.preferredTime === time.value}
                                                            onChange={() => handleTimeSelection(time.value)}
                                                            className="w-5 h-5 text-blue-600 focus:ring-blue-500"
                                                        />
                                                        <span className="text-gray-700 dark:text-gray-300 font-medium">{time.label}</span>
                                                    </label>
                                                ))}
                                            </div>
                                        </div>

                                        {/* 4. Alternative Arrangements */}
                                        <div className="mb-8">
                                            <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-4 flex items-center">
                                                <span className="bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200 w-8 h-8 rounded-full flex items-center justify-center mr-3 text-sm font-bold">4</span>
                                                If you cannot attend at the assigned venue, please let us know:
                                            </h3>
                                            <label className="block text-base font-medium text-gray-700 dark:text-gray-300 mb-2">
                                                Enter suggested location:
                                            </label>
                                            <input
                                                type="text"
                                                value={bmmPreferences.suggestedVenue}
                                                onChange={(e) => setBmmPreferences(prev => ({...prev, suggestedVenue: e.target.value}))}
                                                className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                placeholder="Suggest an alternative venue location"
                                            />
                                        </div>

                                        {/* 5. Workplace Information */}
                                        <div className="mb-8">
                                            <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-4 flex items-center">
                                                <span className="bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200 w-8 h-8 rounded-full flex items-center justify-center mr-3 text-sm font-bold">5</span>
                                                Your workplace information
                                            </h3>
                                            <p className="text-base text-gray-600 dark:text-gray-400 mb-4">
                                                Please tell us your workplace name and location (optional):
                                            </p>

                                            <input
                                                type="text"
                                                value={bmmPreferences.workplaceInfo}
                                                onChange={(e) => setBmmPreferences(prev => ({...prev, workplaceInfo: e.target.value}))}
                                                className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                placeholder="Enter workplace name and location"
                                            />
                                        </div>

                                        {/* 6. Additional Comments */}
                                        <div className="mb-8">
                                            <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-4 flex items-center">
                                                <span className="bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200 w-8 h-8 rounded-full flex items-center justify-center mr-3 text-sm font-bold">6</span>
                                                Additional comments or special requirements
                                            </h3>
                                            <textarea
                                                value={bmmPreferences.additionalComments}
                                                onChange={(e) => setBmmPreferences(prev => ({...prev, additionalComments: e.target.value}))}
                                                className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded-lg px-4 py-3 focus:outline-none focus:ring-2 focus:ring-blue-500"
                                                rows={3}
                                                placeholder="Any additional comments, special requirements, or concerns..."
                                            />
                                        </div>

                                        {error && (
                                            <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-700 rounded-lg p-4">
                                                <p className="text-red-800 dark:text-red-300">{error}</p>
                                            </div>
                                        )}

                                        {/* Submit Button */}
                                        <div className="text-center pt-6">
                                            <button
                                                type="submit"
                                                disabled={isLoading}
                                                className="bg-orange-500 hover:bg-orange-600 text-white font-bold py-4 px-12 rounded-full shadow-lg transform transition-all duration-200 hover:scale-105 disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none"
                                            >
                                                {isLoading ? (
                                                    <span className="flex items-center">
                                                <svg className="animate-spin h-5 w-5 mr-3" viewBox="0 0 24 24">
                                                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none"></circle>
                                                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                                </svg>
                                                Submitting...
                                            </span>
                                                ) : (
                                                    <span className="flex items-center justify-center">
                                                <span className="mr-2">📤</span>
                                                Submit Your Preferences
                                            </span>
                                                )}
                                            </button>
                                        </div>
                                    </form>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </Layout>
    );
}