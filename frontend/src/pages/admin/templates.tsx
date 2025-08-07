'use client';
import { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import Link from 'next/link';
import api from '@/services/api';

interface Template {
    id: number;
    name: string;
    type: 'email' | 'sms';
    subject?: string;
    content: string;
    isActive: boolean;
    createdAt: string;
    updatedAt: string;
}

interface DefaultEmailTemplate {
    name: string;
    subject: string;
    content: string;
}

interface DefaultSmsTemplate {
    name: string;
    content: string;
}

type DefaultTemplate = DefaultEmailTemplate | DefaultSmsTemplate;

export default function TemplatesPage() {
    const router = useRouter();
    const [isAuthorized, setIsAuthorized] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [templates, setTemplates] = useState<Template[]>([]);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [showEditModal, setShowEditModal] = useState(false);
    const [selectedTemplate, setSelectedTemplate] = useState<Template | null>(null);
    const [activeTab, setActiveTab] = useState<'email' | 'sms'>('email');

// Form state
    const [formData, setFormData] = useState({
        name: '',
        type: 'email' as 'email' | 'sms',
        subject: '',
        content: '',
        isActive: true
    });

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }
        setIsAuthorized(true);
        fetchTemplates();
    }, [router]);

    const fetchTemplates = async () => {
        try {
            const response = await api.get('/admin/templates');
            if (response.data.status === 'success') {
                setTemplates(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch templates:', error);
            toast.error('Failed to load templates');
        } finally {
            setIsLoading(false);
        }
    };

    const handleCreateTemplate = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            const response = await api.post('/admin/templates', formData);
            if (response.data.status === 'success') {
                toast.success('Template created successfully');
                setShowCreateModal(false);
                resetForm();
                fetchTemplates();
            }
        } catch (error: any) {
            console.error('Failed to create template:', error);
            toast.error('Failed to create template: ' + (error.response?.data?.message || 'Unknown error'));
        }
    };

    const handleUpdateTemplate = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedTemplate) return;

        try {
            const response = await api.put(`/admin/templates/${selectedTemplate.id}`, formData);
            if (response.data.status === 'success') {
                toast.success('Template updated successfully');
                setShowEditModal(false);
                setSelectedTemplate(null);
                resetForm();
                fetchTemplates();
            }
        } catch (error: any) {
            console.error('Failed to update template:', error);
            toast.error('Failed to update template: ' + (error.response?.data?.message || 'Unknown error'));
        }
    };

    const handleDeleteTemplate = async (templateId: number) => {
        if (!confirm('Are you sure you want to delete this template?')) {
            return;
        }

        try {
            const response = await api.delete(`/admin/templates/${templateId}`);
            if (response.data.status === 'success') {
                toast.success('Template deleted successfully');
                fetchTemplates();
            }
        } catch (error: any) {
            console.error('Failed to delete template:', error);
            toast.error('Failed to delete template: ' + (error.response?.data?.message || 'Unknown error'));
        }
    };

    const handleEditTemplate = (template: Template) => {
        setSelectedTemplate(template);
        setFormData({
            name: template.name,
            type: template.type,
            subject: template.subject || '',
            content: template.content,
            isActive: template.isActive
        });
        setShowEditModal(true);
    };

    const resetForm = () => {
        setFormData({
            name: '',
            type: 'email',
            subject: '',
            content: '',
            isActive: true
        });
    };

    const handleCloseModals = () => {
        setShowCreateModal(false);
        setShowEditModal(false);
        setSelectedTemplate(null);
        resetForm();
    };

    const filteredTemplates = templates.filter(template => template.type === activeTab);

    const getDefaultTemplates = (): DefaultTemplate[] => {
        const emailTemplates = [
            {
                name: 'Registration Invitation',
                subject: 'Pre-registration Now Open: E tū Special Conference',
                content: `Kia ora {{name}},

Pre-registration is now open for the upcoming E tū Special Conference, which will be held online on Thursday, 26 June 2025, at 7:00 PM.

Please complete the pre-registration process now using the link below:
[!!!CLICK HERE TO REGISTER NOW!!!]({{registrationLink}})

Your Member Information:
Membership Number: {{membershipNumber}}
Verification Code: {{verificationCode}}

Ngā mihi,
E tū Union`
            },
            {
                name: 'Registration Reminder',
                subject: 'Follow-Up: Confirm Your Attendance - E tū Special Conference',
                content: `Kia ora {{name}},

This is a friendly follow-up to our email last week inviting you to pre-register for the upcoming E tū Special Conference.

Please register now:
[!!!CLICK HERE TO REGISTER NOW!!!]({{registrationLink}})

Your Member Information:
Membership Number: {{membershipNumber}}
Verification Code: {{verificationCode}}

Ngā mihi,
E tū Union`
            },
            {
                name: 'Registration Confirmation',
                subject: 'Registration Confirmed - E tū Special Conference',
                content: `Dear {{name}},

Thank you for completing your pre-registration for the upcoming E tū Special Conference.

Your membership number: {{membershipNumber}}

We look forward to seeing you at the meeting.

Thank you,
E tū Union`
            }
        ];

        const smsTemplates = [
            {
                name: 'Registration Reminder SMS',
                content: `Hi {{name}}, reminder to register for E tū Special Conference on 26 June. Use membership #{{membershipNumber}} and code {{verificationCode}} at: {{registrationLink}}`
            },
            {
                name: 'Meeting Reminder SMS',
                content: `Hi {{name}}, E tū Special Conference starts in 1 hour. Join at: {{meetingLink}} Your membership: {{membershipNumber}}`
            },
            {
                name: 'Registration Confirmation SMS',
                content: `Hi {{name}}, your registration for E tū Special Conference is confirmed. Meeting details will be sent closer to the date. Thanks!`
            }
        ];

        return activeTab === 'email' ? emailTemplates : smsTemplates;
    };

    const handleUseDefaultTemplate = (defaultTemplate: any) => {
        setFormData({
            name: defaultTemplate.name,
            type: activeTab,
            subject: defaultTemplate.subject || '',
            content: defaultTemplate.content,
            isActive: true
        });
        setShowCreateModal(true);
    };

    if (isLoading) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12 text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-purple-500"></div>
                    <p className="mt-4 text-gray-700 dark:text-gray-300">Loading...</p>
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
                        <h1 className="text-3xl font-bold text-black dark:text-white">Template Management</h1>
                    </div>
                    <button
                        onClick={() => setShowCreateModal(true)}
                        className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded flex items-center"
                    >
                        <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                        </svg>
                        Create Template
                    </button>
                </div>

                {/* Tab Navigation */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md mb-6">
                    <div className="border-b border-gray-200 dark:border-gray-700">
                        <nav className="-mb-px flex">
                            <button
                                onClick={() => setActiveTab('email')}
                                className={`py-4 px-6 text-sm font-medium border-b-2 ${
                                    activeTab === 'email'
                                        ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                        : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600'
                                }`}
                            >
                                <svg className="w-4 h-4 inline mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                                </svg>
                                Email Templates
                            </button>
                            <button
                                onClick={() => setActiveTab('sms')}
                                className={`py-4 px-6 text-sm font-medium border-b-2 ${
                                    activeTab === 'sms'
                                        ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                                        : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600'
                                }`}
                            >
                                <svg className="w-4 h-4 inline mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                                </svg>
                                SMS Templates
                            </button>
                        </nav>
                    </div>
                </div>

                {/* Default Templates Section */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6 mb-6">
                    <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">Default Templates</h2>
                    <p className="text-gray-600 dark:text-gray-400 mb-4">
                        Quick start with pre-built templates for common scenarios
                    </p>
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        {getDefaultTemplates().map((template, index) => (
                            <div key={index} className="border border-gray-200 dark:border-gray-600 rounded-lg p-4 hover:shadow-md transition-shadow">
                                <h3 className="font-medium text-gray-900 dark:text-white mb-2">{template.name}</h3>
                                {'subject' in template && (
                                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">
                                        <strong>Subject:</strong> {template.subject.substring(0, 50)}...
                                    </p>
                                )}
                                <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">
                                    {template.content.substring(0, 100)}...
                                </p>
                                <button
                                    onClick={() => handleUseDefaultTemplate(template)}
                                    className="text-blue-500 hover:text-blue-600 text-sm font-medium"
                                >
                                    Use This Template
                                </button>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Templates List */}
                <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md">
                    <div className="p-6 border-b border-gray-200 dark:border-gray-700">
                        <h2 className="text-xl font-semibold text-gray-900 dark:text-white">
                            {activeTab === 'email' ? 'Email' : 'SMS'} Templates ({filteredTemplates.length})
                        </h2>
                    </div>

                    {filteredTemplates.length === 0 ? (
                        <div className="p-8 text-center">
                            <svg className="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                            </svg>
                            <p className="text-gray-600 dark:text-gray-400 mb-4">
                                No {activeTab} templates found
                            </p>
                            <button
                                onClick={() => setShowCreateModal(true)}
                                className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded"
                            >
                                Create Your First Template
                            </button>
                        </div>
                    ) : (
                        <div className="divide-y divide-gray-200 dark:divide-gray-700">
                            {filteredTemplates.map((template) => (
                                <div key={template.id} className="p-6 hover:bg-gray-50 dark:hover:bg-gray-700">
                                    <div className="flex items-start justify-between">
                                        <div className="flex-1">
                                            <div className="flex items-center mb-2">
                                                <h3 className="text-lg font-medium text-gray-900 dark:text-white mr-3">
                                                    {template.name}
                                                </h3>
                                                <span className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                                                    template.isActive
                                                        ? 'bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200'
                                                        : 'bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200'
                                                }`}>
{template.isActive ? 'Active' : 'Inactive'}
</span>
                                            </div>
                                            {template.subject && (
                                                <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">
                                                    <strong>Subject:</strong> {template.subject}
                                                </p>
                                            )}
                                            <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">
                                                {template.content.length > 200
                                                    ? template.content.substring(0, 200) + '...'
                                                    : template.content
                                                }
                                            </p>
                                            <div className="flex items-center text-xs text-gray-500 dark:text-gray-400">
                                                <span>Created: {new Date(template.createdAt).toLocaleDateString()}</span>
                                                <span className="mx-2">•</span>
                                                <span>Updated: {new Date(template.updatedAt).toLocaleDateString()}</span>
                                            </div>
                                        </div>
                                        <div className="flex items-center space-x-2 ml-4">
                                            <button
                                                onClick={() => handleEditTemplate(template)}
                                                className="text-blue-500 hover:text-blue-600 p-2"
                                                title="Edit Template"
                                            >
                                                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                                                </svg>
                                            </button>
                                            <button
                                                onClick={() => handleDeleteTemplate(template.id)}
                                                className="text-red-500 hover:text-red-600 p-2"
                                                title="Delete Template"
                                            >
                                                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                                </svg>
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Create Template Modal */}
                {showCreateModal && (
                    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                        <div className="bg-white dark:bg-gray-800 rounded-lg p-6 w-full max-w-2xl max-h-[90vh] overflow-y-auto">
                            <div className="flex justify-between items-center mb-6">
                                <h2 className="text-xl font-bold text-gray-900 dark:text-white">Create New Template</h2>
                                <button
                                    onClick={handleCloseModals}
                                    className="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
                                >
                                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                    </svg>
                                </button>
                            </div>

                            <form onSubmit={handleCreateTemplate} className="space-y-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                        Template Name
                                    </label>
                                    <input
                                        type="text"
                                        value={formData.name}
                                        onChange={(e) => setFormData({...formData, name: e.target.value})}
                                        className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2"
                                        required
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                        Template Type
                                    </label>
                                    <select
                                        value={formData.type}
                                        onChange={(e) => setFormData({...formData, type: e.target.value as 'email' | 'sms'})}
                                        className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2"
                                    >
                                        <option value="primaryEmail">Email</option>
                                        <option value="sms">SMS</option>
                                    </select>
                                </div>

                                {formData.type === 'email' && (
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                            Subject
                                        </label>
                                        <input
                                            type="text"
                                            value={formData.subject}
                                            onChange={(e) => setFormData({...formData, subject: e.target.value})}
                                            className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2"
                                        />
                                    </div>
                                )}

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                        Content
                                    </label>
                                    <textarea
                                        value={formData.content}
                                        onChange={(e) => setFormData({...formData, content: e.target.value})}
                                        rows={formData.type === 'email' ? 12 : 4}
                                        className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2"
                                        placeholder={formData.type === 'email'
                                            ? "Enter email content. Use {name}, {membershipNumber}, {verificationCode}, {registrationLink} for personalization"
                                            : "Enter SMS content. Use {name}, {membershipNumber}, {verificationCode} for personalization"
                                        }
                                        required
                                    />
                                    <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                                        Available variables: {'{'}name{'}'}, {'{'}membershipNumber{'}'}, {'{'}verificationCode{'}'}, {'{'}registrationLink{'}'}
                                    </p>
                                </div>

                                <div className="flex items-center">
                                    <input
                                        type="checkbox"
                                        id="isActive"
                                        checked={formData.isActive}
                                        onChange={(e) => setFormData({...formData, isActive: e.target.checked})}
                                        className="mr-2"
                                    />
                                    <label htmlFor="isActive" className="text-sm text-gray-700 dark:text-gray-300">
                                        Active Template
                                    </label>
                                </div>

                                <div className="flex justify-end space-x-3 pt-4">
                                    <button
                                        type="button"
                                        onClick={handleCloseModals}
                                        className="px-4 py-2 text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200"
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        type="submit"
                                        className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded"
                                    >
                                        Create Template
                                    </button>
                                </div>
                            </form>
                        </div>
                    </div>
                )}

                {/* Edit Template Modal */}
                {showEditModal && selectedTemplate && (
                    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                        <div className="bg-white dark:bg-gray-800 rounded-lg p-6 w-full max-w-2xl max-h-[90vh] overflow-y-auto">
                            <div className="flex justify-between items-center mb-6">
                                <h2 className="text-xl font-bold text-gray-900 dark:text-white">Edit Template</h2>
                                <button
                                    onClick={handleCloseModals}
                                    className="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
                                >
                                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                    </svg>
                                </button>
                            </div>

                            <form onSubmit={handleUpdateTemplate} className="space-y-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                        Template Name
                                    </label>
                                    <input
                                        type="text"
                                        value={formData.name}
                                        onChange={(e) => setFormData({...formData, name: e.target.value})}
                                        className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2"
                                        required
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                        Template Type
                                    </label>
                                    <select
                                        value={formData.type}
                                        onChange={(e) => setFormData({...formData, type: e.target.value as 'email' | 'sms'})}
                                        className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2"
                                    >
                                        <option value="primaryEmail">Email</option>
                                        <option value="sms">SMS</option>
                                    </select>
                                </div>

                                {formData.type === 'email' && (
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                            Subject
                                        </label>
                                        <input
                                            type="text"
                                            value={formData.subject}
                                            onChange={(e) => setFormData({...formData, subject: e.target.value})}
                                            className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2"
                                        />
                                    </div>
                                )}

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                        Content
                                    </label>
                                    <textarea
                                        value={formData.content}
                                        onChange={(e) => setFormData({...formData, content: e.target.value})}
                                        rows={formData.type === 'email' ? 12 : 4}
                                        className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2"
                                        required
                                    />
                                    <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                                        Available variables: {'{'}name{'}'}, {'{'}membershipNumber{'}'}, {'{'}verificationCode{'}'}, {'{'}registrationLink{'}'}
                                    </p>
                                </div>

                                <div className="flex items-center">
                                    <input
                                        type="checkbox"
                                        id="editIsActive"
                                        checked={formData.isActive}
                                        onChange={(e) => setFormData({...formData, isActive: e.target.checked})}
                                        className="mr-2"
                                    />
                                    <label htmlFor="editIsActive" className="text-sm text-gray-700 dark:text-gray-300">
                                        Active Template
                                    </label>
                                </div>

                                <div className="flex justify-end space-x-3 pt-4">
                                    <button
                                        type="button"
                                        onClick={handleCloseModals}
                                        className="px-4 py-2 text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200"
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        type="submit"
                                        className="bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded"
                                    >
                                        Update Template
                                    </button>
                                </div>
                            </form>
                        </div>
                    </div>
                )}
            </div>
        </Layout>
    );
}

