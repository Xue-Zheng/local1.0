'use client';
import { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import { toast } from 'react-toastify';
import Layout from '@/components/common/Layout';
import Link from 'next/link';
import api from '@/services/api';

export default function ImportPage() {
    const router = useRouter();
    const [isAuthorized, setIsAuthorized] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [file, setFile] = useState<File | null>(null);
    const [uploadProgress, setUploadProgress] = useState(0);
    const [isUploading, setIsUploading] = useState(false);
    const [uploadComplete, setUploadComplete] = useState(false);
    const [importResults, setImportResults] = useState<{
        total: number;
        success: number;
        failed: number;
        errors: string[];
    } | null>(null);
    const [csvData, setCsvData] = useState<any[]>([]);
    const [csvHeaders, setCsvHeaders] = useState<string[]>([]);
    const [columnMapping, setColumnMapping] = useState<{[key: string]: string}>({});
    const [showMappingStep, setShowMappingStep] = useState(false);
    const [allowUpdates, setAllowUpdates] = useState(true);
    const [isEmergencyMode, setIsEmergencyMode] = useState(false);

    const [importType, setImportType] = useState('email_members');
    const [importMethod, setImportMethod] = useState('token');
    const [informerToken, setInformerToken] = useState('');

    useEffect(() => {
        const token = localStorage.getItem('adminToken');
        if (!token) {
            router.push('/admin/login');
            return;
        }
        setIsAuthorized(true);
        setIsLoading(false);
    }, [router]);

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files.length > 0) {
            const selectedFile = e.target.files[0];
            if (selectedFile.type === 'text/csv' || selectedFile.name.endsWith('.csv')) {
                setFile(selectedFile);
// Parse CSV to analyze headers and show mapping step
                parseCSVHeaders(selectedFile);
            } else {
                toast.error('Please select a CSV file');
                e.target.value = '';
            }
        }
    };

    const parseCSVHeaders = (file: File) => {
        const reader = new FileReader();
        reader.onload = (e) => {
            const text = e.target?.result as string;
            const lines = text.split('\n').filter(line => line.trim());
            if (lines.length > 0) {
// Get headers from first line
                const headers = lines[0].split(',').map(h => h.trim().replace(/^"|"$/g, ''));
                setCsvHeaders(headers);

// Parse a few sample rows for preview
                const sampleData = lines.slice(1, 4).map(line => {
                    const values = line.split(',').map(v => v.trim().replace(/^"|"$/g, ''));
                    const row: any = {};
                    headers.forEach((header, index) => {
                        row[header] = values[index] || '';
                    });
                    return row;
                }).filter(row => Object.values(row).some(val => val !== ''));

                setCsvData(sampleData);
                setShowMappingStep(true);

// Auto-map common column names
                autoMapColumns(headers);
            }
        };
        reader.readAsText(file);
    };

    const autoMapColumns = (headers: string[]) => {
        const mapping: {[key: string]: string} = {};

        headers.forEach(header => {
            const normalizedHeader = header.toLowerCase().trim();

// Auto-map common variations
            if (normalizedHeader.includes('name') && !normalizedHeader.includes('first') && !normalizedHeader.includes('last') && !normalizedHeader.includes('middle')) {
                mapping['name'] = header;
            } else if (normalizedHeader.includes('email') || normalizedHeader.includes('e-mail') || normalizedHeader.includes('mail')) {
                mapping['email'] = header;
            } else if ((normalizedHeader.includes('member') && normalizedHeader.includes('number')) || normalizedHeader === 'member number') {
                mapping['membership_number'] = header;
            } else if (normalizedHeader.includes('first') && normalizedHeader.includes('name')) {
                mapping['firstName'] = header;
            } else if (normalizedHeader.includes('last') && normalizedHeader.includes('name') || normalizedHeader.includes('surname')) {
                mapping['lastName'] = header;
            } else if (normalizedHeader.includes('phone') || normalizedHeader.includes('mobile') || normalizedHeader.includes('cell')) {
                mapping['phone_mobile'] = header;
            } else if (normalizedHeader.includes('address')) {
                mapping['address'] = header;
            } else if (normalizedHeader.includes('dob') || normalizedHeader.includes('birth') || normalizedHeader.includes('birthdate')) {
                mapping['dob'] = header;
            } else if (normalizedHeader.includes('employer')) {
                mapping['employer'] = header;
            } else if (normalizedHeader.includes('department')) {
                mapping['department'] = header;
            } else if (normalizedHeader.includes('job') && normalizedHeader.includes('title')) {
                mapping['job_title'] = header;
            } else if (normalizedHeader.includes('location')) {
                mapping['location'] = header;
            }
        });

        setColumnMapping(mapping);
    };

    const getRequiredFields = () => {
        return ['name', 'email', 'membership_number'];
    };

    const getOptionalFields = () => {
        return ['firstName', 'lastName', 'dob', 'address', 'phone_mobile', 'phone_home', 'phone_work', 'employer', 'department', 'job_title', 'location', 'payroll_number', 'site_number', 'employment_status'];
    };

    const handlePrepareForInformer = async () => {
        try {
            setIsUploading(true);
            const response = await api.post('/admin/cleanup/data-sources');
            if (response.data.status === 'success') {
                toast.success('Members prepared for Informer import successfully');
            } else {
                toast.error('Failed to prepare members');
            }
        } catch (error: any) {
            console.error('Prepare for Informer failed:', error);
            toast.error('Failed to prepare members: ' + (error.message || 'Unknown error'));
        } finally {
            setIsUploading(false);
        }
    };

    const handleUpload = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!file) {
            toast.error('Please select a file to upload');
            return;
        }

