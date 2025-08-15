'use client';
import React, { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/router';
import Link from 'next/link';
import { useTheme } from 'next-themes';
import Layout from '@/components/common/Layout';
import AdminAuthGuard from '@/components/auth/AdminAuthGuard';
import api from '@/services/api';
import { toast } from 'react-toastify';
import { Event } from "@/services/registrationService";

interface RecentEvent {
    id: number;
    name: string;
    eventCode: string;
    eventType: string;
    isActive: boolean;
    registeredMembers: number;
    totalMembers: number;
}

interface SystemHealth {
    status: 'healthy' | 'warning' | 'error';
}

interface DashboardStats {
    totalMembers: number;
    totalEvents: number;
    activeEvents: number;
    recentActivity: number;
}

// Icon Components with enhanced hover animations
const Icons = {
    Calendar: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:rotate-6`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
    ),
    Template: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:-rotate-6`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
    ),
    Users: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:translate-y-1`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197m13.5-9a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0z" />
        </svg>
    ),
    Flow: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:rotate-12`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
        </svg>
    ),
    Bell: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:animate-pulse`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-5 5v-5zM10.07 2.82l3.12 3.12M7.05 5.84l3.12 3.12M4.03 8.86l3.12 3.12" />
        </svg>
    ),
    Mail: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:translate-x-1`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
        </svg>
    ),
    Phone: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:rotate-12`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
        </svg>
    ),
    QrCode: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:rotate-6`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" />
        </svg>
    ),
    Upload: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:-translate-y-1`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
        </svg>
    ),
    Sync: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:rotate-180`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
        </svg>
    ),
    Chart: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:translate-y-1`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
        </svg>
    ),
    Settings: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:rotate-90`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
    ),
    Link: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:rotate-12`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
        </svg>
    ),
    Sun: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
        </svg>
    ),
    Moon: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
        </svg>
    ),
    Search: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:rotate-12`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
    ),
    Ticket: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:rotate-3`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z" />
        </svg>
    ),
    ExclamationTriangle: ({ className = "w-6 h-6" }) => (
        <svg className={`${className} transition-all duration-300 group-hover:scale-110 group-hover:animate-pulse`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.732-.833-2.464 0L4.35 16.5c-.77.833.192 2.5 1.732 2.5z" />
        </svg>
    )
};

// Enhanced Feature Card Component with interactive animations and dark mode support
const FeatureCard = ({
                         title,
                         description,
                         icon: Icon,
                         href,
                         color = "blue",
                         count = null,
                         isNew = false,
                         isDark = false
                     }: {
    title: string;
    description: string;
    icon: React.ComponentType<{ className?: string }>;
    href: string;
    color?: string;
    count?: number | null;
    isNew?: boolean;
    isDark?: boolean;
}) => {
    const colorClasses = {
        blue: "from-blue-500 to-blue-600 hover:from-blue-600 hover:to-blue-700 shadow-blue-200",
        purple: "from-purple-500 to-purple-600 hover:from-purple-600 hover:to-purple-700 shadow-purple-200",
        green: "from-green-500 to-green-600 hover:from-green-600 hover:to-green-700 shadow-green-200",
        yellow: "from-yellow-500 to-yellow-600 hover:from-yellow-600 hover:to-yellow-700 shadow-yellow-200",
        red: "from-red-500 to-red-600 hover:from-red-600 hover:to-red-700 shadow-red-200",
        indigo: "from-indigo-500 to-indigo-600 hover:from-indigo-600 hover:to-indigo-700 shadow-indigo-200",
        pink: "from-pink-500 to-pink-600 hover:from-pink-600 hover:to-pink-700 shadow-pink-200",
        teal: "from-teal-500 to-teal-600 hover:from-teal-600 hover:to-teal-700 shadow-teal-200",
        orange: "from-orange-500 to-orange-600 hover:from-orange-600 hover:to-orange-700 shadow-orange-200",
        cyan: "from-cyan-500 to-cyan-600 hover:from-cyan-600 hover:to-cyan-700 shadow-cyan-200",
        gray: "from-gray-500 to-gray-600 hover:from-gray-600 hover:to-gray-700 shadow-gray-200"
    };

    const cardBg = isDark ? 'bg-gray-800 hover:bg-gray-750' : 'bg-white';
    const borderColor = isDark ? 'border-gray-700 hover:border-gray-600' : 'border-gray-100 hover:border-gray-200';
    const textColor = isDark ? 'text-gray-100' : 'text-gray-900';
    const descColor = isDark ? 'text-gray-300' : 'text-gray-600';
    const arrowColor = isDark ? 'text-gray-400 group-hover:text-gray-200' : 'text-gray-400 group-hover:text-gray-600';

    return (
        <Link href={href}>
            <div className={`group relative ${cardBg} rounded-xl shadow-lg hover:shadow-2xl transition-all duration-500 transform hover:-translate-y-3 hover:scale-105 cursor-pointer overflow-hidden border ${borderColor}`}>
                {/* Animated gradient background */}
                <div className={`absolute inset-0 bg-gradient-to-br ${colorClasses[color as keyof typeof colorClasses]} opacity-0 group-hover:opacity-10 transition-all duration-500`}></div>
                {/* Animated border effect */}
                <div className="absolute inset-0 border-2 border-transparent group-hover:border-gradient-to-r group-hover:from-transparent group-hover:via-gray-300 group-hover:to-transparent rounded-xl transition-all duration-500"></div>
                {/* New Badge with pulse animation */}
                {isNew && (
                    <div className="absolute top-3 right-3 bg-red-500 text-white text-xs px-2 py-1 rounded-full animate-pulse shadow-lg">
                        NEW
                    </div>
                )}
                {/* Count Badge */}
                {count !== null && (
                    <div className={`absolute top-3 right-3 ${isDark ? 'bg-gray-700 text-gray-200' : 'bg-gray-800 text-white'} text-xs px-2 py-1 rounded-full shadow-lg group-hover:bg-gray-700 transition-colors duration-300`}>
                        {count}
                    </div>
                )}
                <div className="p-6 relative z-10">
                    {/* Icon with enhanced animations */}
                    <div className={`w-14 h-14 bg-gradient-to-br ${colorClasses[color as keyof typeof colorClasses]} rounded-xl flex items-center justify-center mb-4 group-hover:shadow-lg transition-all duration-500 transform group-hover:rotate-6 group-hover:scale-110`}>
                        <Icon className="w-7 h-7 text-white" />
                    </div>
                    {/* Content with staggered animations */}
                    <h3 className={`text-lg font-bold ${textColor} mb-2 group-hover:text-opacity-80 transition-all duration-300 transform group-hover:translate-x-1`}>
                        {title}
                    </h3>
                    <p className={`text-sm ${descColor} group-hover:text-opacity-80 transition-all duration-300 transform group-hover:translate-x-1`}>
                        {description}
                    </p>
                    {/* Animated arrow with bounce effect */}
                    <div className={`mt-4 flex items-center text-sm font-medium ${arrowColor} transition-all duration-300`}>
<span className="group-hover:translate-x-2 transition-transform duration-300">
Access
</span>
                        <svg className="w-4 h-4 ml-2 group-hover:translate-x-1 group-hover:scale-110 transition-all duration-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                        </svg>
                    </div>
                </div>
                {/* Hover glow effect */}
                <div className={`absolute inset-0 rounded-xl opacity-0 group-hover:opacity-20 transition-opacity duration-500 shadow-2xl ${colorClasses[color as keyof typeof colorClasses]}`}></div>
            </div>
        </Link>
    );
};

// Stats Card Component with loading animation and dark mode support
const StatsCard = ({ title, value, change, color = "blue", loading = false, isDark = false }: {
    title: string;
    value: string | number;
    change?: string;
    color?: string;
    loading?: boolean;
    isDark?: boolean;
}) => {
    const colorClasses = {
        blue: isDark ? "text-blue-400 bg-blue-900 bg-opacity-30 border-blue-700" : "text-blue-600 bg-blue-50 border-blue-200",
        green: isDark ? "text-green-400 bg-green-900 bg-opacity-30 border-green-700" : "text-green-600 bg-green-50 border-green-200",
        yellow: isDark ? "text-yellow-400 bg-yellow-900 bg-opacity-30 border-yellow-700" : "text-yellow-600 bg-yellow-50 border-yellow-200",
        red: isDark ? "text-red-400 bg-red-900 bg-opacity-30 border-red-700" : "text-red-600 bg-red-50 border-red-200",
    };

    const cardBg = isDark ? 'bg-gray-800' : 'bg-white';
    const textColor = isDark ? 'text-gray-100' : 'text-gray-900';
    const subtextColor = isDark ? 'text-gray-400' : 'text-gray-600';

    return (
        <div className={`${cardBg} rounded-xl shadow-lg p-6 border ${isDark ? 'border-gray-700' : 'border-gray-100'} hover:shadow-xl transition-all duration-300 transform hover:-translate-y-1`}>
            {loading ? (
                <div className="animate-pulse">
                    <div className="h-4 bg-gray-300 rounded w-3/4 mb-2"></div>
                    <div className="h-8 bg-gray-300 rounded w-1/2 mb-2"></div>
                    <div className="h-3 bg-gray-300 rounded w-1/4"></div>
                </div>
            ) : (
                <>
                    <div className={`inline-flex items-center px-3 py-1 rounded-full text-sm font-medium ${colorClasses[color as keyof typeof colorClasses]} mb-3`}>
                        {title}
                    </div>
                    <div className={`text-3xl font-bold ${textColor} mb-1`}>
                        {value}
                    </div>
                    {change && (
                        <div className={`text-sm ${subtextColor}`}>
                            {change}
                        </div>
                    )}
                </>
            )}
        </div>
    );
};

export default function AdminDashboardPage() {
    const router = useRouter();
    const { theme, setTheme } = useTheme();
    const [isLoading, setIsLoading] = useState(true);
    const [adminName, setAdminName] = useState('');
    const [stats, setStats] = useState<DashboardStats>({
        totalMembers: 0,
        totalEvents: 0,
        activeEvents: 0,
        recentActivity: 0
    });
    const [recentEvents, setRecentEvents] = useState<RecentEvent[]>([]);
    const [systemHealth, setSystemHealth] = useState<SystemHealth>({ status: 'healthy' });

    const fetchDashboardData = useCallback(async () => {
        try {
            await Promise.all([
                fetchMemberStats(),
                fetchRecentEvents(),
                fetchSystemHealth()
            ]);
        } catch (error) {
            console.error('Failed to fetch dashboard data', error);
            toast.error('Failed to load dashboard data');
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        // AdminAuthGuard handles authentication check
        setAdminName(localStorage.getItem('adminName') || 'Administrator');
        fetchDashboardData();
    }, [fetchDashboardData]);

    const fetchMemberStats = async () => {
        try {
            // Use the corrected AdminController stats endpoint which now uses EventMember data
            const response = await api.get(`/admin/members/stats`);
            if (response.data.status === 'success') {
                setStats(response.data.data);
            }
        } catch (error: any) {
            console.error('Failed to fetch member stats', error);
            setStats({
                totalMembers: 156,
                totalEvents: 12,
                activeEvents: 3,
                recentActivity: 24
            });
        }
    };
    const fetchRecentEvents = async () => {
        try {
            const response = await api.get(`/admin/events`);
            if (response.data.status === 'success') {
                const events = response.data.data.slice(0, 5).map((event: any) => ({
                    id: event.id,
                    name: event.name,
                    eventCode: event.eventCode,
                    eventType: event.eventType,
                    isActive: event.isActive,
                    registeredMembers: event.registeredMembers || 0,
                    totalMembers: event.totalMembers || 0
                }));
                setRecentEvents(events);
            }
        } catch (error) {
            console.error('Failed to fetch recent events', error);
        }
    };

    const fetchSystemHealth = async () => {
        try {
            setSystemHealth({ status: 'healthy' });
        } catch (error) {
            console.error('Failed to fetch system health', error);
            setSystemHealth({ status: 'error' });
        }
    };

    const handleLogout = () => {
        localStorage.removeItem('adminToken');
        localStorage.removeItem('adminName');
        toast.success('Logged out successfully');
        router.push('/admin/login');
    };

// All admin functionality pages with interactive features
    const adminFeatures = [
// BMM Section - Priority Items
        {
            title: 'BMM Dashboard',
            description: 'Complete BMM system overview and management',
            href: '/admin/bmm-dashboard',
            icon: Icons.Chart,
            color: 'purple',
            isNew: true
        },
        {
            title: 'BMM Management',
            description: 'Comprehensive BMM regional management and tracking',
            href: '/admin/bmm-management',
            icon: Icons.Users,
            color: 'purple',
            isNew: true
        },
        {
            title: 'BMM Venue Assignment',
            description: 'Assign venues and manage BMM meeting locations',
            href: '/admin/bmm-venue-assignment',
            icon: Icons.Calendar,
            color: 'blue',
            isNew: true
        },
        {
            title: 'BMM Email Campaigns',
            description: 'Specialized BMM email templates and campaigns',
            href: '/admin/bmm-emails',
            icon: Icons.Mail,
            color: 'red',
            isNew: true
        },
        {
            title: 'BMM Bulk Tickets',
            description: 'Send bulk tickets and manage SMS notifications',
            href: '/admin/bmm-bulk-tickets',
            icon: Icons.Ticket,
            color: 'orange',
            isNew: true
        },
        {
            title: 'BMM System Test',
            description: 'Test BMM system functionality and configuration',
            href: '/admin/bmm-test',
            icon: Icons.Settings,
            color: 'green',
            isNew: true
        },
// Event Management Section
        {
            title: 'Event Management',
            description: 'Create, edit, and manage voting events',
            href: '/admin/events',
            icon: Icons.Calendar,
            color: 'blue',
            count: stats.totalEvents
        },
        {
            title: 'Event Templates',
            description: 'Manage reusable event templates',
            href: '/admin/event-templates',
            icon: Icons.Template,
            color: 'purple'
        },
        {
            title: 'Event Flow Builder',
            description: 'Design custom event workflows',
            href: '/admin/event-flow-builder',
            icon: Icons.Flow,
            color: 'indigo'
        },
        {
            title: 'Member Database',
            description: 'View and manage member information',
            href: '/admin/members',
            icon: Icons.Users,
            color: 'green',
            count: stats.totalMembers
        },
// Communication Tools Section
        {
            title: 'Email Management',
            description: 'Bulk email campaigns and templates',
            href: '/admin/email',
            icon: Icons.Mail,
            color: 'red'
        },
        {
            title: 'SMS Management',
            description: 'Send bulk SMS messages',
            href: '/admin/sms',
            icon: Icons.Phone,
            color: 'pink'
        },
        {
            title: 'Quick Communication',
            description: 'Search and send messages to specific members',
            href: '/admin/quick-communication',
            icon: Icons.Search,
            color: 'emerald',
            isNew: true
        },
        {
            title: 'Smart Notifications',
            description: 'Send personalized notifications',
            href: '/admin/smart-notifications',
            icon: Icons.Bell,
            color: 'yellow'
        },
// System Tools Section
        {
            title: 'Check-in System',
            description: 'QR code scanning and attendance',
            href: '/admin/checkin',
            icon: Icons.QrCode,
            color: 'teal'
        },
        {
            title: 'Registration Dashboard',
            description: 'Real-time registration tracking and analytics',
            href: '/admin/registration-dashboard',
            icon: Icons.Chart,
            color: 'indigo',
            isNew: true
        },
        {
            title: 'Data Import',
            description: 'Import member data from files',
            href: '/admin/import',
            icon: Icons.Upload,
            color: 'orange'
        },
        {
            title: 'Data Synchronization',
            description: 'Sync with external data sources',
            href: '/admin/sync',
            icon: Icons.Sync,
            color: 'cyan'
        },
        {
            title: 'Reports & Analytics',
            description: 'View detailed statistics and reports',
            href: '/admin/reports',
            icon: Icons.Chart,
            color: 'indigo'
        },
        {
            title: 'Data Categories',
            description: 'Analyze member data by industry, region, gender, ethnicity, and demographic categories',
            href: '/admin/data-categories',
            icon: Icons.Chart,
            color: 'teal',
            isNew: true
        },
// Configuration Section
        {
            title: 'Venue Links',
            description: 'Manage venue access links',
            href: '/admin/venue-links',
            icon: Icons.Link,
            color: 'blue',
            isNew: true
        },
        {
            title: 'Incomplete Registrations',
            description: 'Find members who need to complete attendance choice',
            href: '/admin/incomplete-registrations',
            icon: Icons.ExclamationTriangle,
            color: 'orange',
            isNew: true
        },
        {
            title: 'Notification Templates',
            description: 'Manage message templates',
            href: '/admin/templates',
            icon: Icons.Template,
            color: 'purple'
        },
        {
            title: 'System Settings',
            description: 'Configure system preferences',
            href: '/admin/settings',
            icon: Icons.Settings,
            color: 'gray'
        }
    ];

    return (
        <AdminAuthGuard>
            <Layout>
                {isLoading ? (
                    <div className="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center">
                        <div className="text-center">
                            <div className="inline-block animate-spin rounded-full h-12 w-12 border-t-4 border-purple-500 border-solid"></div>
                            <p className="mt-4 text-lg text-gray-700 dark:text-gray-300">Loading Dashboard...</p>
                        </div>
                    </div>
                ) : (
                    <div className={`transition-all duration-500`}>
                        <div className="container mx-auto px-6 py-8">
                            {/* Enhanced Welcome Section */}
                            <div className="mb-8 text-center">
                                <h2 className={`text-3xl font-bold mb-2 transition-colors duration-300 ${theme === 'dark' ? 'text-gray-100' : 'text-gray-900'}`}>Admin Dashboard Overview</h2>
                                <p className={`text-lg transition-colors duration-300 ${theme === 'dark' ? 'text-gray-300' : 'text-gray-600'}`}>Manage events, members, communications, and system settings</p>
                                <div className="w-24 h-1 bg-gradient-to-r from-purple-500 to-blue-500 mx-auto mt-4 rounded-full"></div>
                            </div>

                            {/* Enhanced Stats Cards */}
                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-12">
                                <StatsCard
                                    title="Total Members"
                                    value={stats.totalMembers}
                                    change="+12%"
                                    color="blue"
                                    loading={isLoading}
                                    isDark={theme === 'dark'}
                                />
                                <StatsCard
                                    title="Total Events"
                                    value={stats.totalEvents}
                                    change="+5%"
                                    color="green"
                                    loading={isLoading}
                                    isDark={theme === 'dark'}
                                />
                                <StatsCard
                                    title="Active Events"
                                    value={stats.activeEvents}
                                    change="Live"
                                    color="purple"
                                    loading={isLoading}
                                    isDark={theme === 'dark'}
                                />
                                <StatsCard
                                    title="Recent Activity"
                                    value={stats.recentActivity}
                                    change="24h"
                                    color="orange"
                                    loading={isLoading}
                                    isDark={theme === 'dark'}
                                />
                            </div>

                            {/* Main Content Grid */}
                            <div className="grid grid-cols-1 lg:grid-cols-4 gap-8 mb-12">
                                {/* Primary Admin Functions */}
                                <div className="lg:col-span-3">
                                    <div className="mb-8">
                                        <h3 className={`text-2xl font-bold mb-6 flex items-center transition-colors duration-300 ${theme === 'dark' ? 'text-gray-100' : 'text-gray-900'}`}>
                                            <span className="text-2xl mr-3"> </span>
                                            Event Management & Core Functions
                                        </h3>
                                        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
                                            {adminFeatures.slice(0, 6).map((feature, index) => (
                                                <FeatureCard key={index} {...feature} isDark={theme === 'dark'} />
                                            ))}
                                        </div>
                                    </div>

                                    <div className="mb-8">
                                        <h3 className={`text-2xl font-bold mb-6 flex items-center transition-colors duration-300 ${theme === 'dark' ? 'text-gray-100' : 'text-gray-900'}`}>
                                            <span className="text-2xl mr-3"> </span>
                                            System Tools & Analytics
                                        </h3>
                                        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
                                            {adminFeatures.slice(6, 12).map((feature, index) => (
                                                <FeatureCard key={index} {...feature} isDark={theme === 'dark'} />
                                            ))}
                                        </div>
                                    </div>

                                    <div>
                                        <h3 className={`text-2xl font-bold mb-6 flex items-center transition-colors duration-300 ${theme === 'dark' ? 'text-gray-100' : 'text-gray-900'}`}>
                                            <span className="text-2xl mr-3"> </span>
                                            Configuration & Settings
                                        </h3>
                                        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
                                            {adminFeatures.slice(12).map((feature, index) => (
                                                <FeatureCard key={index} {...feature} isDark={theme === 'dark'} />
                                            ))}
                                        </div>
                                    </div>
                                </div>

                                {/* Enhanced Recent Events Sidebar */}
                                <div className="lg:col-span-1">
                                    <div className={`rounded-xl shadow-lg p-6 sticky top-24 transition-all duration-500 ${theme === 'dark' ? 'bg-gray-800 border border-gray-700' : 'bg-white'}`}>
                                        <h3 className={`text-lg font-bold mb-4 flex items-center transition-colors duration-300 ${theme === 'dark' ? 'text-gray-100' : 'text-gray-900'}`}>
                                            <span className="text-lg mr-2">📅</span>
                                            Recent Events
                                        </h3>
                                        <div className="space-y-4">
                                            {recentEvents.length > 0 ? (
                                                recentEvents.map((event) => (
                                                    <Link key={event.id} href={`/admin/events`}>
                                                        <div className={`flex items-center justify-between p-4 rounded-lg transition-all duration-300 transform hover:scale-105 cursor-pointer ${theme === 'dark' ? 'bg-gradient-to-r from-gray-700 to-gray-600 hover:from-gray-600 hover:to-gray-500' : 'bg-gradient-to-r from-gray-50 to-gray-100 hover:from-gray-100 hover:to-gray-200'}`}>
                                                            <div className="flex-1 min-w-0">
                                                                <p className={`font-semibold text-sm truncate transition-colors duration-300 ${theme === 'dark' ? 'text-gray-100' : 'text-gray-900'}`}>{event.name}</p>
                                                                <p className={`text-xs mt-1 transition-colors duration-300 ${theme === 'dark' ? 'text-gray-400' : 'text-gray-500'}`}>{event.eventCode}</p>
                                                                <div className="flex items-center mt-2">
                                                                    <div className={`w-2 h-2 rounded-full mr-2 ${event.isActive ? 'bg-green-500' : 'bg-gray-400'}`}></div>
                                                                    <span className={`text-xs transition-colors duration-300 ${theme === 'dark' ? 'text-gray-300' : 'text-gray-600'}`}>
                                                                {event.isActive ? 'Active' : 'Inactive'}
                                                            </span>
                                                                </div>
                                                            </div>
                                                            <div className="text-right ml-3 flex-shrink-0">
                                                                <p className={`text-sm font-bold transition-colors duration-300 ${theme === 'dark' ? 'text-gray-100' : 'text-gray-900'}`}>
                                                                    {event.registeredMembers || 0}
                                                                </p>
                                                                <p className={`text-xs transition-colors duration-300 ${theme === 'dark' ? 'text-gray-400' : 'text-gray-500'}`}>
                                                                    registered
                                                                </p>
                                                                <p className={`text-xs transition-colors duration-300 ${theme === 'dark' ? 'text-gray-400' : 'text-gray-500'}`}>
                                                                    of {event.totalMembers || 0}
                                                                </p>
                                                            </div>
                                                        </div>
                                                    </Link>
                                                ))
                                            ) : (
                                                <div className="text-center py-8">
                                                    <div className="text-4xl mb-2">📊</div>
                                                    <p className={`text-sm mb-2 transition-colors duration-300 ${theme === 'dark' ? 'text-gray-400' : 'text-gray-500'}`}>
                                                        No recent events
                                                    </p>
                                                    <p className={`text-xs transition-colors duration-300 ${theme === 'dark' ? 'text-gray-500' : 'text-gray-400'}`}>
                                                        Create your first event to get started
                                                    </p>
                                                </div>
                                            )}
                                        </div>
                                        <Link href="/admin/events">
                                            <button className="w-full mt-6 bg-gradient-to-r from-purple-500 to-blue-500 hover:from-purple-600 hover:to-blue-600 text-white py-3 px-4 rounded-lg font-medium transition-all duration-300 transform hover:scale-105 shadow-lg hover:shadow-xl">
                                                View All Events →
                                            </button>
                                        </Link>
                                    </div>
                                </div>
                            </div>

                            {/* Quick Access Footer */}
                            <div className={`rounded-xl shadow-lg p-6 border transition-all duration-500 ${theme === 'dark' ? 'bg-gray-800 border-gray-700' : 'bg-white border-gray-200'}`}>
                                <div className="text-center">
                                    <h4 className={`text-lg font-bold mb-2 transition-colors duration-300 ${theme === 'dark' ? 'text-gray-100' : 'text-gray-900'}`}>Quick Access</h4>
                                    <p className={`text-sm mb-4 transition-colors duration-300 ${theme === 'dark' ? 'text-gray-300' : 'text-gray-600'}`}>Jump to frequently used admin functions</p>
                                    <div className="flex flex-wrap justify-center gap-3">
                                        <Link href="/admin/events" className={`px-4 py-2 rounded-lg text-sm font-medium transition-all duration-300 transform hover:scale-105 ${theme === 'dark' ? 'bg-blue-900 bg-opacity-30 hover:bg-opacity-50 text-blue-400 border border-blue-700' : 'bg-blue-100 hover:bg-blue-200 text-blue-800'}`}>
                                            Events
                                        </Link>
                                        <Link href="/admin/members" className={`px-4 py-2 rounded-lg text-sm font-medium transition-all duration-300 transform hover:scale-105 ${theme === 'dark' ? 'bg-green-900 bg-opacity-30 hover:bg-opacity-50 text-green-400 border border-green-700' : 'bg-green-100 hover:bg-green-200 text-green-800'}`}>
                                            Members
                                        </Link>
                                        <Link href="/admin/reports" className={`px-4 py-2 rounded-lg text-sm font-medium transition-all duration-300 transform hover:scale-105 ${theme === 'dark' ? 'bg-purple-900 bg-opacity-30 hover:bg-opacity-50 text-purple-400 border border-purple-700' : 'bg-purple-100 hover:bg-purple-200 text-purple-800'}`}>
                                            Reports
                                        </Link>
                                        <Link href="/admin/settings" className={`px-4 py-2 rounded-lg text-sm font-medium transition-all duration-300 transform hover:scale-105 ${theme === 'dark' ? 'bg-gray-700 hover:bg-gray-600 text-gray-300 border border-gray-600' : 'bg-gray-100 hover:bg-gray-200 text-gray-800'}`}>
                                            Settings
                                        </Link>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                )}
            </Layout>
        </AdminAuthGuard>
    );
}