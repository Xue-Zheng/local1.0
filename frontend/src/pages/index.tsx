'use client';
import { useState, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/router';
import Layout from '@/components/common/Layout';
import { getMemberByToken, getUpcomingEvents, Event } from '@/services/registrationService';
import SpecialVotingTooltip from "@/components/SpecialVotingTooltip";
import api from '@/services/api';

export default function HomePage() {
    const router = useRouter();
    const token = router.query.token as string || '';
    const eventId = router.query.event as string || '';

    const [currentEventMember, setCurrentEventMember] = useState<any>(null);
    const [upcomingEvents, setUpcomingEvents] = useState<Event[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [memberChecked, setMemberChecked] = useState(false);
    const [showInviteAlert, setShowInviteAlert] = useState(false);


    useEffect(() => {
        const fetchData = async () => {
            setIsLoading(true);
            try {
                const eventsResponse = await getUpcomingEvents();
                if (eventsResponse.status === 'success') {
                    setUpcomingEvents(eventsResponse.data.slice(0, 3));
                }

                if (token && router.isReady) {
                    // 优先使用EventMember API (支持BMM等现代事件类型)
                    try {
                        const eventMemberResponse = await api.get(`/event-registration/member/${token}`);
                        if (eventMemberResponse.data.status === 'success') {
                            const memberData = eventMemberResponse.data.data;
                            setCurrentEventMember({
                                ...memberData,
                                eventType: memberData.event?.type || 'UNKNOWN',
                                eventId: memberData.event?.id,
                                eventName: memberData.event?.name
                            });

                            // 特别处理BMM事件的页面显示逻辑
                            if (memberData.event?.type === 'BMM_VOTING') {
                                document.title = 'BMM Voting Registration - E tū Union';
                                // 可以添加特殊的页面元数据或样式
                            }
                        }
                    } catch (eventMemberError) {
                        // 如果EventMember API失败，回退到传统Member API
                        console.log('EventMember API failed, falling back to Member API');
                        const memberResponse = await getMemberByToken(token);
                        if (memberResponse.status === 'success') {
                            const memberData = memberResponse.data;
                            setCurrentEventMember(memberData);

                            // 特别处理BMM事件的页面显示逻辑
                            if (memberData.eventType === 'BMM_VOTING') {
                                document.title = 'BMM Voting Registration - E tū Union';
                                // 可以添加特殊的页面元数据或样式
                            }
                        }
                    }
                }
            } catch (error) {
                console.error('Error fetching data:', error);
            } finally {
                setIsLoading(false);
                setMemberChecked(true);
            }
        };

        if (router.isReady) {
            fetchData();
        }
    }, [token, router.isReady]);

    if (isLoading) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-16 text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-orange-500"></div>
                    <p className="text-lg mt-4 text-gray-600 dark:text-gray-400">Loading...</p>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="container mx-auto px-4 py-16">

                {memberChecked && currentEventMember && (currentEventMember.eventType === 'SPECIAL_CONFERENCE' || currentEventMember.eventType === 'BMM_VOTING' || currentEventMember.eventType === 'SURVEY_MEETING') ? (
                    <>
                        {/* BMM Voting Event Display */}
                        {currentEventMember.eventType === 'BMM_VOTING' && (
                            <>
                                <section className="text-center mb-16 animate-fade-in">
                                    <h1 className="text-4xl md:text-5xl font-bold text-purple-600 dark:text-purple-400 mb-6">
                                        🗳️ E tū Biennial Membership Meetings (BMMs)
                                    </h1>
                                    <p className="text-lg md:text-xl text-gray-600 dark:text-gray-400 max-w-4xl mx-auto mb-10">
                                        Welcome to the registration portal for E tū's Biennial Membership Meetings. As a valued member, your participation is essential in shaping the future of our union, empowering working people and our communities for a better life.                                  </p>
                                    <div className="flex justify-center">
                                        <Link
                                            href={`/register?token=${token}&event=${currentEventMember.eventId}`}
                                            className="bg-orange-600 hover:bg-orange-700 text-white font-bold text-2xl py-4 px-10 rounded-lg shadow-lg transition-all animate-bounce"
                                        >
                                            Confirm your attendance
                                        </Link>
                                    </div>
                                </section>

                                <section className="mb-16 bg-gradient-to-r from-blue-50 to-purple-50 dark:from-blue-900/20 dark:to-purple-900/20 rounded-xl p-8 shadow-md">
                                    <h2 className="text-2xl font-bold text-purple-900 dark:text-purple-300 mb-6 text-center">
                                        {currentEventMember.eventName}
                                    </h2>

                                    <div className="grid md:grid-cols-2 gap-8 items-center">
                                        <div>
                                            <h3 className="text-xl font-bold text-gray-900 dark:text-white mb-4">At this Special Conference you will:</h3>
                                            <ul className="space-y-3 text-gray-700 dark:text-gray-300">
                                                <li className="flex items-start">
                                                    <span className="text-purple-600 mr-3">•</span>
                                                    <span>Vote on important policy matters that affect all members.</span>
                                                </li>
                                                <li className="flex items-start">
                                                    <span className="text-purple-600 mr-3">•</span>
                                                    <span>Participate in discussions about our union's future.</span>
                                                </li>
                                                <li className="flex items-start">
                                                    <span className="text-purple-600 mr-3">•</span>
                                                    <span>Represent the views and concerns of members in your workplace.</span>
                                                </li>
                                                <li className="flex items-start">
                                                    <span className="text-purple-600 mr-3">•</span>
                                                    <span>Connect with other delegates from across New Zealand.</span>
                                                </li>
                                            </ul>
                                        </div>
                                        <div className="rounded-lg overflow-hidden shadow-lg">
                                            <div className="bg-orange-600 text-white p-6 text-center">
                                                <h3 className="text-xl font-bold mb-4">Important Details</h3>
                                                <div className="space-y-2">
                                                    <p className="font-medium">Date: September 2025</p>
                                                    <p className="font-medium">Time: To be confirmed</p>
                                                    <p className="font-medium">Locations: Central, Northern, Southern Regions</p>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </section>

                                <section className="mb-16">
                                    <div className="grid md:grid-cols-2 gap-8">
                                        <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
                                            <h3 className="text-xl font-bold text-purple-600 dark:text-purple-400 mb-4">
                                                Key Issues
                                            </h3>
                                            <div className="space-y-3 text-gray-700 dark:text-gray-300">
                                                <p>• Constitutional amendments and rule changes</p>
                                                <p>• Election of national leadership positions</p>
                                                <p>• Strategic direction for the next two years</p>
                                                <p>• Key policy proposals affecting all members</p>
                                            </div>
                                        </div>
                                        <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md">
                                            <h3 className="text-xl font-bold text-purple-600 dark:text-purple-400 mb-4">
                                                Stay Connected
                                            </h3>
                                            <div className="space-y-3 text-gray-700 dark:text-gray-300">
                                                <p>• Check your email for BMM updates and venue details</p>
                                                <p>• Communicate with Union Support or your organiser when needed</p>
                                                <p>• Access BMM documents and voting information</p>
                                                <p>• Update your membership details and communication preferences</p>
                                                <p>• Contact us: 0800 1 UNION (0800 186 466)</p>
                                            </div>
                                        </div>
                                    </div>
                                </section>

                                <section className="text-center">
                                    <p className="text-gray-600 dark:text-gray-400 mb-4">
                                        Please complete your registration process to confirm your attendance and secure your voting rights.
                                    </p>
                                </section>
                            </>
                        )}

                        {/* Special Conference Display - Keep existing */}
                        {currentEventMember.eventType === 'SPECIAL_CONFERENCE' && (
                            <>
                                <section className="text-center mb-16 animate-fade-in">
                                    <h1 className="text-4xl md:text-5xl font-bold text-black dark:text-white mb-6">
                                        E tū Special Conference
                                    </h1>
                                    <p className="text-lg md:text-xl text-gray-600 dark:text-gray-400 max-w-3xl mx-auto mb-10">
                                        Welcome to the registration portal for E tū Special Conference. As a valued conference delegate, your participation is essential in shaping the future of our union.
                                    </p>
                                </section>

                                <section className="mb-16 bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 rounded-xl p-8 shadow-md">
                                    <h2 className="text-2xl font-bold text-purple-900 dark:text-purple-300 mb-6 text-center">
                                        {currentEventMember.eventName}
                                    </h2>
                                    <div className="grid md:grid-cols-2 gap-8 items-center">
                                        <div>
                                            <p className="text-gray-700 dark:text-gray-300 mb-4">
                                                Your role as a conference delegate is crucial to E tū's democratic structure. At this special conference you will participate in important decision-making processes for our union.
                                            </p>
                                            <p className="text-gray-700 dark:text-gray-300">
                                                Please complete your registration process to confirm your attendance and ensure your voice is heard.
                                            </p>
                                        </div>
                                        <div className="rounded-lg overflow-hidden shadow-lg">
                                            <div className="bg-orange-600 text-white p-6 text-center">
                                                <h3 className="text-xl font-bold mb-2">Event Details</h3>
                                                <p className="font-medium">Event: {currentEventMember.eventName}</p>
                                                <p className="font-medium">Type: Special Conference</p>
                                                {currentEventMember.venue && (
                                                    <p className="font-medium">Venue: {currentEventMember.venue}</p>
                                                )}
                                            </div>
                                        </div>
                                    </div>
                                </section>
                            </>
                        )}

                        {/* Survey Meeting Display - Keep existing */}
                        {currentEventMember.eventType === 'SURVEY_MEETING' && (
                            <>
                                <section className="text-center mb-16 animate-fade-in">
                                    <h1 className="text-4xl md:text-5xl font-bold text-green-600 dark:text-green-400 mb-6">
                                        📊 E tū Survey Meeting
                                    </h1>
                                    <p className="text-lg md:text-xl text-gray-600 dark:text-gray-400 max-w-3xl mx-auto mb-10">
                                        Welcome to the survey meeting portal. Your feedback and participation in this survey will help us better understand member needs and improve our services.
                                    </p>
                                </section>
                            </>
                        )}

                        <div className="text-center">
                            <Link
                                href={`/register?token=${token}&event=${currentEventMember.eventId}`}
                                className="bg-purple-600 hover:bg-purple-700 text-white font-bold text-xl py-3 px-8 rounded-lg shadow-lg transition-all"
                            >
                                Begin Registration Process
                            </Link>
                        </div>

                    </>
                ) : memberChecked && currentEventMember && currentEventMember.eventType === 'BMM_VOTING' ? (
                    <>
                        <section className="text-center mb-16 animate-fade-in">
                            <h1 className="text-4xl md:text-5xl font-bold text-purple-900 dark:text-purple-300 mb-6">
                                🗳️ E tū Biennial Membership Meetings (BMMs)
                            </h1>
                            <p className="text-lg md:text-xl text-gray-600 dark:text-gray-400 max-w-3xl mx-auto mb-10">
                                Welcome to the BMM voting portal. Your participation in this democratic process is crucial for E tū's future direction. Vote on important union matters across all three regions.
                            </p>
                            <div className="flex justify-center">
                                <Link
                                    href={`/register?token=${token}&event=${currentEventMember.eventId}`}
                                    className="bg-purple-600 hover:bg-purple-700 text-white font-bold text-2xl py-4 px-10 rounded-lg shadow-lg transition-all animate-bounce"
                                >
                                    🎯 Confirm Attendance & Voting
                                </Link>
                            </div>
                        </section>

                        <section className="mb-16 bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 rounded-xl p-8 shadow-md">
                            <h2 className="text-2xl font-bold text-purple-900 dark:text-purple-300 mb-6 text-center">
                                {currentEventMember.eventName}
                            </h2>
                            <div className="grid md:grid-cols-2 gap-8 items-center">
                                <div>
                                    <p className="text-gray-700 dark:text-gray-300 mb-4">
                                        <strong>🏛️ Democratic Participation:</strong> As an E tū member, your vote shapes our union's policies, leadership, and future direction.
                                    </p>
                                    <p className="text-gray-700 dark:text-gray-300 mb-4">
                                        <strong>🌏 Multi-Regional Voting:</strong> This BMM covers all three regions - Northern, Central, and Southern.
                                    </p>
                                    <p className="text-gray-700 dark:text-gray-300">
                                        Please confirm your attendance and complete the voting process to ensure your voice is counted.
                                    </p>
                                </div>
                                <div className="rounded-lg overflow-hidden shadow-lg">
                                    <div className="bg-gradient-to-r from-purple-600 to-blue-600 text-white p-6 text-center">
                                        <h3 className="text-xl font-bold mb-2">🗳️ BMM Voting Details</h3>
                                        <p className="font-medium">Event: {currentEventMember.eventName}</p>
                                        <p className="font-medium">Type: BMM Voting Meeting</p>
                                        <p className="font-medium">Coverage: 3 Regions (Northern, Central, Southern)</p>
                                        {currentEventMember.venue && (
                                            <p className="font-medium">📍 Venues: {currentEventMember.venue}</p>
                                        )}
                                    </div>
                                </div>
                            </div>
                        </section>

                        <section className="mb-16">
                            <h3 className="text-2xl font-bold text-center text-gray-900 dark:text-white mb-8">
                                🎯 BMM Voting Process
                            </h3>
                            <div className="grid md:grid-cols-3 gap-6">
                                <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md text-center">
                                    <div className="text-3xl mb-4">✅</div>
                                    <h4 className="font-bold text-lg mb-2">Step 1: Confirm Attendance</h4>
                                    <p className="text-gray-600 dark:text-gray-400">
                                        Confirm whether you'll attend in person at your regional venue
                                    </p>
                                </div>
                                <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md text-center">
                                    <div className="text-3xl mb-4">🗳️</div>
                                    <h4 className="font-bold text-lg mb-2">Step 2: Special Voting Option</h4>
                                    <p className="text-gray-600 dark:text-gray-400">
                                        If unable to attend, request special voting rights (if eligible)
                                    </p>
                                </div>
                                <div className="bg-white dark:bg-gray-800 p-6 rounded-lg shadow-md text-center">
                                    <div className="text-3xl mb-4">🎉</div>
                                    <h4 className="font-bold text-lg mb-2">Step 3: Participate</h4>
                                    <p className="text-gray-600 dark:text-gray-400">
                                        Attend your regional venue or vote via special voting process
                                    </p>
                                </div>
                            </div>
                        </section>
                    </>
                ) : (
                    <>
                        {/* BMM Hero Banner */}
                        <section className="relative mb-16 -mx-4 px-4">
                            <div className="bg-gradient-to-r from-orange-500 to-orange-600 rounded-xl shadow-2xl overflow-hidden">
                                <div className="relative z-10 text-center py-16 px-8">
                                    <h1 className="text-4xl md:text-6xl font-bold text-white mb-6 animate-fade-in">
                                        🏛️ E tū Biennial Membership Meetings
                                    </h1>
                                    <p className="text-xl md:text-2xl text-orange-100 max-w-4xl mx-auto mb-4">
                                        September 2025 • Northern, Central & Southern Regions
                                    </p>
                                    <p className="text-lg md:text-xl text-white max-w-3xl mx-auto mb-10">
                                        Your voice matters. Join thousands of E tū members in shaping our union's future through democratic participation.
                                    </p>
                                    <div className="animate-bounce">
                                        <p className="text-2xl font-bold text-white">
                                            Pre-registration closes: 1 August 2025
                                        </p>
                                    </div>
                                </div>
                                <div className="absolute inset-0 bg-black opacity-10"></div>
                            </div>
                        </section>

                        <section className="text-center mb-20">
                            <p className="text-lg md:text-xl text-gray-600 dark:text-gray-400 max-w-3xl mx-auto">
                                "Empowering working people and our communities for a better life."
                            </p>

                            {/*{token ? (*/}
                            {/*    <div className="flex justify-center">*/}
                            {/*        <Link*/}
                            {/*            href={`/register?token=${token}${eventId ? `&event=${eventId}` : ''}`}*/}
                            {/*            className="bg-orange-500 hover:bg-orange-600 text-white font-bold text-2xl py-4 px-10 rounded-lg shadow-lg transition-all animate-bounce"*/}
                            {/*        >*/}
                            {/*            Continue Registration*/}
                            {/*        </Link>*/}
                            {/*    </div>*/}
                            {/*) : (*/}
                            {/*    <div className="flex justify-center">*/}
                            {/*        <Link*/}
                            {/*            href="/register"*/}
                            {/*            className="bg-orange-500 hover:bg-orange-600 text-white font-bold text-2xl py-4 px-10 rounded-lg shadow-lg transition-all animate-bounce"*/}
                            {/*        >*/}
                            {/*            Register for Event*/}
                            {/*        </Link>*/}
                            {/*    </div>*/}
                            {/*)}*/}
                            {token ? (
                                <div className="flex justify-center">
                                    <Link
                                        href={`/register?token=${token}${eventId ? `&event=${eventId}` : ''}`}
                                        className="bg-orange-500 hover:bg-orange-600 text-white font-bold text-2xl py-4 px-10 rounded-lg shadow-lg transition-all animate-bounce"
                                    >
                                        Continue Registration
                                    </Link>
                                </div>
                            ) : (
                                <>
                                    <div className="flex justify-center">
                                        <button
                                            onClick={() => setShowInviteAlert(true)}
                                            className="bg-orange-500 hover:bg-orange-600 text-white font-bold text-2xl py-4 px-10 rounded-lg shadow-lg transition-all animate-bounce"
                                        >
                                            Register for Event
                                        </button>
                                    </div>

                                    {showInviteAlert && (
                                        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 px-4">
                                            <div className="bg-white rounded-lg overflow-hidden max-w-md w-full shadow-xl">
                                                <div className="bg-orange-500 p-4">
                                                    <h3 className="text-xl font-bold text-white">Registration by Invitation Only</h3>
                                                </div>
                                                <div className="p-6">
                                                    <div className="flex items-start space-x-3 mb-6">
                                                        <svg className="w-6 h-6 text-orange-500 flex-shrink-0 mt-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                                                        </svg>
                                                        <p className="text-gray-700">
                                                            Event registration requires an invitation. Please use the registration link from your invitation email or SMS.
                                                        </p>
                                                    </div>
                                                    <button
                                                        onClick={() => setShowInviteAlert(false)}
                                                        className="w-full bg-orange-500 hover:bg-orange-600 text-white font-bold py-3 px-4 rounded-lg transition-colors"
                                                    >
                                                        OK, I understand
                                                    </button>
                                                </div>
                                            </div>
                                        </div>
                                    )}
                                </>
                            )}

                        </section>

                        {/* BMM Information Section */}
                        <section className="mb-16">
                            <div className="bg-white dark:bg-gray-800 rounded-xl shadow-lg p-8">
                                <h2 className="text-3xl font-bold text-orange-600 dark:text-orange-400 mb-6 text-center">
                                    What are the Biennial Membership Meetings?
                                </h2>
                                <div className="grid md:grid-cols-2 gap-8">
                                    <div>
                                        <h3 className="text-xl font-semibold mb-4 text-gray-900 dark:text-white">
                                            🗳️ Democratic Participation
                                        </h3>
                                        <p className="text-gray-600 dark:text-gray-400 mb-4">
                                            The BMM is E tū's highest decision-making body, held every two years.
                                            As a member, you have the right to participate in shaping our union's
                                            policies, electing leadership, and setting our strategic direction.
                                        </p>
                                    </div>
                                    <div>
                                        <h3 className="text-xl font-semibold mb-4 text-gray-900 dark:text-white">
                                            🌏 Regional Meetings
                                        </h3>
                                        <p className="text-gray-600 dark:text-gray-400 mb-4">
                                            To ensure maximum participation, BMMs are held across three regions:
                                            Northern, Central, and Southern. Members attend their regional meeting
                                            to vote on matters affecting all E tū members nationwide.
                                        </p>
                                    </div>
                                </div>
                            </div>
                        </section>

                        {upcomingEvents.length > 0 && (
                            <section className="mb-16">
                                <h2 className="text-3xl font-bold text-orange-600 dark:text-orange-400 mb-8 text-center">
                                    📅 Upcoming BMM Events
                                </h2>
                                <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
                                    {upcomingEvents.map((event) => (
                                        <div
                                            key={event.id}
                                            className="bg-white dark:bg-gray-800 rounded-xl p-6 shadow hover:shadow-lg transition-all h-full flex flex-col"
                                        >
                                            <div className="flex items-start justify-between mb-4">
                                                <h3 className="text-xl font-bold text-black dark:text-white line-clamp-2">
                                                    {event.name}
                                                </h3>
                                                <span className={`px-2 py-1 text-xs rounded-full flex-shrink-0 ml-2 ${
                                                    event.registrationOpen
                                                        ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
                                                        : 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300'
                                                }`}>
                                                    {event.registrationOpen ? 'Open' : 'Closed'}
                                                </span>
                                            </div>

                                            <div className="flex-grow">
                                                {event.description && (
                                                    <p className="text-gray-600 dark:text-gray-400 mb-4 text-sm line-clamp-3">
                                                        {event.description}
                                                    </p>
                                                )}

                                                <div className="space-y-2 text-sm">
                                                    <div className="flex justify-between">
                                                        <span className="text-gray-500 dark:text-gray-400">Type:</span>
                                                        <span className="font-medium">{event.eventType.replace('_', ' ')}</span>
                                                    </div>
                                                    {event.venue && (
                                                        <div className="flex justify-between">
                                                            <span className="text-gray-500 dark:text-gray-400">Venue:</span>
                                                            <span className="font-medium">{event.venue}</span>
                                                        </div>
                                                    )}
                                                    <div className="flex justify-between">
                                                        <span className="text-gray-500 dark:text-gray-400">Members:</span>
                                                        <span className="font-medium">{event.totalMembers}</span>
                                                    </div>
                                                    <div className="flex justify-between">
                                                        <span className="text-gray-500 dark:text-gray-400">Registered:</span>
                                                        <span className="font-medium text-green-600 dark:text-green-400">
                                                            {event.registeredMembers}
                                                        </span>
                                                    </div>
                                                </div>
                                            </div>

                                            {event.registrationOpen && (
                                                <div className="mt-4 pt-4">
                                                    <Link
                                                        href={`/register?event=${event.id}`}
                                                        className="block w-full bg-orange-500 hover:bg-orange-600 text-white text-center py-2 px-4 rounded transition-colors"
                                                    >
                                                        Registration by Invitation Only
                                                    </Link>
                                                </div>
                                            )}
                                        </div>
                                    ))}
                                </div>
                            </section>
                        )}

                        <section className="grid gap-10 md:grid-cols-3 mb-24">
                            {steps.map((step, index) => (
                                <div
                                    key={index}
                                    className="bg-white dark:bg-gray-800 rounded-xl p-8 shadow hover:shadow-lg transition-all transform hover:scale-105 text-center animate-fade-in-up"
                                >
                                    <div className="w-16 h-16 mx-auto mb-4 rounded-full flex items-center justify-center" style={{ backgroundColor: step.color }}>
                                        {step.icon}
                                    </div>
                                    <h3 className="text-xl font-bold text-black dark:text-white mb-2">{step.title}</h3>
                                    <p className="text-gray-600 dark:text-gray-400">
                                        {step.description}
                                        {step.title === 'Apply for Special Voting' && (
                                            <span className="ml-1">
<SpecialVotingTooltip>
See eligibility
</SpecialVotingTooltip>
</span>
                                        )}
                                    </p>
                                </div>
                            ))}
                        </section>
                    </>
                )}
            </div>
        </Layout>
    );
}

const steps = [
    {
        title: 'Pre-register Your Interest',
        description: 'Let us know if you plan to attend and your preferred venue and timing.',
        color: '#F17000',
        icon: (
            <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
        ),
    },
    {
        title: 'Receive Venue Assignment',
        description: 'We\'ll assign you to your regional BMM venue based on your preferences.',
        color: '#5BBB87',
        icon: (
            <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
        ),
    },
    {
        title: 'Confirm Your Attendance',
        description: 'Update your details and confirm whether you\'ll attend in person.',
        color: '#5D8BAB',
        icon: (
            <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
        ),
    },
    {
        title: 'Get Your Ticket',
        description: 'Receive your BMM ticket with QR code for easy check-in at your venue.',
        color: '#F17000',
        icon: (
            <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2H5a2 2 0 00-2-2v0" />
            </svg>
        ),
    },
    {
        title: 'Participate in BMM',
        description: 'Join your regional meeting to vote on important union matters and elect leadership.',
        color: '#5BBB87',
        icon: (
            <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
            </svg>
        ),
    },
    {
        title: 'Special Vote Option',
        description: 'Central & Southern members unable to attend may apply for special voting rights.',
        color: '#5D8BAB',
        icon: (
            <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
        ),
    },
];