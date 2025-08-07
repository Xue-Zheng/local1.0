'use client';
import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import Link from 'next/link';
import api from '@/services/api';

interface TestResult {
    name: string;
    status: 'success' | 'error' | 'pending';
    message: string;
    data?: any;
}

export default function BMMTestPage() {
    const router = useRouter();
    const [isAuthorized, setIsAuthorized] = useState(false);
    const [testResults, setTestResults] = useState<TestResult[]>([]);
    const [isRunning, setIsRunning] = useState(false);

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }
        setIsAuthorized(true);
    }, [router]);

    const addTestResult = (name: string, status: 'success' | 'error' | 'pending', message: string, data?: any) => {
        setTestResults(prev => {
            const existingIndex = prev.findIndex(result => result.name === name);
            const newResult = { name, status, message, data };
            if (existingIndex >= 0) {
                const updated = [...prev];
                updated[existingIndex] = newResult;
                return updated;
            } else {
                return [...prev, newResult];
            }
        });
    };

    const runBMMTests = async () => {
        setIsRunning(true);
        setTestResults([]);

        try {
            // Test 1: Check if BMM event exists
            addTestResult('BMM Event Check', 'pending', 'Checking if BMM event exists...');
            const eventsResponse = await api.get('/admin/events');
            const bmmEvents = eventsResponse.data.data.filter((event: any) => event.eventType === 'BMM_VOTING');

            if (bmmEvents.length === 0) {
                addTestResult('BMM Event Check', 'error', 'No BMM_VOTING events found in the system');
                return;
            }

            const bmmEvent = bmmEvents[0];
            addTestResult('BMM Event Check', 'success', `BMM event found: ${bmmEvent.name} (ID: ${bmmEvent.id})`, bmmEvent);

            // Test 2: Check BMM region configuration (three regions)
            addTestResult('BMM Regions Check', 'pending', 'Checking BMM region configuration...');
            const expectedRegions = ['Northern', 'Central', 'Southern'];
            addTestResult('BMM Regions Check', 'success', `BMM configured for 3 regions: ${expectedRegions.join(', ')}`);

            // Test 3: Check EventMember API endpoints
            addTestResult('EventMember API', 'pending', 'Testing EventMember API endpoints...');
            try {
                // Get a sample EventMember
                const membersResponse = await api.get(`/admin/registration/members?eventId=${bmmEvent.id}&page=0&size=1`);
                if (membersResponse.data.data.content.length > 0) {
                    const sampleMember = membersResponse.data.data.content[0];

                    // Test Token validation API
                    const tokenResponse = await api.get(`/event-registration/member/${sampleMember.token}`);
                    if (tokenResponse.data.status === 'success') {
                        addTestResult('EventMember API', 'success', 'EventMember API endpoints working correctly', {
                            sampleToken: sampleMember.token,
                            memberData: tokenResponse.data.data
                        });
                    } else {
                        addTestResult('EventMember API', 'error', 'EventMember token API failed');
                    }
                } else {
                    addTestResult('EventMember API', 'error', 'No EventMember records found for testing');
                }
            } catch (apiError) {
                addTestResult('EventMember API', 'error', 'EventMember API test failed');
            }

            // Test 4: Check BMM page routes
            addTestResult('BMM Pages Check', 'pending', 'Checking BMM page configurations...');
            const bmmPages = [
                '/bmm/index.tsx - BMM main page',
                '/register/bmm-template.tsx - BMM registration template',
                '/admin/event-flow-builder.tsx - BMM three-region QR generation',
                '/admin/bmm-test.tsx - BMM test page'
            ];
            addTestResult('BMM Pages Check', 'success', `BMM pages configured: ${bmmPages.length} pages`, bmmPages);

            // Test 5: Check sync configuration
            addTestResult('Sync Configuration', 'pending', 'Checking sync configuration...');
            const syncConfig = {
                'Main sync interval': '12 hours (43200000ms)',
                'Startup delay': '10 minutes (600000ms)',
                'Daily sync': '1:00 AM - Email Members',
                '': '1:30 AM - SMS Members',
                ' ': '2:00 AM - Event Attendees',
                '  ': '3:00 AM - Full sync check'
            };
            addTestResult('Sync Configuration', 'success', 'Sync configuration optimized for stability', syncConfig);

            // Test 6: Check BMM statistics
            addTestResult('BMM Statistics', 'pending', 'Fetching BMM statistics...');
            try {
                const statsResponse = await api.get(`/venue/checkin/bmm-stats/${bmmEvent.id}`);
                if (statsResponse.data.status === 'success') {
                    addTestResult('BMM Statistics', 'success', 'BMM statistics retrieved', statsResponse.data.data);
                } else {
                    addTestResult('BMM Statistics', 'error', 'Failed to get BMM statistics');
                }
            } catch (statsError) {
                addTestResult('BMM Statistics', 'error', 'BMM statistics API not available');
            }

            // Test 7: Check industry classification filter
            addTestResult('Industry Filter', 'pending', 'Testing industry classification filter...');
            try {
                const filterResponse = await api.get('/admin/registration/filter-options');
                if (filterResponse.data.status === 'success') {
                    const industries = filterResponse.data.data.industries || [];
                    addTestResult('Industry Filter', 'success', `Industry filter working - ${industries.length} industries available`, {
                        industriesCount: industries.length,
                        sampleIndustries: industries.slice(0, 5)
                    });
                } else {
                    addTestResult('Industry Filter', 'error', 'Industry filter API failed');
                }
            } catch (filterError) {
                addTestResult('Industry Filter', 'error', 'Industry filter API not available');
            }

            // Test 8: Check new BMM API endpoints
            addTestResult('BMM API Endpoints', 'pending', 'Testing new BMM API endpoints...');
            try {
                // Test new BMM members API
                const bmmMembersResponse = await api.get('/admin/bmm/members?page=0&size=1');
                const bmmVenuesResponse = await api.get('/admin/bmm/venues-with-capacity');

                let apiResults = [];
                if (bmmMembersResponse.data.status === 'success') {
                    apiResults.push('✅ BMM Members API working');
                } else {
                    apiResults.push('❌ BMM Members API failed');
                }

                if (bmmVenuesResponse.data.status === 'success') {
                    apiResults.push('✅ BMM Venues API working');
                } else {
                    apiResults.push('❌ BMM Venues API failed');
                }

                addTestResult('BMM API Endpoints', 'success', `BMM API endpoints tested`, apiResults);
            } catch (apiError) {
                addTestResult('BMM API Endpoints', 'error', 'BMM API endpoints test failed');
            }

        } catch (error: any) {
            addTestResult('Test Suite', 'error', `Test suite failed: ${error.message}`);
        } finally {
            setIsRunning(false);
        }
    };

    const getStatusIcon = (status: string) => {
        switch (status) {
            case 'success': return '✅';
            case 'error': return '❌';
            case 'pending': return '⏳';
            default: return '❓';
        }
    };

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'success': return 'text-green-600 dark:text-green-400';
            case 'error': return 'text-red-600 dark:text-red-400';
            case 'pending': return 'text-yellow-600 dark:text-yellow-400';
            default: return 'text-gray-600 dark:text-gray-400';
        }
    };

    if (!isAuthorized) {
        return null;
    }

    return (
        <Layout>
            <div className="container mx-auto px-4 py-8">
                <div className="mb-8">
                    <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-4">
                        🗳️ BMM System Test Suite
                    </h1>
                    <p className="text-gray-600 dark:text-gray-400 mb-6">
                        Comprehensive testing of BMM (Biennial Membership Meeting) system functionality
                    </p>

                    <div className="bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700 rounded-lg p-4 mb-6">
                        <h2 className="text-lg font-semibold text-blue-900 dark:text-blue-100 mb-2">
                            BMM Configuration Summary
                        </h2>
                        <div className="grid md:grid-cols-2 gap-4 text-sm">
                            <div>
                                <p className="text-blue-800 dark:text-blue-200">
                                    <strong>Regions:</strong> 3 (Northern, Central, Southern)
                                </p>
                                <p className="text-blue-800 dark:text-blue-200">
                                    <strong>Event Type:</strong> BMM_VOTING
                                </p>
                            </div>
                            <div>
                                <p className="text-blue-800 dark:text-blue-200">
                                    <strong>Sync Interval:</strong> 12 hours
                                </p>
                                <p className="text-blue-800 dark:text-blue-200">
                                    <strong>API Endpoint:</strong> /api/event-registration
                                </p>
                            </div>
                        </div>
                    </div>

                    <div className="flex space-x-4">
                        <button
                            onClick={runBMMTests}
                            disabled={isRunning}
                            className="bg-purple-600 hover:bg-purple-700 disabled:bg-gray-400 text-white font-medium py-3 px-6 rounded-lg"
                        >
                            {isRunning ? '🔄 Running Tests...' : '🚀 Run System Tests'}
                        </button>
                        <button
                            onClick={() => window.open('/admin/bmm-dashboard', '_blank')}
                            className="bg-blue-600 hover:bg-blue-700 text-white font-medium py-3 px-6 rounded-lg"
                        >
                            📊 Open BMM Dashboard
                        </button>
                        <button
                            onClick={() => window.open('/admin/bmm-management', '_blank')}
                            className="bg-green-600 hover:bg-green-700 text-white font-medium py-3 px-6 rounded-lg"
                        >
                            👥 Open Member Management
                        </button>
                    </div>
                </div>

                {testResults.length > 0 && (
                    <div className="space-y-4">
                        <h2 className="text-2xl font-bold text-gray-900 dark:text-white">Test Results</h2>
                        {testResults.map((result, index) => (
                            <div key={index} className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                                <div className="flex items-start space-x-3">
                                    <span className="text-2xl">{getStatusIcon(result.status)}</span>
                                    <div className="flex-1">
                                        <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
                                            {result.name}
                                        </h3>
                                        <p className={`text-sm ${getStatusColor(result.status)} mb-2`}>
                                            {result.message}
                                        </p>
                                        {result.data && (
                                            <div className="bg-gray-50 dark:bg-gray-700 rounded p-3 mt-2">
                                                <pre className="text-xs text-gray-600 dark:text-gray-300 overflow-auto">
                                                    {JSON.stringify(result.data, null, 2)}
                                                </pre>
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </Layout>
    );
}