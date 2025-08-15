import React, { useState } from 'react';
import Layout from '@/components/common/Layout';
import { toast } from 'react-toastify';
import api from '@/services/api';

export default function BMMDataMigration() {
    const [isLoading, setIsLoading] = useState(false);
    const [migrationStatus, setMigrationStatus] = useState<any>(null);

    const handleDataMigration = async (operation: string) => {
        if (!confirm(`Are you sure you want to perform ${operation}? This operation cannot be undone.`)) {
            return;
        }

        setIsLoading(true);
        try {
            const response = await api.post(`/admin/migration/${operation}`);
            if (response.data.status === 'success') {
                toast.success(`${operation} completed successfully`);
                setMigrationStatus(response.data.data);
            } else {
                toast.error(response.data.message || `Failed to perform ${operation}`);
            }
        } catch (error: any) {
            console.error(`Error performing ${operation}:`, error);
            toast.error(error.response?.data?.message || `Failed to perform ${operation}`);
        } finally {
            setIsLoading(false);
        }
    };

    const checkMigrationStatus = async () => {
        try {
            const response = await api.get('/admin/migration/status');
            setMigrationStatus(response.data.data);
        } catch (error: any) {
            console.error('Error checking migration status:', error);
            toast.error('Failed to check migration status');
        }
    };

    React.useEffect(() => {
        checkMigrationStatus();
    }, []);

    return (
        <Layout>
            <div className="container mx-auto px-4 py-8">
                <div className="max-w-4xl mx-auto">
                    <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-8">
                        BMM Data Migration & Management
                    </h1>

                    {/* Warning Section */}
                    <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-6 mb-8">
                        <div className="flex items-start">
                            <div className="flex-shrink-0">
                                <svg className="h-5 w-5 text-red-400" viewBox="0 0 20 20" fill="currentColor">
                                    <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                                </svg>
                            </div>
                            <div className="ml-3">
                                <h3 className="text-sm font-medium text-red-800 dark:text-red-200">
                                    Warning: Data Migration Operations
                                </h3>
                                <div className="mt-2 text-sm text-red-700 dark:text-red-300">
                                    <p>These operations will modify your database. Please ensure you have a backup before proceeding.</p>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Migration Status */}
                    {migrationStatus && (
                        <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-6 mb-8">
                            <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">
                                Current Database Status
                            </h2>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                <div className="bg-blue-50 dark:bg-blue-900/20 p-4 rounded-lg">
                                    <div className="text-2xl font-bold text-blue-600 dark:text-blue-400">
                                        {migrationStatus.totalMembers || 0}
                                    </div>
                                    <div className="text-sm text-blue-800 dark:text-blue-300">Total Members</div>
                                </div>
                                <div className="bg-green-50 dark:bg-green-900/20 p-4 rounded-lg">
                                    <div className="text-2xl font-bold text-green-600 dark:text-green-400">
                                        {migrationStatus.totalEventMembers || 0}
                                    </div>
                                    <div className="text-sm text-green-800 dark:text-green-300">Event Members</div>
                                </div>
                                <div className="bg-purple-50 dark:bg-purple-900/20 p-4 rounded-lg">
                                    <div className="text-2xl font-bold text-purple-600 dark:text-purple-400">
                                        {migrationStatus.totalEvents || 0}
                                    </div>
                                    <div className="text-sm text-purple-800 dark:text-purple-300">Events</div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Migration Operations */}
                    <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
                        <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-6">
                            Migration Operations
                        </h2>

                        <div className="space-y-4">
                            {/* Legacy Data Migration */}
                            <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                                <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
                                    Legacy Data Migration
                                </h3>
                                <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                                    Migrate data from previous Special Conference deployment to BMM format.
                                </p>
                                <button
                                    onClick={() => handleDataMigration('migrate-legacy-data')}
                                    disabled={isLoading}
                                    className="bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white px-4 py-2 rounded-lg mr-3"
                                >
                                    {isLoading ? 'Processing...' : 'Migrate Legacy Data'}
                                </button>
                            </div>

                            {/* Token Regeneration */}
                            <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                                <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
                                    Token Management
                                </h3>
                                <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                                    Regenerate tokens for all EventMembers (use when tokens are compromised).
                                </p>
                                <button
                                    onClick={() => handleDataMigration('regenerate-tokens')}
                                    disabled={isLoading}
                                    className="bg-orange-600 hover:bg-orange-700 disabled:opacity-50 text-white px-4 py-2 rounded-lg mr-3"
                                >
                                    {isLoading ? 'Processing...' : 'Regenerate All Tokens'}
                                </button>
                            </div>

                            {/* Data Cleanup */}
                            <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                                <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
                                    Data Cleanup
                                </h3>
                                <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                                    Remove duplicate records and clean up inconsistent data.
                                </p>
                                <button
                                    onClick={() => handleDataMigration('cleanup-data')}
                                    disabled={isLoading}
                                    className="bg-green-600 hover:bg-green-700 disabled:opacity-50 text-white px-4 py-2 rounded-lg mr-3"
                                >
                                    {isLoading ? 'Processing...' : 'Cleanup Data'}
                                </button>
                            </div>

                            {/* Fresh Start */}
                            <div className="border border-red-200 dark:border-red-700 rounded-lg p-4 bg-red-50 dark:bg-red-900/10">
                                <h3 className="text-lg font-medium text-red-800 dark:text-red-200 mb-2">
                                    Fresh Start (Danger Zone)
                                </h3>
                                <p className="text-sm text-red-600 dark:text-red-400 mb-4">
                                    Clear all BMM-related data and start fresh. This will delete all EventMembers and Events.
                                </p>
                                <button
                                    onClick={() => handleDataMigration('fresh-start')}
                                    disabled={isLoading}
                                    className="bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white px-4 py-2 rounded-lg"
                                >
                                    {isLoading ? 'Processing...' : 'Fresh Start (DELETE ALL)'}
                                </button>
                            </div>
                        </div>
                    </div>

                    {/* Refresh Status */}
                    <div className="mt-6 text-center">
                        <button
                            onClick={checkMigrationStatus}
                            className="bg-gray-600 hover:bg-gray-700 text-white px-6 py-2 rounded-lg"
                        >
                            Refresh Status
                        </button>
                    </div>
                </div>
            </div>
        </Layout>
    );
}