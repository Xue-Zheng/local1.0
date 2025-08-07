'use client';
import { ReactNode, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/router';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

interface AdminLayoutProps {
    children: ReactNode;
}

interface NavItem {
    label: string;
    href: string;
    icon?: string;
}

interface NavGroup {
    title: string;
    items: NavItem[];
}

const AdminLayout = ({ children }: AdminLayoutProps) => {
    const router = useRouter();
    const [isCollapsed, setIsCollapsed] = useState(false);
    const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set(['BMM Management']));

    const navGroups: NavGroup[] = [
        {
            title: 'Dashboard',
            items: [
                { label: 'Overview', href: '/admin/dashboard', icon: '📊' },
                { label: 'BMM Dashboard', href: '/admin/bmm-dashboard', icon: '🎯' },
            ]
        },
        {
            title: 'BMM Management',
            items: [
                { label: 'Member Management', href: '/admin/bmm-management', icon: '👥' },
                { label: 'Attendance Overview', href: '/admin/bmm-attendance-overview', icon: '✅' },
                { label: 'Preferences', href: '/admin/bmm-preferences-overview', icon: '⚙️' },
                { label: 'Venue Assignment', href: '/admin/bmm-venue-assignment', icon: '📍' },
                { label: 'Bulk Tickets', href: '/admin/bmm-bulk-tickets', icon: '🎫' },
                { label: 'Timeline', href: '/admin/bmm-timeline', icon: '📅' },
            ]
        },
        {
            title: 'Communication',
            items: [
                { label: 'Email Broadcast', href: '/admin/email', icon: '📧' },
                { label: 'BMM Emails', href: '/admin/bmm-emails', icon: '✉️' },
                { label: 'SMS', href: '/admin/sms', icon: '💬' },
                { label: 'Quick Comm', href: '/admin/quick-communication', icon: '⚡' },
            ]
        },
        {
            title: 'Registration',
            items: [
                { label: 'Registration Dashboard', href: '/admin/registration-dashboard', icon: '📝' },
                { label: 'Incomplete Registrations', href: '/admin/incomplete-registrations', icon: '⚠️' },
                { label: 'Check-in', href: '/admin/checkin', icon: '✔️' },
                { label: 'Venue Links', href: '/admin/venue-links', icon: '🔗' },
            ]
        },
        {
            title: 'Data & Reports',
            items: [
                { label: 'Members', href: '/admin/members', icon: '👤' },
                { label: 'Reports', href: '/admin/reports', icon: '📈' },
                { label: 'Import Data', href: '/admin/import', icon: '📥' },
                { label: 'Sync', href: '/admin/sync', icon: '🔄' },
            ]
        },
        {
            title: 'Settings',
            items: [
                { label: 'Events', href: '/admin/events', icon: '🎪' },
                { label: 'Templates', href: '/admin/templates', icon: '📄' },
                { label: 'Settings', href: '/admin/settings', icon: '⚙️' },
            ]
        }
    ];

    const toggleGroup = (groupTitle: string) => {
        const newExpanded = new Set(expandedGroups);
        if (newExpanded.has(groupTitle)) {
            newExpanded.delete(groupTitle);
        } else {
            newExpanded.add(groupTitle);
        }
        setExpandedGroups(newExpanded);
    };

    const isActive = (href: string) => router.pathname === href;

    return (
        <div className="flex h-screen bg-gray-50 dark:bg-gray-900">
            {/* Sidebar */}
            <div className={`${isCollapsed ? 'w-16' : 'w-64'} bg-white dark:bg-gray-800 shadow-lg transition-all duration-300 overflow-hidden`}>
                <div className="flex flex-col h-full">
                    {/* Header */}
                    <div className="p-4 border-b dark:border-gray-700">
                        <div className="flex items-center justify-between">
                            <Link href="/admin/dashboard" className={`flex items-center space-x-2 ${isCollapsed ? 'justify-center' : ''}`}>
                                <span className="text-2xl">🏢</span>
                                {!isCollapsed && <span className="font-bold text-lg">Admin Portal</span>}
                            </Link>
                            <button
                                onClick={() => setIsCollapsed(!isCollapsed)}
                                className="p-1 hover:bg-gray-100 dark:hover:bg-gray-700 rounded"
                            >
                                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                                          d={isCollapsed ? "M13 5l7 7-7 7" : "M11 19l-7-7 7-7"} />
                                </svg>
                            </button>
                        </div>
                    </div>

                    {/* Search Bar */}
                    {!isCollapsed && (
                        <div className="p-4 border-b dark:border-gray-700">
                            <input
                                type="text"
                                placeholder="Quick search..."
                                className="w-full px-3 py-2 text-sm border rounded-lg focus:outline-none focus:ring-2 focus:ring-orange-500"
                                onKeyDown={(e) => {
                                    if (e.key === 'Enter') {
                                        const search = e.currentTarget.value.toLowerCase();
                                        // Simple search logic - find first matching page
                                        for (const group of navGroups) {
                                            for (const item of group.items) {
                                                if (item.label.toLowerCase().includes(search)) {
                                                    router.push(item.href);
                                                    e.currentTarget.value = '';
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }}
                            />
                        </div>
                    )}

                    {/* Navigation */}
                    <nav className="flex-1 overflow-y-auto p-4">
                        {navGroups.map((group) => (
                            <div key={group.title} className="mb-4">
                                <button
                                    onClick={() => toggleGroup(group.title)}
                                    className={`flex items-center justify-between w-full text-left font-semibold text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white mb-2 ${isCollapsed ? 'px-1' : ''}`}
                                >
                                    {!isCollapsed && <span>{group.title}</span>}
                                    {!isCollapsed && (
                                        <svg className={`w-4 h-4 transform transition-transform ${expandedGroups.has(group.title) ? 'rotate-90' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                                        </svg>
                                    )}
                                </button>
                                {(expandedGroups.has(group.title) || isCollapsed) && (
                                    <ul className="space-y-1">
                                        {group.items.map((item) => (
                                            <li key={item.href}>
                                                <Link
                                                    href={item.href}
                                                    className={`flex items-center space-x-2 px-3 py-2 text-sm rounded-lg transition-colors ${
                                                        isActive(item.href)
                                                            ? 'bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400'
                                                            : 'hover:bg-gray-100 dark:hover:bg-gray-700'
                                                    } ${isCollapsed ? 'justify-center' : ''}`}
                                                    title={isCollapsed ? item.label : ''}
                                                >
                                                    <span className="text-lg">{item.icon}</span>
                                                    {!isCollapsed && <span>{item.label}</span>}
                                                </Link>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </div>
                        ))}
                    </nav>

                    {/* Footer */}
                    <div className="p-4 border-t dark:border-gray-700">
                        <Link
                            href="/"
                            className={`flex items-center space-x-2 text-sm text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white ${isCollapsed ? 'justify-center' : ''}`}
                        >
                            <span>🏠</span>
                            {!isCollapsed && <span>Back to Main Site</span>}
                        </Link>
                    </div>
                </div>
            </div>

            {/* Main Content */}
            <div className="flex-1 flex flex-col overflow-hidden">
                {/* Top Bar */}
                <header className="bg-white dark:bg-gray-800 shadow-sm border-b dark:border-gray-700">
                    <div className="px-6 py-4">
                        <div className="flex items-center justify-between">
                            <h1 className="text-2xl font-semibold text-gray-800 dark:text-white">
                                {navGroups.flatMap(g => g.items).find(item => item.href === router.pathname)?.label || 'Admin'}
                            </h1>
                            <div className="flex items-center space-x-4">
                                {/* Quick Actions */}
                                <button className="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg" title="Notifications">
                                    🔔
                                </button>
                                <button className="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg" title="Help">
                                    ❓
                                </button>
                            </div>
                        </div>
                    </div>
                </header>

                {/* Page Content */}
                <main className="flex-1 overflow-y-auto bg-gray-50 dark:bg-gray-900">
                    <div className="container mx-auto px-6 py-8">
                        {children}
                    </div>
                </main>
            </div>

            <ToastContainer
                position="top-right"
                autoClose={4000}
                hideProgressBar={false}
                newestOnTop={true}
                closeOnClick
                rtl={false}
                pauseOnFocusLoss
                draggable
                pauseOnHover
                theme="colored"
            />
        </div>
    );
};

export default AdminLayout;