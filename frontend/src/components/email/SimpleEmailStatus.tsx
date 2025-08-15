import React, { useState, useEffect } from 'react';
import { toast } from 'react-toastify';
import api from '@/services/api';

const SimpleEmailStatus: React.FC = () => {
    const [statuses, setStatuses] = useState<any[]>([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState('');

    useEffect(() => {
        fetchEmailStatuses();
    }, []);

    const fetchEmailStatuses = async () => {
        try {
            setLoading(true);
            const response = await api.get('/admin/email/status');
            if (response.data.status === 'success') {
                setStatuses(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch email statuses:', error);
            toast.error('Failed to load email statuses');
        } finally {
            setLoading(false);
        }
    };

    const filteredStatuses = statuses.filter(status =>
        status.name.toLowerCase().includes(search.toLowerCase()) ||
        status.email.toLowerCase().includes(search.toLowerCase()) ||
        status.membershipNumber.toLowerCase().includes(search.toLowerCase())
    );

    const formatDate = (dateString: string) => {
        if (!dateString) return 'Never';
        const date = new Date(dateString);
        return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
    };

    if (loading) {
        return (
            <div className="text-center p-4">
                <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-purple-500"></div>
                <p className="mt-2">Loading...</p>
            </div>
        );
    }

    return (
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
            <div className="flex justify-between items-center mb-4">
                <h3 className="text-lg font-semibold">Email Status</h3>
                <button
                    onClick={fetchEmailStatuses}
                    className="bg-blue-500 text-white px-3 py-1 rounded text-sm"
                >
                    Refresh
                </button>
            </div>

            <div className="mb-4">
                <input
                    type="text"
                    placeholder="Search by name, email or membership number"
                    className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                />
            </div>

            <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                    <thead className="bg-gray-50 dark:bg-gray-700">
                    <tr>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Name</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Email</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Type</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Status</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Last Email</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">Count</th>
                    </tr>
                    </thead>
                    <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                    {filteredStatuses.map((status) => (
                        <tr key={status.memberId} className="hover:bg-gray-50 dark:hover:bg-gray-700">
                            <td className="px-4 py-3 whitespace-nowrap text-sm">{status.name}</td>
                            <td className="px-4 py-3 whitespace-nowrap text-sm">{status.email}</td>
                            <td className="px-4 py-3 whitespace-nowrap text-sm">
                  <span className={`px-2 py-1 text-xs rounded-full ${
                      status.isSpecialMember
                          ? 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300'
                          : 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300'
                  }`}>
                    {status.isSpecialMember ? 'Special' : 'Regular'}
                  </span>
                            </td>
                            <td className="px-4 py-3 whitespace-nowrap text-sm">
                  <span className={`px-2 py-1 text-xs rounded-full ${
                      status.hasReceivedEmail
                          ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
                          : 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300'
                  }`}>
                    {status.hasReceivedEmail ? 'Sent' : 'Not Sent'}
                  </span>
                            </td>
                            <td className="px-4 py-3 whitespace-nowrap text-sm">{formatDate(status.lastEmailSent)}</td>
                            <td className="px-4 py-3 whitespace-nowrap text-sm text-center">{status.emailCount}</td>
                        </tr>
                    ))}
                    {filteredStatuses.length === 0 && (
                        <tr>
                            <td colSpan={6} className="px-4 py-3 text-center text-sm text-gray-500 dark:text-gray-400">
                                No members found
                            </td>
                        </tr>
                    )}
                    </tbody>
                </table>
            </div>
        </div>
    );
};

export default SimpleEmailStatus;