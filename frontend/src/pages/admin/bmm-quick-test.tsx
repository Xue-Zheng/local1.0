'use client';
import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import api from '@/services/api';

interface TestMember {
    id: number;
    name: string;
    membershipNumber: string;
    email: string;
    token: string;
    regionDesc: string;
    bmmRegistrationStage: string;
}

export default function BMMQuickTestPage() {
    const router = useRouter();
    const [currentStage, setCurrentStage] = useState(1);
    const [testMember, setTestMember] = useState<TestMember | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const [venues, setVenues] = useState<any[]>([]);

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }
        loadTestMember();
        loadVenues();
    }, []);

    const loadTestMember = async () => {
        try {
            const response = await api.get('/admin/registration/members?page=0&size=10');
            if (response.data.data.content.length > 0) {
                // Find a member that hasn't completed all stages
                const member = response.data.data.content.find(
                    (m: any) => !m.bmmRegistrationStage || m.bmmRegistrationStage === 'not_started'
                ) || response.data.data.content[0];
                setTestMember(member);
            }
        } catch (error) {
            toast.error('Failed to load test member');
        }
    };

    const loadVenues = async () => {
        try {
            const response = await api.get('/admin/bmm/venues-with-capacity');
            if (response.data.status === 'success') {
                const allVenues: any[] = [];
                Object.values(response.data.data).forEach((regionVenues: any) => {
                    allVenues.push(...regionVenues);
                });
                setVenues(allVenues);
            }
        } catch (error) {
            console.error('Failed to load venues');
        }
    };

    const runStage1Test = async () => {
        if (!testMember) return;
        setIsLoading(true);

        try {
            // Generate registration link
            const registrationLink = `/register/bmm-template?token=${testMember.token}`;

            // Open in new tab
            window.open(registrationLink, '_blank');

            toast.success(`Stage 1 link opened for ${testMember.name}`);
            toast.info('Complete the preference form in the new tab, then click "Check Status"');

        } catch (error) {
            toast.error('Stage 1 test failed');
        } finally {
            setIsLoading(false);
        }
    };

    const runStage2Test = async () => {
        if (!testMember || !venues.length) return;
        setIsLoading(true);

        try {
            // Auto-assign venue
            const venue = venues[0];
            const response = await api.post('/admin/bmm/manual-assign-venue', {
                memberIds: [testMember.id],
                venueName: venue.name,
                assignedDate: new Date().toISOString().split('T')[0],
                assignedTime: '10:00 AM'
            });

            if (response.data.status === 'success') {
                toast.success('Venue assigned successfully');

                // Generate confirmation link
                const confirmLink = `/register/confirm-attendance?token=${testMember.token}`;
                window.open(confirmLink, '_blank');

                toast.info('Complete the confirmation in the new tab');
            }
        } catch (error) {
            toast.error('Stage 2 test failed');
        } finally {
            setIsLoading(false);
        }
    };

    const runStage3Test = async () => {
        if (!testMember) return;
        setIsLoading(true);

        try {
            // Generate ticket
            const response = await api.post('/admin/bmm/generate-tickets', {
                memberIds: [testMember.id]
            });

            if (response.data.status === 'success') {
                toast.success('Ticket generated successfully');

                // Open ticket page
                const ticketLink = `/ticket?token=${testMember.token}`;
                window.open(ticketLink, '_blank');
            }
        } catch (error) {
            toast.error('Stage 3 test failed');
        } finally {
            setIsLoading(false);
        }
    };

    const checkMemberStatus = async () => {
        if (!testMember) return;

        try {
            const response = await api.get(`/event-registration/member/${testMember.token}`);
            if (response.data.status === 'success') {
                const updatedMember = response.data.data;
                toast.info(`Current stage: ${updatedMember.bmmStage || 'not_started'}`);

                // Update current stage based on member status
                if (updatedMember.bmmStage === 'stage1_completed') {
                    setCurrentStage(2);
                } else if (updatedMember.bmmStage === 'stage2_confirmed') {
                    setCurrentStage(3);
                }
            }
        } catch (error) {
            toast.error('Failed to check member status');
        }
    };

    if (!testMember) {
        return (
            <Layout>
                <div className="max-w-7xl mx-auto px-4 py-8">
                    <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
                        <p className="text-yellow-800">Loading test member...</p>
                    </div>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="max-w-7xl mx-auto px-4 py-8">
                <h1 className="text-3xl font-bold mb-8">BMM Quick Test Flow</h1>

                {/* Test Member Info */}
                <div className="bg-gray-100 rounded-lg p-6 mb-8">
                    <h2 className="text-xl font-semibold mb-4">Test Member</h2>
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <span className="font-medium">Name:</span> {testMember.name}
                        </div>
                        <div>
                            <span className="font-medium">Email:</span> {testMember.email}
                        </div>
                        <div>
                            <span className="font-medium">Membership #:</span> {testMember.membershipNumber}
                        </div>
                        <div>
                            <span className="font-medium">Region:</span> {testMember.regionDesc}
                        </div>
                        <div>
                            <span className="font-medium">Token:</span>
                            <code className="text-xs bg-gray-200 px-2 py-1 rounded ml-2">
                                {testMember.token}
                            </code>
                        </div>
                        <div>
                            <span className="font-medium">Current Stage:</span> {testMember.bmmRegistrationStage || 'not_started'}
                        </div>
                    </div>
                    <button
                        onClick={checkMemberStatus}
                        className="mt-4 px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
                    >
                        🔄 Check Status
                    </button>
                </div>

                {/* Stage Progress */}
                <div className="mb-8">
                    <div className="flex items-center justify-between">
                        <div className={`flex-1 text-center ${currentStage >= 1 ? 'text-green-600' : 'text-gray-400'}`}>
                            <div className={`w-10 h-10 mx-auto rounded-full ${currentStage >= 1 ? 'bg-green-600' : 'bg-gray-300'} text-white flex items-center justify-center mb-2`}>
                                1
                            </div>
                            <span className="text-sm">Preference Collection</span>
                        </div>
                        <div className={`flex-1 text-center ${currentStage >= 2 ? 'text-green-600' : 'text-gray-400'}`}>
                            <div className={`w-10 h-10 mx-auto rounded-full ${currentStage >= 2 ? 'bg-green-600' : 'bg-gray-300'} text-white flex items-center justify-center mb-2`}>
                                2
                            </div>
                            <span className="text-sm">Venue Assignment</span>
                        </div>
                        <div className={`flex-1 text-center ${currentStage >= 3 ? 'text-green-600' : 'text-gray-400'}`}>
                            <div className={`w-10 h-10 mx-auto rounded-full ${currentStage >= 3 ? 'bg-green-600' : 'bg-gray-300'} text-white flex items-center justify-center mb-2`}>
                                3
                            </div>
                            <span className="text-sm">Ticket Generation</span>
                        </div>
                    </div>
                </div>

                {/* Stage Actions */}
                <div className="space-y-6">
                    {/* Stage 1 */}
                    <div className="bg-white border rounded-lg p-6">
                        <h3 className="text-lg font-semibold mb-4">Stage 1: Preference Collection</h3>
                        <p className="text-gray-600 mb-4">
                            Opens the registration form where member can select venue preferences
                        </p>
                        <button
                            onClick={runStage1Test}
                            disabled={isLoading || currentStage > 1}
                            className={`px-6 py-2 rounded ${
                                currentStage > 1
                                    ? 'bg-gray-300 text-gray-500 cursor-not-allowed'
                                    : 'bg-green-600 text-white hover:bg-green-700'
                            }`}
                        >
                            {isLoading ? 'Loading...' : '🚀 Run Stage 1 Test'}
                        </button>
                    </div>

                    {/* Stage 2 */}
                    <div className="bg-white border rounded-lg p-6">
                        <h3 className="text-lg font-semibold mb-4">Stage 2: Venue Assignment & Confirmation</h3>
                        <p className="text-gray-600 mb-4">
                            Auto-assigns venue and opens confirmation form
                        </p>
                        <button
                            onClick={runStage2Test}
                            disabled={isLoading || currentStage !== 2}
                            className={`px-6 py-2 rounded ${
                                currentStage !== 2
                                    ? 'bg-gray-300 text-gray-500 cursor-not-allowed'
                                    : 'bg-blue-600 text-white hover:bg-blue-700'
                            }`}
                        >
                            {isLoading ? 'Loading...' : '🏢 Run Stage 2 Test'}
                        </button>
                    </div>

                    {/* Stage 3 */}
                    <div className="bg-white border rounded-lg p-6">
                        <h3 className="text-lg font-semibold mb-4">Stage 3: Ticket Generation</h3>
                        <p className="text-gray-600 mb-4">
                            Generates and displays the digital ticket with QR code
                        </p>
                        <button
                            onClick={runStage3Test}
                            disabled={isLoading || currentStage !== 3}
                            className={`px-6 py-2 rounded ${
                                currentStage !== 3
                                    ? 'bg-gray-300 text-gray-500 cursor-not-allowed'
                                    : 'bg-purple-600 text-white hover:bg-purple-700'
                            }`}
                        >
                            {isLoading ? 'Loading...' : '🎫 Run Stage 3 Test'}
                        </button>
                    </div>
                </div>

                {/* Quick Links */}
                <div className="mt-8 bg-blue-50 border border-blue-200 rounded-lg p-6">
                    <h3 className="text-lg font-semibold mb-4">Quick Links</h3>
                    <div className="space-y-2">
                        <a href="/admin/bmm-management" className="block text-blue-600 hover:underline">
                            → BMM Management Dashboard
                        </a>
                        <a href="/admin/bmm-emails" className="block text-blue-600 hover:underline">
                            → BMM Email Management
                        </a>
                        <a href="/admin/bmm-venue-assignment" className="block text-blue-600 hover:underline">
                            → Venue Assignment
                        </a>
                        <a href="/admin/bmm-dashboard" className="block text-blue-600 hover:underline">
                            → BMM Check-in Dashboard
                        </a>
                    </div>
                </div>
            </div>
        </Layout>
    );
}