// Validate required field mappings
        const requiredFields = getRequiredFields();
        const missingMappings = requiredFields.filter(field => !columnMapping[field]);
        if (missingMappings.length > 0) {
            toast.error(`Please map required fields: ${missingMappings.join(', ')}`);
            return;
        }

        setIsUploading(true);
        setUploadProgress(0);

        const formData = new FormData();
        formData.append('file', file);

// Add column mapping information
        formData.append('columnMapping', JSON.stringify(columnMapping));
        formData.append('importType', importType);
        formData.append('allowUpdates', allowUpdates.toString());
        formData.append('isEmergencyMode', isEmergencyMode.toString());

        try {
            const importEndpoint = importType === 'special'
                ? '/admin/import/members-with-details'
                : '/admin/import/members';

            console.log('Uploading to endpoint:', importEndpoint);
            console.log('File name:', file.name);
            console.log('File size:', file.size);

            const response = await api.post(importEndpoint, formData, {
                headers: {
                    'Content-Type': 'multipart/form-data',
                },
                onUploadProgress: (progressEvent) => {
                    if (progressEvent.total) {
                        const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
                        setUploadProgress(percentCompleted);
                    }
                },
            });

            console.log('Response received:', response);

            if (response.data.status === 'success') {
                setUploadComplete(true);
                setImportResults(response.data.data);
                toast.success(`Import completed! ${response.data.data.success} successful, ${response.data.data.failed} failed`);
            } else {
                throw new Error(response.data.message || 'Import failed');
            }
        } catch (error: any) {
            console.error('Upload failed:', error);
            toast.error('Upload failed: ' + (error.response?.data?.message || error.message || 'Unknown error'));
        } finally {
            setIsUploading(false);
        }
    };

    const handleTokenImport = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!informerToken.trim()) {
            toast.error('Please enter an Informer token');
            return;
        }

        setIsUploading(true);
        setUploadProgress(0);

        try {
            const response = await api.post('/admin/import/informer', {
                token: informerToken,
                importType: importType
            });

            if (response.data.status === 'success') {
                setUploadComplete(true);
                setImportResults(response.data.data);
                toast.success(`Import completed! ${response.data.data.success} successful, ${response.data.data.failed} failed`);
            } else {
                throw new Error(response.data.message || 'Import failed');
            }
        } catch (error: any) {
            console.error('Token import failed:', error);
            toast.error('Token import failed: ' + (error.response?.data?.message || error.message || 'Unknown error'));
        } finally {
            setIsUploading(false);
        }
    };

    const handleReset = () => {
        setFile(null);
        setUploadProgress(0);
        setIsUploading(false);
        setUploadComplete(false);
        setImportResults(null);
        setCsvData([]);
        setCsvHeaders([]);
        setColumnMapping({});
        setShowMappingStep(false);
        setInformerToken('');
    };

    const getDataSourceTitle = (type: string) => {
        switch (type) {
            case 'email_members': return 'Email Members';
            case 'special': return 'Members';
            default: return 'Members';
        }
    };

    const getDataSourceDescription = (type: string) => {
        switch (type) {
            case 'email_members': return 'Import members with email addresses for notifications';
            case 'special': return 'Import members from CSV file';
            default: return 'Import general member data';
        }
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
                        <h1 className="text-3xl font-bold text-black dark:text-white">Data Import</h1>
                    </div>
                </div>

                {!uploadComplete ? (
                    <div className="space-y-6">
                        {/* Import Method Selection */}
                        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                            <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">Import Method</h2>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <label className={`flex items-center p-4 border-2 rounded-lg cursor-pointer transition-colors ${
                                    importMethod === 'file'
                                        ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                        : 'border-gray-300 dark:border-gray-600 hover:border-gray-400 dark:hover:border-gray-500'
                                }`}>
                                    <input
                                        type="radio"
                                        name="importMethod"
                                        value="file"
                                        checked={importMethod === 'file'}
                                        onChange={(e) => setImportMethod(e.target.value)}
                                        className="mr-3"
                                    />
                                    <div>
                                        <h3 className="font-medium text-gray-900 dark:text-white">CSV File Upload</h3>
                                        <p className="text-sm text-gray-600 dark:text-gray-400">Upload a CSV file with member data</p>
                                    </div>
                                </label>

                                <label className={`flex items-center p-4 border-2 rounded-lg cursor-pointer transition-colors ${
                                    importMethod === 'token'
                                        ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                        : 'border-gray-300 dark:border-gray-600 hover:border-gray-400 dark:hover:border-gray-500'
                                }`}>
                                    <input
                                        type="radio"
                                        name="importMethod"
                                        value="token"
                                        checked={importMethod === 'token'}
                                        onChange={(e) => setImportMethod(e.target.value)}
                                        className="mr-3"
                                    />
                                    <div>
                                        <h3 className="font-medium text-gray-900 dark:text-white">Informer Token</h3>
                                        <p className="text-sm text-gray-600 dark:text-gray-400">Import using Informer system token</p>
                                    </div>
                                </label>
                            </div>
                        </div>

                        {/* Data Source Selection */}
                        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                            <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">Data Source</h2>
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <label className={`flex items-center p-4 border-2 rounded-lg cursor-pointer transition-colors ${
                                    importType === 'email_members'
                                        ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                        : 'border-gray-300 dark:border-gray-600 hover:border-gray-400 dark:hover:border-gray-500'
                                }`}>
                                    <input
                                        type="radio"
                                        name="importType"
                                        value="email_members"
                                        checked={importType === 'email_members'}
                                        onChange={(e) => setImportType(e.target.value)}
                                        className="mr-3"
                                    />
                                    <div>
                                        <h3 className="font-medium text-gray-900 dark:text-white">{getDataSourceTitle('email_members')}</h3>
                                        <p className="text-sm text-gray-600 dark:text-gray-400">{getDataSourceDescription('email_members')}</p>
                                    </div>
                                </label>

                                <label className={`flex items-center p-4 border-2 rounded-lg cursor-pointer transition-colors ${
                                    importType === 'special'
                                        ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                        : 'border-gray-300 dark:border-gray-600 hover:border-gray-400 dark:hover:border-gray-500'
                                }`}>
                                    <input
                                        type="radio"
                                        name="importType"
                                        value="special"
                                        checked={importType === 'special'}
                                        onChange={(e) => setImportType(e.target.value)}
                                        className="mr-3"
                                    />
                                    <div>
                                        <h3 className="font-medium text-gray-900 dark:text-white">{getDataSourceTitle('special')}</h3>
                                        <p className="text-sm text-gray-600 dark:text-gray-400">{getDataSourceDescription('special')}</p>
                                    </div>
                                </label>
                            </div>
                        </div>

                        {/* Import Options */}
                        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                            <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">Import Options</h2>
                            <div className="space-y-4">
                                <label className="flex items-center">
                                    <input
                                        type="checkbox"
                                        checked={allowUpdates}
                                        onChange={(e) => setAllowUpdates(e.target.checked)}
                                        className="mr-3"
                                    />
                                    <div>
                                        <span className="font-medium text-gray-900 dark:text-white">Allow Updates</span>
                                        <p className="text-sm text-gray-600 dark:text-gray-400">Update existing member records if they already exist</p>
                                    </div>
                                </label>

                                <label className="flex items-center">
                                    <input
                                        type="checkbox"
                                        checked={isEmergencyMode}
                                        onChange={(e) => setIsEmergencyMode(e.target.checked)}
                                        className="mr-3"
                                    />
                                    <div>
                                        <span className="font-medium text-gray-900 dark:text-white">Emergency Mode</span>
                                        <p className="text-sm text-gray-600 dark:text-gray-400">Skip validation checks for urgent imports</p>
                                    </div>
                                </label>
                            </div>
                        </div>

                        {/* File Upload Section */}
                        {importMethod === 'file' && (
                            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                                <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">File Upload</h2>
                                <form onSubmit={handleUpload} className="space-y-6">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                            Select CSV File
                                        </label>
                                        <input
                                            type="file"
                                            accept=".csv"
                                            onChange={handleFileChange}
                                            className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2"
                                            required
                                        />
                                        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                                            Please upload a CSV file with member data
                                        </p>
                                    </div>

                                    {file && (
                                        <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-4">
                                            <h3 className="font-medium text-gray-900 dark:text-white mb-2">Selected File</h3>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">
                                                <strong>Name:</strong> {file.name}
                                            </p>
                                            <p className="text-sm text-gray-600 dark:text-gray-400">
                                                <strong>Size:</strong> {(file.size / 1024).toFixed(2)} KB
                                            </p>
                                        </div>
                                    )}

                                    {/* Column Mapping */}
                                    {showMappingStep && (
                                        <div className="bg-gray-50 dark:bg-gray-700 rounded-lg p-6">
                                            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Column Mapping</h3>
                                            <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                                                Map your CSV columns to the required fields. Required fields are marked with *
                                            </p>

                                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
                                                {/* Required Fields */}
                                                <div>
                                                    <h4 className="font-medium text-gray-900 dark:text-white mb-3">Required Fields *</h4>
                                                    {getRequiredFields().map(field => (
                                                        <div key={field} className="mb-3">
                                                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                                                                {field.replace('_', ' ').toUpperCase()} *
                                                            </label>
                                                            <select
                                                                value={columnMapping[field] || ''}
                                                                onChange={(e) => setColumnMapping({...columnMapping, [field]: e.target.value})}
                                                                className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2"
                                                                required
                                                            >
                                                                <option value="">Select column...</option>
                                                                {csvHeaders.map(header => (
                                                                    <option key={header} value={header}>{header}</option>
                                                                ))}
                                                            </select>
                                                        </div>
                                                    ))}
                                                </div>

                                                {/* Optional Fields */}
                                                <div>
                                                    <h4 className="font-medium text-gray-900 dark:text-white mb-3">Optional Fields</h4>
                                                    {getOptionalFields().slice(0, 6).map(field => (
                                                        <div key={field} className="mb-3">
                                                            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                                                                {field.replace('_', ' ').toUpperCase()}
                                                            </label>
                                                            <select
                                                                value={columnMapping[field] || ''}
                                                                onChange={(e) => setColumnMapping({...columnMapping, [field]: e.target.value})}
                                                                className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2"
                                                            >
                                                                <option value="">Select column...</option>
                                                                {csvHeaders.map(header => (
                                                                    <option key={header} value={header}>{header}</option>
                                                                ))}
                                                            </select>
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>

                                            {/* Preview Data */}
                                            {csvData.length > 0 && (
                                                <div>
                                                    <h4 className="font-medium text-gray-900 dark:text-white mb-3">Data Preview</h4>
                                                    <div className="overflow-x-auto">
                                                        <table className="min-w-full border border-gray-300 dark:border-gray-600">
                                                            <thead className="bg-gray-100 dark:bg-gray-600">
                                                            <tr>
                                                                {csvHeaders.map(header => (
                                                                    <th key={header} className="px-4 py-2 text-left text-sm font-medium text-gray-700 dark:text-gray-300 border-b border-gray-300 dark:border-gray-600">
                                                                        {header}
                                                                    </th>
                                                                ))}
                                                            </tr>
                                                            </thead>
                                                            <tbody className="bg-white dark:bg-gray-800">
                                                            {csvData.map((row, index) => (
                                                                <tr key={index}>
                                                                    {csvHeaders.map(header => (
                                                                        <td key={header} className="px-4 py-2 text-sm text-gray-900 dark:text-gray-300 border-b border-gray-200 dark:border-gray-700">
                                                                            {row[header]}
                                                                        </td>
                                                                    ))}
                                                                </tr>
                                                            ))}
                                                            </tbody>
                                                        </table>
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                    )}

                                    {/* Upload Progress */}
                                    {isUploading && (
                                        <div className="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4">
                                            <div className="flex items-center mb-2">
                                                <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-blue-500 mr-2"></div>
                                                <span className="text-sm font-medium text-blue-700 dark:text-blue-300">
Uploading... {uploadProgress}%
</span>
                                            </div>
                                            <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
                                                <div
                                                    className="bg-blue-500 h-2 rounded-full transition-all duration-300"
                                                    style={{ width: `${uploadProgress}%` }}
                                                ></div>
                                            </div>
                                        </div>
                                    )}

                                    <div className="flex justify-end space-x-4">
                                        <button
                                            type="button"
                                            onClick={handleReset}
                                            className="px-4 py-2 text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200"
                                        >
                                            Reset
                                        </button>
                                        <button
                                            type="submit"
                                            disabled={isUploading || !file || (showMappingStep && getRequiredFields().some(field => !columnMapping[field]))}
                                            className="bg-blue-500 hover:bg-blue-600 disabled:bg-gray-400 text-white px-6 py-2 rounded"
                                        >
                                            {isUploading ? 'Uploading...' : 'Import Data'}
                                        </button>
                                    </div>
                                </form>
                            </div>
                        )}

                        {/* Token Import Section */}
                        {importMethod === 'token' && (
                            <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                                <h2 className="text-xl font-semibold text-gray-900 dark:text-white mb-4">Informer Token Import</h2>
                                <form onSubmit={handleTokenImport} className="space-y-6">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                                            Informer Token
                                        </label>
                                        <input
                                            type="text"
                                            value={informerToken}
                                            onChange={(e) => setInformerToken(e.target.value)}
                                            className="w-full border border-gray-300 dark:border-gray-600 dark:bg-gray-700 dark:text-white rounded px-3 py-2"
                                            placeholder="Enter your Informer system token"
                                            required
                                        />
                                        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                                            This token will be used to fetch data from the Informer system
                                        </p>
                                    </div>

                                    <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-4">
                                        <div className="flex">
                                            <svg className="w-5 h-5 text-yellow-400 mr-2 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                                            </svg>
                                            <div>
                                                <h3 className="text-sm font-medium text-yellow-800 dark:text-yellow-200">
                                                    Prepare Data Sources
                                                </h3>
                                                <p className="text-sm text-yellow-700 dark:text-yellow-300 mt-1">
                                                    Before importing, you may need to prepare the data sources in the Informer system.
                                                </p>
                                                <button
                                                    type="button"
                                                    onClick={handlePrepareForInformer}
                                                    disabled={isUploading}
                                                    className="mt-2 bg-yellow-500 hover:bg-yellow-600 disabled:bg-gray-400 text-white px-4 py-2 rounded text-sm"
                                                >
                                                    Prepare Data Sources
                                                </button>
                                            </div>
                                        </div>
                                    </div>

                                    {/* Upload Progress */}
                                    {isUploading && (
                                        <div className="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4">
                                            <div className="flex items-center">
                                                <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-blue-500 mr-2"></div>
                                                <span className="text-sm font-medium text-blue-700 dark:text-blue-300">
Importing data from Informer...
</span>
                                            </div>
                                        </div>
                                    )}

                                    <div className="flex justify-end space-x-4">
                                        <button
                                            type="button"
                                            onClick={handleReset}
                                            className="px-4 py-2 text-gray-600 dark:text-gray-400 hover:text-gray-800 dark:hover:text-gray-200"
                                        >
                                            Reset
                                        </button>
                                        <button
                                            type="submit"
                                            disabled={isUploading || !informerToken.trim()}
                                            className="bg-blue-500 hover:bg-blue-600 disabled:bg-gray-400 text-white px-6 py-2 rounded"
                                        >
                                            {isUploading ? 'Importing...' : 'Import from Informer'}
                                        </button>
                                    </div>
                                </form>
                            </div>
                        )}
                    </div>
                ) : (
                    /* Import Results */
                    <div className="bg-white dark:bg-gray-800 rounded-lg shadow-md p-6">
                        <div className="text-center mb-6">
                            <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-green-100 dark:bg-green-900/20 text-green-500 dark:text-green-400 mb-4">
                                <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                                </svg>
                            </div>
                            <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">Import Complete</h2>
                            <p className="text-gray-600 dark:text-gray-400">
                                Your data has been processed successfully
                            </p>
                        </div>

                        {importResults && (
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
                                <div className="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-6 text-center">
                                    <p className="text-3xl font-bold text-blue-600 dark:text-blue-400">{importResults.total}</p>
                                    <p className="text-sm text-blue-800 dark:text-blue-300">Total Records</p>
                                </div>
                                <div className="bg-green-50 dark:bg-green-900/20 rounded-lg p-6 text-center">
                                    <p className="text-3xl font-bold text-green-600 dark:text-green-400">{importResults.success}</p>
                                    <p className="text-sm text-green-800 dark:text-green-300">Successfully Imported</p>
                                </div>
                                <div className="bg-red-50 dark:bg-red-900/20 rounded-lg p-6 text-center">
                                    <p className="text-3xl font-bold text-red-600 dark:text-red-400">{importResults.failed}</p>
                                    <p className="text-sm text-red-800 dark:text-red-300">Failed</p>
                                </div>
                            </div>
                        )}

                        {importResults && importResults.errors && importResults.errors.length > 0 && (
                            <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4 mb-6">
                                <h3 className="text-lg font-medium text-red-800 dark:text-red-200 mb-2">Import Errors</h3>
                                <ul className="list-disc list-inside text-sm text-red-700 dark:text-red-300 space-y-1">
                                    {importResults.errors.map((error, index) => (
                                        <li key={index}>{error}</li>
                                    ))}
                                </ul>
                            </div>
                        )}

                        <div className="flex justify-center space-x-4">
                            <button
                                onClick={handleReset}
                                className="bg-gray-500 hover:bg-gray-600 text-white px-6 py-2 rounded"
                            >
                                Import More Data
                            </button>
                            <Link href="/admin/members">
                                <button className="bg-blue-500 hover:bg-blue-600 text-white px-6 py-2 rounded">
                                    View Members
                                </button>
                            </Link>
                        </div>
                    </div>
                )}
            </div>
        </Layout>
    );
}

