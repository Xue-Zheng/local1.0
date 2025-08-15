'use client';
import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import Layout from '@/components/common/Layout';
import Link from 'next/link';
import api from '@/services/api';

interface DataCategoriesOverview {
    totalMembers: number;
    withEmail: number;
    withMobile: number;
    industryBreakdown: { [key: string]: number };
    regionBreakdown: { [key: string]: number };
    genderBreakdown: { [key: string]: number };
    ethnicBreakdown: { [key: string]: number };
    membershipTypeBreakdown: { [key: string]: number };
    topEmployers: { [key: string]: number };
    branchBreakdown: { [key: string]: number };
    subIndustryBreakdown: { [key: string]: number };
    topBargainingGroups: { [key: string]: number };
    ageGroupBreakdown: { [key: string]: number };
    dataSourceBreakdown: { [key: string]: number };
}

export default function DataCategoriesPage() {
    const router = useRouter();
    const [isAuthorized, setIsAuthorized] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [overview, setOverview] = useState<DataCategoriesOverview | null>(null);
    const [selectedCategory, setSelectedCategory] = useState<string>('industry');
    const [categoryDetails, setCategoryDetails] = useState<any>(null);
    const [loadingDetails, setLoadingDetails] = useState(false);
    const [selectedFilter, setSelectedFilter] = useState<string>('');

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }
        setIsAuthorized(true);
        fetchDataCategoriesOverview();
    }, [router]);

    const fetchDataCategoriesOverview = async () => {
        try {
            setIsLoading(true);
            const response = await api.get('/admin/data-categories/overview');
            if (response.data.status === 'success') {
                setOverview(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch data categories overview:', error);
        } finally {
            setIsLoading(false);
        }
    };

    const fetchCategoryDetails = async (categoryType: string) => {
        try {
            setLoadingDetails(true);
            const response = await api.get(`/admin/data-categories/category/${categoryType}?page=0&size=100`);
            if (response.data.status === 'success') {
                setCategoryDetails(response.data.data);
            }
        } catch (error) {
            console.error(`Failed to fetch ${categoryType} details:`, error);
        } finally {
            setLoadingDetails(false);
        }
    };

    const handleCategoryClick = (categoryType: string) => {
        setSelectedCategory(categoryType);
        fetchCategoryDetails(categoryType);
    };

    const renderCategoryChart = (title: string, data: { [key: string]: number }, icon: string, categoryType: string) => {
        const sortedData = Object.entries(data)
            .sort(([,a], [,b]) => b - a)
            .slice(0, 10); // Top 10

        const total = Object.values(data).reduce((sum, count) => sum + count, 0);

        return (
            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6 hover:shadow-lg transition-shadow cursor-pointer"
                 onClick={() => handleCategoryClick(categoryType)}>
                <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4 flex items-center justify-between">
                    <span>{icon} {title}</span>
                    <button className="text-sm text-blue-600 dark:text-blue-400 hover:text-blue-800">
                        View Details →
                    </button>
                </h3>
                <div className="space-y-3">
                    {sortedData.map(([category, count]) => {
                        const percentage = total > 0 ? (count / total * 100) : 0;
                        return (
                            <div key={category} className="flex items-center justify-between">
                                <div className="flex-1">
                                    <div className="flex items-center justify-between mb-1">
                                        <span className="text-sm font-medium text-gray-700 dark:text-gray-300 truncate">
                                            {category}
                                        </span>
                                        <span className="text-sm text-gray-500 dark:text-gray-400">
                                            {count} ({percentage.toFixed(1)}%)
                                        </span>
                                    </div>
                                    <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
                                        <div
                                            className="bg-blue-600 h-2 rounded-full transition-all duration-300"
                                            style={{ width: `${percentage}%` }}
                                        ></div>
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>
                {Object.keys(data).length > 10 && (
                    <p className="text-xs text-gray-500 dark:text-gray-400 mt-3">
                        Showing top 10 of {Object.keys(data).length} categories
                    </p>
                )}
            </div>
        );
    };

    if (isLoading) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12 text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-purple-500"></div>
                    <p className="mt-4 text-gray-700 dark:text-gray-300">Loading data categories...</p>
                </div>
            </Layout>
        );
    }

    if (!isAuthorized) {
        return null;
    }

    return (
        <Layout>
            <div className="container mx-auto px-4 py-8">
                <div className="flex justify-between items-center mb-6">
                    <div className="flex items-center">
                        <Link href="/admin/dashboard">
                            <button className="mr-4 text-gray-600 dark:text-gray-400 hover:text-black dark:hover:text-white">
                                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
                                </svg>
                            </button>
                        </Link>
                        <h1 className="text-3xl font-bold text-black dark:text-white">📊 Data Categories</h1>
                    </div>
                </div>

                {overview && (
                    <>
                        {/* Summary Cards */}
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
                            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                                <div className="flex items-center">
                                    <div className="p-3 rounded-full bg-blue-100 dark:bg-blue-900">
                                        <svg className="w-6 h-6 text-blue-600 dark:text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197m13.5-9a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0z" />
                                        </svg>
                                    </div>
                                    <div className="ml-4">
                                        <p className="text-sm font-medium text-gray-600 dark:text-gray-400">Total Members</p>
                                        <p className="text-2xl font-bold text-gray-900 dark:text-white">{overview.totalMembers.toLocaleString()}</p>
                                    </div>
                                </div>
                            </div>

                            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                                <div className="flex items-center">
                                    <div className="p-3 rounded-full bg-green-100 dark:bg-green-900">
                                        <svg className="w-6 h-6 text-green-600 dark:text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                                        </svg>
                                    </div>
                                    <div className="ml-4">
                                        <p className="text-sm font-medium text-gray-600 dark:text-gray-400">With Email</p>
                                        <p className="text-2xl font-bold text-gray-900 dark:text-white">{overview.withEmail.toLocaleString()}</p>
                                        <p className="text-xs text-gray-500 dark:text-gray-400">
                                            {((overview.withEmail / overview.totalMembers) * 100).toFixed(1)}%
                                        </p>
                                    </div>
                                </div>
                            </div>

                            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                                <div className="flex items-center">
                                    <div className="p-3 rounded-full bg-purple-100 dark:bg-purple-900">
                                        <svg className="w-6 h-6 text-purple-600 dark:text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z" />
                                        </svg>
                                    </div>
                                    <div className="ml-4">
                                        <p className="text-sm font-medium text-gray-600 dark:text-gray-400">With Mobile</p>
                                        <p className="text-2xl font-bold text-gray-900 dark:text-white">{overview.withMobile.toLocaleString()}</p>
                                        <p className="text-xs text-gray-500 dark:text-gray-400">
                                            {((overview.withMobile / overview.totalMembers) * 100).toFixed(1)}%
                                        </p>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Category Charts Grid */}
                        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                            {renderCategoryChart('Industry Classification', overview.industryBreakdown, '🏭', 'industry')}
                            {renderCategoryChart('Regional Distribution', overview.regionBreakdown, '🌍', 'region')}
                            {renderCategoryChart('Gender Distribution', overview.genderBreakdown, '👥', 'gender')}
                            {renderCategoryChart('Ethnic Groups', overview.ethnicBreakdown, '🌐', 'ethnic')}
                            {renderCategoryChart('Membership Types', overview.membershipTypeBreakdown, '💼', 'membershipType')}
                            {renderCategoryChart('Top Employers', overview.topEmployers, '🏢', 'employer')}
                            {renderCategoryChart('Branch Distribution', overview.branchBreakdown, '🌳', 'branch')}
                            {renderCategoryChart('Sub-Industry Categories', overview.subIndustryBreakdown, '🔧', 'subindustry')}
                            {renderCategoryChart('Top Bargaining Groups', overview.topBargainingGroups, '🤝', 'bargaining')}
                            {renderCategoryChart('Age Groups', overview.ageGroupBreakdown, '📊', 'age')}
                        </div>

                        {/* Category Details Modal/Panel */}
                        {categoryDetails && (
                            <div className="mt-8 bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6">
                                <div className="flex justify-between items-center mb-6">
                                    <h2 className="text-2xl font-bold text-gray-900 dark:text-white">
                                        📋 {categoryDetails.categoryName} Details
                                    </h2>
                                    <button
                                        onClick={() => setCategoryDetails(null)}
                                        className="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
                                    >
                                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                        </svg>
                                    </button>
                                </div>

                                {loadingDetails ? (
                                    <div className="text-center py-8">
                                        <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-blue-500"></div>
                                        <p className="mt-2 text-gray-600 dark:text-gray-400">Loading details...</p>
                                    </div>
                                ) : (
                                    <div>
                                        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
                                            <div className="bg-blue-50 dark:bg-blue-900/20 p-4 rounded-lg">
                                                <p className="text-sm text-blue-600 dark:text-blue-400">Total Categories</p>
                                                <p className="text-2xl font-bold text-blue-700 dark:text-blue-300">{categoryDetails.totalCategories}</p>
                                            </div>
                                            <div className="bg-green-50 dark:bg-green-900/20 p-4 rounded-lg">
                                                <p className="text-sm text-green-600 dark:text-green-400">Total Members</p>
                                                <p className="text-2xl font-bold text-green-700 dark:text-green-300">{categoryDetails.totalMembers}</p>
                                            </div>
                                            <div className="bg-purple-50 dark:bg-purple-900/20 p-4 rounded-lg">
                                                <p className="text-sm text-purple-600 dark:text-purple-400">Current Page</p>
                                                <p className="text-2xl font-bold text-purple-700 dark:text-purple-300">{categoryDetails.page + 1}</p>
                                            </div>
                                        </div>

                                        <div className="overflow-x-auto">
                                            <table className="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                                                <thead className="bg-gray-50 dark:bg-gray-700">
                                                <tr>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                                                        Category Name
                                                    </th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                                                        Member Count
                                                    </th>
                                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider">
                                                        Percentage
                                                    </th>
                                                </tr>
                                                </thead>
                                                <tbody className="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                                                {categoryDetails.data.map((item: any, index: number) => (
                                                    <tr key={index} className="hover:bg-gray-50 dark:hover:bg-gray-700">
                                                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900 dark:text-white">
                                                            {item.name}
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 dark:text-gray-400">
                                                            {item.count.toLocaleString()}
                                                        </td>
                                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 dark:text-gray-400">
                                                            {item.percentage}
                                                        </td>
                                                    </tr>
                                                ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}
                    </>
                )}
            </div>
        </Layout>
    );
}