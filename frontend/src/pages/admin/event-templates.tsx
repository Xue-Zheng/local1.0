import React, { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import Link from 'next/link';
import Layout from '@/components/common/Layout';
import api from '@/services/api';
import { toast } from 'react-toastify';

interface EventTemplate {
    id: number;
    name: string;
    eventType: string;
    description: string;
    customFields: string;
    registrationFlow: string;
    pageContent: string;
    isActive: boolean;
    createdAt: string;
    updatedAt: string;
}

const eventTypeLabels = {
    'SPECIAL_CONFERENCE': 'Special Conference',
    'SURVEY_MEETING': 'Survey Meeting',
    'BMM_VOTING': 'BMM Voting',
    'GENERAL_MEETING': 'General Meeting',
    'BALLOT_VOTING': 'Ballot Voting',
    'ANNUAL_MEETING': 'Annual Meeting',
    'WORKSHOP': 'Workshop',
    'UNION_MEETING': 'Union Meeting'
};

export default function EventTemplatesPage() {
    const router = useRouter();
    const [isAuthorized, setIsAuthorized] = useState(false);
    const [templates, setTemplates] = useState<EventTemplate[]>([]);
    const [loading, setLoading] = useState(true);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [editingTemplate, setEditingTemplate] = useState<EventTemplate | null>(null);
    const [newTemplate, setNewTemplate] = useState({
        name: '',
        eventType: 'GENERAL_MEETING',
        description: '',
        customFields: '',
        registrationFlow: '',
        pageContent: ''
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
            setLoading(true);
            const response = await api.get('/admin/event-templates');
            if (response.data.status === 'success') {
                setTemplates(response.data.data);
            }
        } catch (error) {
            console.error('Failed to fetch templates', error);
            toast.error('Failed to load templates');
        } finally {
            setLoading(false);
        }
    };

    const handleCreateTemplate = async () => {
        try {
            if (!newTemplate.name || !newTemplate.eventType) {
                toast.error('Please fill in required fields');
                return;
            }

            const response = await api.post('/admin/event-templates', newTemplate);
            if (response.data.status === 'success') {
                toast.success('Template created successfully');
                setShowCreateModal(false);
                fetchTemplates();
                resetForm();
            }
        } catch (error: any) {
            console.error('Failed to create template', error);
            toast.error('Failed to create template');
        }
    };

    const handleUpdateTemplate = async () => {
        if (!editingTemplate) return;
        try {
            const response = await api.put(`/admin/event-templates/${editingTemplate.id}`, newTemplate);
            if (response.data.status === 'success') {
                toast.success('Template updated successfully');
                setEditingTemplate(null);
                fetchTemplates();
                resetForm();
            }
        } catch (error: any) {
            console.error('Failed to update template', error);
            toast.error('Failed to update template');
        }
    };

    const handleDeleteTemplate = async (id: number) => {
        if (!confirm('Are you sure you want to delete this template?')) return;
        try {
            const response = await api.delete(`/admin/event-templates/${id}`);
            if (response.data.status === 'success') {
                toast.success('Template deleted successfully');
                fetchTemplates();
            }
        } catch (error: any) {
            console.error('Failed to delete template', error);
            toast.error('Failed to delete template');
        }
    };

    const resetForm = () => {
        setNewTemplate({
            name: '',
            eventType: 'GENERAL_MEETING',
            description: '',
            customFields: '',
            registrationFlow: '',
            pageContent: ''
        });
    };

    const startEdit = (template: EventTemplate) => {
        setEditingTemplate(template);
        setNewTemplate({
            name: template.name,
            eventType: template.eventType,
            description: template.description,
            customFields: template.customFields,
            registrationFlow: template.registrationFlow,
            pageContent: template.pageContent
        });
        setShowCreateModal(true);
    };

    const getEventTypeColor = (type: string) => {
        const colors = {
            'GENERAL_MEETING': 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
            'SPECIAL_CONFERENCE': 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200',
            'SURVEY_MEETING': 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
            'BMM_VOTING': 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
            'BALLOT_VOTING': 'bg-orange-100 text-orange-800 dark:bg-orange-900 dark:text-orange-200',
            'ANNUAL_MEETING': 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900 dark:text-indigo-200',
            'WORKSHOP': 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200',
            'UNION_MEETING': 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200'
        };
        return colors[type as keyof typeof colors] || 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200';
    };

    const getTemplateFeatures = (template: EventTemplate) => {
        const features = [];
        if (template.customFields) features.push('Custom Fields');
        if (template.registrationFlow) features.push('Custom Flow');
        if (template.pageContent) features.push('Custom Pages');
        return features.length > 0 ? features : ['Basic Template'];
    };

    if (!isAuthorized) return null;

    if (loading) {
        return (
            <Layout>
                <div className="container mx-auto px-4 py-12 text-center">
                    <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-purple-500"></div>
                    <p className="mt-4 text-gray-700 dark:text-gray-300">Loading templates...</p>
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
                            <h1 className="text-3xl font-bold text-black dark:text-white">  Event Templates</h1>
                        </div>
                        <button
                            onClick={() => setShowCreateModal(true)}
                            className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-2 rounded-lg flex items-center"
                        >
                            <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                            </svg>
                            Create Template
                        </button>
                    </div>

                    {/* Templates Grid */}
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                        {templates.map((template) => (
                            <div key={template.id} className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 hover:shadow-md transition-shadow">
                                <div className="p-6">
                                    <div className="flex justify-between items-start mb-4">
                                        <div className="flex-1">
                                            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">{template.name}</h3>
                                            <span className={`inline-block px-2 py-1 rounded-full text-xs font-medium ${getEventTypeColor(template.eventType)}`}>
{eventTypeLabels[template.eventType as keyof typeof eventTypeLabels]}
</span>
                                        </div>
                                        <div className="flex space-x-2">
                                            <button
                                                onClick={() => startEdit(template)}
                                                className="text-blue-600 dark:text-blue-400 hover:text-blue-800 dark:hover:text-blue-300"
                                            >
                                                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                                                </svg>
                                            </button>
                                            <button
                                                onClick={() => handleDeleteTemplate(template.id)}
                                                className="text-red-600 dark:text-red-400 hover:text-red-800 dark:hover:text-red-300"
                                            >
                                                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                                </svg>
                                            </button>
                                        </div>
                                    </div>

                                    {template.description && (
                                        <p className="text-gray-600 dark:text-gray-400 text-sm mb-4">{template.description}</p>
                                    )}

                                    <div className="flex flex-wrap gap-2 mb-4">
                                        {getTemplateFeatures(template).map((feature, index) => (
                                            <span key={index} className="bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 px-2 py-1 rounded text-xs">
{feature}
</span>
                                        ))}
                                    </div>

                                    <div className="flex justify-between items-center text-xs text-gray-500 dark:text-gray-400">
                                        <span>Created: {new Date(template.createdAt).toLocaleDateString()}</span>
                                        <span className={`px-2 py-1 rounded ${template.isActive ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'}`}>
{template.isActive ? 'Active' : 'Inactive'}
</span>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>

                    {templates.length === 0 && (
                        <div className="text-center py-12">
                            <div className="text-gray-400 dark:text-gray-500 text-6xl mb-4"> </div>
                            <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">No templates found</h3>
                            <p className="text-gray-500 dark:text-gray-400 mb-6">Create your first event template to get started</p>
                            <button
                                onClick={() => setShowCreateModal(true)}
                                className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-2 rounded-lg"
                            >
                                Create Template
                            </button>
                        </div>
                    )}
                </div>

                {/* Create/Edit Modal */}
                {showCreateModal && (
                    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
                        <div className="bg-white dark:bg-gray-800 rounded-lg max-w-2xl w-full max-h-[90vh] overflow-y-auto">
                            <div className="p-6">
                                <div className="flex justify-between items-center mb-6">
                                    <h2 className="text-xl font-bold text-gray-900 dark:text-white">
                                        {editingTemplate ? 'Edit Template' : 'Create New Template'}
                                    </h2>
                                    <button
                                        onClick={() => {
                                            setShowCreateModal(false);
                                            setEditingTemplate(null);
                                            resetForm();
                                        }}
                                        className="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300"
                                    >
                                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                        </svg>
                                    </button>
                                </div>

                                <div className="space-y-4">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                            Template Name *
                                        </label>
                                        <input
                                            type="text"
                                            value={newTemplate.name}
                                            onChange={(e) => setNewTemplate({...newTemplate, name: e.target.value})}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                            placeholder="Enter template name"
                                        />
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                            Event Type *
                                        </label>
                                        <select
                                            value={newTemplate.eventType}
                                            onChange={(e) => setNewTemplate({...newTemplate, eventType: e.target.value})}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                        >
                                            {Object.entries(eventTypeLabels).map(([value, label]) => (
                                                <option key={value} value={value}>{label}</option>
                                            ))}
                                        </select>
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                            Description
                                        </label>
                                        <textarea
                                            value={newTemplate.description}
                                            onChange={(e) => setNewTemplate({...newTemplate, description: e.target.value})}
                                            rows={3}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white"
                                            placeholder="Enter template description"
                                        />
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                            Custom Fields (JSON)
                                        </label>
                                        <textarea
                                            value={newTemplate.customFields}
                                            onChange={(e) => setNewTemplate({...newTemplate, customFields: e.target.value})}
                                            rows={4}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white font-mono text-sm"
                                            placeholder='{"field1": "value1", "field2": "value2"}'
                                        />
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                            Registration Flow (JSON)
                                        </label>
                                        <textarea
                                            value={newTemplate.registrationFlow}
                                            onChange={(e) => setNewTemplate({...newTemplate, registrationFlow: e.target.value})}
                                            rows={4}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white font-mono text-sm"
                                            placeholder='[{"step": 1, "title": "Registration"}]'
                                        />
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                            Page Content (HTML)
                                        </label>
                                        <textarea
                                            value={newTemplate.pageContent}
                                            onChange={(e) => setNewTemplate({...newTemplate, pageContent: e.target.value})}
                                            rows={6}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-700 dark:text-white font-mono text-sm"
                                            placeholder="<div>Custom HTML content</div>"
                                        />
                                    </div>
                                </div>

                                <div className="flex justify-end space-x-3 mt-6">
                                    <button
                                        onClick={() => {
                                            setShowCreateModal(false);
                                            setEditingTemplate(null);
                                            resetForm();
                                        }}
                                        className="px-4 py-2 text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 rounded-md"
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        onClick={editingTemplate ? handleUpdateTemplate : handleCreateTemplate}
                                        className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-md"
                                    >
                                        {editingTemplate ? 'Update Template' : 'Create Template'}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </Layout>
    );
}

