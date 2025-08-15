import React from 'react';
import Link from 'next/link';
import Layout from '@/components/common/Layout';

interface FeatureCard {
    title: string;
    description: string;
    link: string;
    icon: string;
    status: 'active' | 'new' | 'important';
    category: string;
}

export default function FeaturesOverviewPage() {
    const features: FeatureCard[] = [
        // 核心功能
        {
            title: '📧 Email Management',
            description: 'Bulk email sending with filtering by registration status, region, and industry',
            link: '/admin/email',
            icon: '📧',
            status: 'active',
            category: 'communication'
        },
        {
            title: '📱 SMS Management',
            description: 'SMS sending via Stratum API, prioritizing Financial Form updated phone numbers',
            link: '/admin/sms',
            icon: '📱',
            status: 'active',
            category: 'communication'
        },
        {
            title: '🎫 Ticket Email Management',
            description: 'Automatic sending of cinema-style professional ticket emails',
            link: '/admin/ticket-management',
            icon: '🎫',
            status: 'new',
            category: 'registration'
        },

        // 数据管理
        {
            title: '🔄 BMM Member Migration',
            description: 'Bulk import from Members table to EventMember with one-click BMM import',
            link: '/admin/bmm-migration',
            icon: '🔄',
            status: 'new',
            category: 'data'
        },
        {
            title: '📥 Informer Data Import',
            description: 'Smart routing of Informer data to appropriate tables',
            link: '/admin/import',
            icon: '📥',
            status: 'active',
            category: 'data'
        },
        {
            title: '👥 Member Management',
            description: 'View and manage all member information',
            link: '/admin/members',
            icon: '👥',
            status: 'active',
            category: 'data'
        },

        // 事件管理
        {
            title: '📅 Event Management',
            description: 'Create and manage BMM and other events',
            link: '/admin/events',
            icon: '📅',
            status: 'active',
            category: 'events'
        },
        {
            title: '📊 Registration Dashboard',
            description: 'Real-time registration statistics and progress monitoring',
            link: '/admin/registration-dashboard',
            icon: '📊',
            status: 'active',
            category: 'events'
        },
        {
            title: '✅ Check-in Management',
            description: 'QR code scanning and on-site check-in management',
            link: '/admin/checkin',
            icon: '✅',
            status: 'active',
            category: 'events'
        },

        // 分析报告
        {
            title: '📈 Data Reports',
            description: 'Statistical analysis and export functionality',
            link: '/admin/reports',
            icon: '📈',
            status: 'active',
            category: 'analysis'
        },
        {
            title: '🔔 Smart Notifications',
            description: 'Condition-based automated notification system',
            link: '/admin/smart-notifications',
            icon: '🔔',
            status: 'active',
            category: 'analysis'
        },
        {
            title: '📋 Data Synchronization',
            description: 'Data sync with external systems',
            link: '/admin/sync',
            icon: '📋',
            status: 'active',
            category: 'analysis'
        }
    ];

    const categories = {
        communication: { name: '📞 Communication Systems', color: 'bg-blue-100 border-blue-300' },
        registration: { name: '🎫 Registration Management', color: 'bg-green-100 border-green-300' },
        data: { name: '💾 Data Management', color: 'bg-purple-100 border-purple-300' },
        events: { name: '📅 Event Management', color: 'bg-orange-100 border-orange-300' },
        analysis: { name: '📊 Analysis & Reports', color: 'bg-gray-100 border-gray-300' }
    };

    const getStatusBadge = (status: string) => {
        switch (status) {
            case 'new':
                return <span className="bg-green-100 text-green-800 text-xs px-2 py-1 rounded-full">New</span>;
            case 'important':
                return <span className="bg-red-100 text-red-800 text-xs px-2 py-1 rounded-full">Important</span>;
            default:
                return <span className="bg-gray-100 text-gray-800 text-xs px-2 py-1 rounded-full">Active</span>;
        }
    };

    return (
        <Layout>
            <div className="container mx-auto px-4 py-8">
                <div className="mb-8">
                    <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-2">
                        🎯 BMM Management System Overview
                    </h1>
                    <p className="text-gray-600 dark:text-gray-300">
                        Complete BMM event management with registration, email, SMS, ticketing and data analysis
                    </p>
                </div>

                {/* 快速统计 */}
                <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
                    <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                        <div className="text-2xl font-bold text-blue-600">12</div>
                        <div className="text-sm text-blue-700">Core Features</div>
                    </div>
                    <div className="bg-green-50 border border-green-200 rounded-lg p-4">
                        <div className="text-2xl font-bold text-green-600">2</div>
                        <div className="text-sm text-green-700">New Features</div>
                    </div>
                    <div className="bg-purple-50 border border-purple-200 rounded-lg p-4">
                        <div className="text-2xl font-bold text-purple-600">5</div>
                        <div className="text-sm text-purple-700">Categories</div>
                    </div>
                    <div className="bg-orange-50 border border-orange-200 rounded-lg p-4">
                        <div className="text-2xl font-bold text-orange-600">100%</div>
                        <div className="text-sm text-orange-700">System Availability</div>
                    </div>
                </div>

                {/* 按分类显示功能 */}
                {Object.entries(categories).map(([categoryKey, categoryInfo]) => (
                    <div key={categoryKey} className="mb-8">
                        <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-4 flex items-center">
                            {categoryInfo.name}
                        </h2>

                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                            {features
                                .filter(feature => feature.category === categoryKey)
                                .map((feature, index) => (
                                    <Link key={index} href={feature.link}>
                                        <div className={`${categoryInfo.color} border-2 rounded-lg p-4 hover:shadow-lg transition-shadow cursor-pointer h-full`}>
                                            <div className="flex items-start justify-between mb-3">
                                                <h3 className="font-semibold text-gray-900 flex items-center">
                                                    <span className="text-2xl mr-2">{feature.icon}</span>
                                                    {feature.title}
                                                </h3>
                                                {getStatusBadge(feature.status)}
                                            </div>
                                            <p className="text-sm text-gray-700 mb-3">
                                                {feature.description}
                                            </p>
                                            <div className="flex items-center text-xs text-gray-600">
                                                <svg className="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7l5 5m0 0l-5 5m5-5H6" />
                                                </svg>
                                                Click to access
                                            </div>
                                        </div>
                                    </Link>
                                ))}
                        </div>
                    </div>
                ))}

                {/* 关键流程说明 */}
                <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-6 mt-8">
                    <h3 className="text-lg font-semibold text-yellow-800 mb-4">
                        🔄 Complete BMM Workflow
                    </h3>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <div>
                            <h4 className="font-semibold text-yellow-700 mb-2">📝 Data Preparation</h4>
                            <ol className="text-sm text-yellow-700 space-y-1">
                                <li>1. Import member data via Informer or BMM migration</li>
                                <li>2. Create BMM event and configure parameters</li>
                                <li>3. Verify member information and categories</li>
                            </ol>
                        </div>
                        <div>
                            <h4 className="font-semibold text-yellow-700 mb-2">📧 Email Sending</h4>
                            <ol className="text-sm text-yellow-700 space-y-1">
                                <li>1. Send registration invitations by category</li>
                                <li>2. Members click link to access BMM registration</li>
                                <li>3. Complete information confirmation and attendance</li>
                            </ol>
                        </div>
                        <div>
                            <h4 className="font-semibold text-yellow-700 mb-2">🎫 Ticket Delivery</h4>
                            <ol className="text-sm text-yellow-700 space-y-1">
                                <li>1. Auto-send professional tickets upon attendance confirmation</li>
                                <li>2. Tickets include QR codes and event details</li>
                                <li>3. Admins can bulk resend tickets</li>
                            </ol>
                        </div>
                        <div>
                            <h4 className="font-semibold text-yellow-700 mb-2">✅ On-site Check-in</h4>
                            <ol className="text-sm text-yellow-700 space-y-1">
                                <li>1. Members check in using ticket QR codes or links</li>
                                <li>2. Real-time monitoring of check-in status</li>
                                <li>3. Generate various statistical reports</li>
                            </ol>
                        </div>
                    </div>
                </div>

                {/* 重要提醒 */}
                <div className="bg-blue-50 border border-blue-200 rounded-lg p-6 mt-6">
                    <h3 className="text-lg font-semibold text-blue-800 mb-3">
                        💡 System Usage Tips
                    </h3>
                    <ul className="text-sm text-blue-700 space-y-2">
                        <li>• <strong>SMS Sending</strong>: Configured to use Financial Form updated phone numbers for accuracy</li>
                        <li>• <strong>Email Categories</strong>: Supports filtering by registration status, region, industry, etc.</li>
                        <li>• <strong>Ticket Emails</strong>: Auto-sent on attendance confirmation, manual sending also available</li>
                        <li>• <strong>Data Import</strong>: Recommend using BMM migration to import existing members to EventMember</li>
                        <li>• <strong>Security</strong>: All admin features require login authentication, please secure credentials</li>
                    </ul>
                </div>
            </div>
        </Layout>
    );
}