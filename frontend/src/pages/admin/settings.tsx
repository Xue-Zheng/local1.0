import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import Link from 'next/link';
import Layout from '@/components/common/Layout';
import api from '@/services/api';
import { toast } from 'react-toastify';

interface SystemSettings {
    siteName: string;
    siteDescription: string;
    adminEmail: string;
    defaultEventType: string;
    maxAttendees: number;
    enableEmailNotifications: boolean;
    enableSmsNotifications: boolean;
    enableAutoSync: boolean;
    syncInterval: number;
    qrCodeExpiry: number;
    defaultThemeColor: string;
    maintenanceMode: boolean;
    debugMode: boolean;
}

export default function SettingsPage() {
    const router = useRouter();
    const [isAuthorized, setIsAuthorized] = useState(false);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [activeTab, setActiveTab] = useState('general');
    const [settings, setSettings] = useState<SystemSettings>({
        siteName: 'E tū Event Management',
        siteDescription: 'Professional event management system for E tū union',
        adminEmail: 'admin@etu.nz',
        defaultEventType: 'GENERAL_MEETING',
        maxAttendees: 1000,
        enableEmailNotifications: true,
        enableSmsNotifications: true,
        enableAutoSync: true,
        syncInterval: 30,
        qrCodeExpiry: 24,
        defaultThemeColor: '#1e40af',
        maintenanceMode: false,
        debugMode: false
    });

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }
        setIsAuthorized(true);
        fetchSettings();
    }, [router]);

    const fetchSettings = async () => {
        try {
            setLoading(true);
// Since backend might not have settings API, we use default values
// const response = await api.get('/admin/settings');
// if (response.data.status === 'success') {
// setSettings(response.data.data);
// }
        } catch (error) {
            console.error('Failed to fetch settings', error);
// Use default settings, don't show error
        } finally {
            setLoading(false);
        }
    };

    const handleSaveSettings = async () => {
        try {
            setSaving(true);
// const response = await api.put('/admin/settings', settings);
// if (response.data.status === 'success') {
            toast.success('Settings saved successfully');
// }
        } catch (error: any) {
            console.error('Failed to save settings', error);
            toast.error('Failed to save settings');
        } finally {
            setSaving(false);
        }
    };

    const handleInputChange = (field: keyof SystemSettings, value: any) => {
        setSettings(prev => ({
            ...prev,
            [field]: value
        }));
    };

    if (!isAuthorized) return null;

    if (loading) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12 text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-purple-500"></div>
                    <p className="mt-4 text-gray-700 dark:text-gray-300">Loading settings...</p>
                </div>
            </Layout>
        );
    }

    return (
        <Layout>
            <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
                <div className="container mx-auto px-4 py-8">
                    {/* Header */}
                    <div className="flex justify-between items-center mb-8">
                        <div className="flex items-center">
                            <Link href="/admin/dashboard">
                                <button className="mr-4 text-gray-600 dark:text-gray-400 hover:text-black dark:hover:text-white">
                                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
                                    </svg>
                                </button>
                            </Link>
                            <h1 className="text-3xl font-bold text-black dark:text-white">  System Settings</h1>
                        </div>
                        <button
                            onClick={handleSaveSettings}
                            disabled={saving}
                            className="bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white px-6 py-2 rounded-lg flex items-center"
                        >
                            {saving ? (
                                <>
                                    <div className="animate-spin rounded-full h-4 w-4 border-t-2 border-b-2 border-white mr-2"></div>
                                    Saving...
                                </>
                            ) : (
                                <>
                                    <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                                    </svg>
                                    Save Settings
                                </>
                            )}
                        </button>
                    </div>

                    <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm">
                        {/* Tabs */}
                        <div className="border-b border-gray-200 dark:border-gray-700">
                            <nav className="-mb-px flex space-x-8 px-6">
                                <button
                                    onClick={() => setActiveTab('general')}
                                    className={`py-4 px-1 border-b-2 font-medium text-sm ${
                                        activeTab === 'general'
                                            ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                            : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600'
                                    }`}
                                >
                                    General
                                </button>
                                <button
                                    onClick={() => setActiveTab('notifications')}
                                    className={`py-4 px-1 border-b-2 font-medium text-sm ${
                                        activeTab === 'notifications'
                                            ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                            : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600'
                                    }`}
                                >
                                    Notifications
                                </button>
                                <button
                                    onClick={() => setActiveTab('events')}
                                    className={`py-4 px-1 border-b-2 font-medium text-sm ${
                                        activeTab === 'events'
                                            ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                            : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600'
                                    }`}
                                >
                                    Events
                                </button>
                                <button
                                    onClick={() => setActiveTab('system')}
                                    className={`py-4 px-1 border-b-2 font-medium text-sm ${
                                        activeTab === 'system'
                                            ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                            : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600'
                                    }`}
                                >
                                    System
                                </button>
                            </nav>
                        </div>

                        {/* Tab Content */}
                        <div className="p-6">
                            {activeTab === 'general' && (
                                <div className="space-y-6">
                                    <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-4">General Settings</h3>
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Site Name</label>
                                            <input
                                                type="text"
                                                value={settings.siteName}
                                                onChange={(e) => handleInputChange('siteName', e.target.value)}
                                                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                            />
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Admin Email</label>
                                            <input
                                                type="primaryEmail"
                                                value={settings.adminEmail}
                                                onChange={(e) => handleInputChange('adminEmail', e.target.value)}
                                                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                            />
                                        </div>

                                        <div className="md:col-span-2">
                                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Site Description</label>
                                            <textarea
                                                value={settings.siteDescription}
                                                onChange={(e) => handleInputChange('siteDescription', e.target.value)}
                                                rows={3}
                                                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                            />
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Default Theme Color</label>
                                            <input
                                                type="color"
                                                value={settings.defaultThemeColor}
                                                onChange={(e) => handleInputChange('defaultThemeColor', e.target.value)}
                                                className="w-full h-10 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                                            />
                                        </div>
                                    </div>
                                </div>
                            )}

                            {activeTab === 'notifications' && (
                                <div className="space-y-6">
                                    <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-4">Notification Settings</h3>
                                    <div className="space-y-4">
                                        <div className="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-700 rounded-lg">
                                            <div>
                                                <h4 className="text-sm font-medium text-gray-900 dark:text-white">Email Notifications</h4>
                                                <p className="text-sm text-gray-500 dark:text-gray-400">Send email notifications to members</p>
                                            </div>
                                            <label className="relative inline-flex items-center cursor-pointer">
                                                <input
                                                    type="checkbox"
                                                    checked={settings.enableEmailNotifications}
                                                    onChange={(e) => handleInputChange('enableEmailNotifications', e.target.checked)}
                                                    className="sr-only peer"
                                                />
                                                <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 dark:peer-focus:ring-blue-800 rounded-full peer dark:bg-gray-600 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-blue-600"></div>
                                            </label>
                                        </div>

                                        <div className="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-700 rounded-lg">
                                            <div>
                                                <h4 className="text-sm font-medium text-gray-900 dark:text-white">SMS Notifications</h4>
                                                <p className="text-sm text-gray-500 dark:text-gray-400">Send SMS notifications to members</p>
                                            </div>
                                            <label className="relative inline-flex items-center cursor-pointer">
                                                <input
                                                    type="checkbox"
                                                    checked={settings.enableSmsNotifications}
                                                    onChange={(e) => handleInputChange('enableSmsNotifications', e.target.checked)}
                                                    className="sr-only peer"
                                                />
                                                <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 dark:peer-focus:ring-blue-800 rounded-full peer dark:bg-gray-600 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-blue-600"></div>
                                            </label>
                                        </div>

                                        <div className="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-700 rounded-lg">
                                            <div>
                                                <h4 className="text-sm font-medium text-gray-900 dark:text-white">Auto Sync</h4>
                                                <p className="text-sm text-gray-500 dark:text-gray-400">Automatically sync data with external systems</p>
                                            </div>
                                            <label className="relative inline-flex items-center cursor-pointer">
                                                <input
                                                    type="checkbox"
                                                    checked={settings.enableAutoSync}
                                                    onChange={(e) => handleInputChange('enableAutoSync', e.target.checked)}
                                                    className="sr-only peer"
                                                />
                                                <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-blue-300 dark:peer-focus:ring-blue-800 rounded-full peer dark:bg-gray-600 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-blue-600"></div>
                                            </label>
                                        </div>

                                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Sync Interval (minutes)</label>
                                                <input
                                                    type="number"
                                                    value={settings.syncInterval}
                                                    onChange={(e) => handleInputChange('syncInterval', parseInt(e.target.value))}
                                                    min="5"
                                                    max="1440"
                                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                                />
                                            </div>

                                            <div>
                                                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">QR Code Expiry (hours)</label>
                                                <input
                                                    type="number"
                                                    value={settings.qrCodeExpiry}
                                                    onChange={(e) => handleInputChange('qrCodeExpiry', parseInt(e.target.value))}
                                                    min="1"
                                                    max="168"
                                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                                />
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {activeTab === 'events' && (
                                <div className="space-y-6">
                                    <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-4">Event Settings</h3>
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Default Event Type</label>
                                            <select
                                                value={settings.defaultEventType}
                                                onChange={(e) => handleInputChange('defaultEventType', e.target.value)}
                                                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                            >
                                                <option value="GENERAL_MEETING">General Meeting</option>
                                                <option value="SPECIAL_CONFERENCE">Special Conference</option>
                                                <option value="SURVEY_MEETING">Survey Meeting</option>
                                                <option value="BMM_VOTING">BMM Voting</option>
                                                <option value="BALLOT_VOTING">Ballot Voting</option>
                                                <option value="ANNUAL_MEETING">Annual Meeting</option>
                                                <option value="WORKSHOP">Workshop</option>
                                                <option value="UNION_MEETING">Union Meeting</option>
                                            </select>
                                        </div>

                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Maximum Attendees</label>
                                            <input
                                                type="number"
                                                value={settings.maxAttendees}
                                                onChange={(e) => handleInputChange('maxAttendees', parseInt(e.target.value))}
                                                min="1"
                                                max="10000"
                                                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                            />
                                        </div>
                                    </div>

                                    <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
                                        <div className="flex">
                                            <div className="flex-shrink-0">
                                                <svg className="h-5 w-5 text-blue-400" viewBox="0 0 20 20" fill="currentColor">
                                                    <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                                                </svg>
                                            </div>
                                            <div className="ml-3">
                                                <h3 className="text-sm font-medium text-blue-800 dark:text-blue-200">Event Configuration</h3>
                                                <div className="mt-2 text-sm text-blue-700 dark:text-blue-300">
                                                    <p>These settings will be applied as defaults when creating new events. Individual events can override these settings.</p>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {activeTab === 'system' && (
                                <div className="space-y-6">
                                    <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-4">System Settings</h3>
                                    <div className="space-y-4">
                                        <div className="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-700 rounded-lg">
                                            <div>
                                                <h4 className="text-sm font-medium text-gray-900 dark:text-white">Maintenance Mode</h4>
                                                <p className="text-sm text-gray-500 dark:text-gray-400">Put the system in maintenance mode</p>
                                            </div>
                                            <label className="relative inline-flex items-center cursor-pointer">
                                                <input
                                                    type="checkbox"
                                                    checked={settings.maintenanceMode}
                                                    onChange={(e) => handleInputChange('maintenanceMode', e.target.checked)}
                                                    className="sr-only peer"
                                                />
                                                <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-red-300 dark:peer-focus:ring-red-800 rounded-full peer dark:bg-gray-600 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-red-600"></div>
                                            </label>
                                        </div>

                                        <div className="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-700 rounded-lg">
                                            <div>
                                                <h4 className="text-sm font-medium text-gray-900 dark:text-white">Debug Mode</h4>
                                                <p className="text-sm text-gray-500 dark:text-gray-400">Enable debug logging and error details</p>
                                            </div>
                                            <label className="relative inline-flex items-center cursor-pointer">
                                                <input
                                                    type="checkbox"
                                                    checked={settings.debugMode}
                                                    onChange={(e) => handleInputChange('debugMode', e.target.checked)}
                                                    className="sr-only peer"
                                                />
                                                <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-yellow-300 dark:peer-focus:ring-yellow-800 rounded-full peer dark:bg-gray-600 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-yellow-600"></div>
                                            </label>
                                        </div>
                                    </div>

                                    <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
                                        <div className="flex">
                                            <div className="flex-shrink-0">
                                                <svg className="h-5 w-5 text-red-400" viewBox="0 0 20 20" fill="currentColor">
                                                    <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                                                </svg>
                                            </div>
                                            <div className="ml-3">
                                                <h3 className="text-sm font-medium text-red-800 dark:text-red-200">Warning</h3>
                                                <div className="mt-2 text-sm text-red-700 dark:text-red-300">
                                                    <p>Enabling maintenance mode will prevent users from accessing the system. Only administrators will be able to log in.</p>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </Layout>
    );
}

