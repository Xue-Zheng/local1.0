import React, { useState } from 'react';
import api from '@/services/api';
import Layout from '@/components/common/Layout';

interface SearchResult {
    membershipNumber: string;
    name: string;
    email: string;
    mobile: string;
    region: string;
    hasEmail: boolean;
    hasMobile: boolean;
    source: string;
}

export default function QuickCommunication() {
    const [searchTerm, setSearchTerm] = useState('');
    const [searchResults, setSearchResults] = useState<SearchResult[]>([]);
    const [loading, setLoading] = useState(false);
    const [selectedMember, setSelectedMember] = useState<SearchResult | null>(null);
    const [communicationType, setCommunicationType] = useState<'email' | 'sms'>('email');
    const [emailSubject, setEmailSubject] = useState('');
    const [emailContent, setEmailContent] = useState('');
    const [smsMessage, setSmsMessage] = useState('');
    const [sending, setSending] = useState(false);
    const [sendingProgress, setSendingProgress] = useState<{
        sent: number;
        total: number;
        percentage: number;
        status: string;
    } | null>(null);
    const [message, setMessage] = useState<{type: 'success' | 'error', text: string} | null>(null);
    const [useVariableReplacement, setUseVariableReplacement] = useState(true);
    const [emailProvider, setEmailProvider] = useState<'STRATUM' | 'MAILJET'>('STRATUM');

    // Available variables for replacement
    const availableVariables = [
        { key: 'name', label: 'Full Name', description: 'Member\'s full name' },
        { key: 'firstName', label: 'First Name', description: 'Member\'s first name only' },
        { key: 'membershipNumber', label: 'Membership Number', description: 'Unique membership identifier' },
        { key: 'region', label: 'Region', description: 'Member\'s region (Northern/Central/Southern)' },
        { key: 'registrationLink', label: 'BMM Registration Link', description: 'Direct link to BMM registration for this member' },
        { key: 'preferencesResult', label: 'BMM Preferences Result', description: 'Display member\'s first stage venue and time preferences' },
        { key: 'preferredVenues', label: 'Preferred Venues', description: 'Member\'s selected venue preferences for BMM' },
        { key: 'preferredDates', label: 'Preferred Dates', description: 'Member\'s selected date preferences for BMM' },
        { key: 'preferredTimes', label: 'Preferred Times', description: 'Member\'s selected time preferences for BMM' },
        { key: 'assignedVenue', label: 'Assigned Venue', description: 'Final venue assigned for BMM attendance' },
        { key: 'assignedDateTime', label: 'Assigned Date/Time', description: 'Final date and time for BMM attendance' },
        { key: 'bmmStage', label: 'BMM Registration Stage', description: 'Current stage in BMM registration process' },
        { key: 'confirmationLink', label: 'Confirmation Link', description: 'Link to confirm BMM attendance' },
        { key: 'ticketLink', label: 'Ticket Link', description: 'Link to access BMM ticket' },
        { key: 'verificationCode', label: 'Verification Code', description: 'Member\'s verification code' },
        { key: 'email', label: 'Email Address', description: 'Member\'s primary email address' },
        { key: 'mobile', label: 'Mobile Number', description: 'Member\'s mobile phone number' }
    ];

    const insertVariable = (variableKey: string, isSubject: boolean = false) => {
        const variableText = `{{${variableKey}}}`;
        if (communicationType === 'email') {
            if (isSubject) {
                setEmailSubject(prev => prev + variableText);
            } else {
                setEmailContent(prev => prev + variableText);
            }
        } else {
            setSmsMessage(prev => prev + variableText);
        }
    };

    const handleSearch = async () => {
        if (!searchTerm.trim()) return;

        setLoading(true);
        try {
            const response = await api.post('/admin/quick-search', {
                searchTerm: searchTerm.trim()
            });

            if (response.data?.data) {
                setSearchResults(response.data.data);
                setMessage(null);
            }
        } catch (error: any) {
            console.error('Search failed:', error);
            setMessage({type: 'error', text: 'Search failed: ' + (error.response?.data?.message || error.message)});
        } finally {
            setLoading(false);
        }
    };

    const handleSend = async () => {
        if (!selectedMember) return;

        setSending(true);

        // Initialize progress for single recipient
        setSendingProgress({
            sent: 0,
            total: 1,
            percentage: 0,
            status: communicationType === 'email' ? 'Preparing to send email...' : 'Preparing to send SMS...'
        });

        try {
            if (communicationType === 'email') {
                if (!emailSubject.trim() || !emailContent.trim()) {
                    setMessage({type: 'error', text: 'Please fill in both email subject and content'});
                    setSendingProgress(null);
                    return;
                }

                if (!selectedMember.hasEmail && (!selectedMember.email || selectedMember.email.trim() === '')) {
                    setMessage({type: 'error', text: 'Selected member has no email address'});
                    setSendingProgress(null);
                    return;
                }

                // Update progress - sending
                setSendingProgress({
                    sent: 0,
                    total: 1,
                    percentage: 50,
                    status: `Sending email via ${emailProvider}...`
                });

                await api.post('/admin/quick-send-email', {
                    membershipNumber: selectedMember.membershipNumber,
                    subject: emailSubject,
                    content: emailContent,
                    useVariableReplacement: useVariableReplacement,
                    provider: emailProvider
                });

                // Complete progress
                setSendingProgress({
                    sent: 1,
                    total: 1,
                    percentage: 100,
                    status: 'Email sent successfully!'
                });

                setTimeout(() => {
                    setMessage({type: 'success', text: 'Email sent successfully!'});
                    setEmailSubject('');
                    setEmailContent('');
                    setSendingProgress(null);
                }, 1000);
            } else {
                if (!smsMessage.trim()) {
                    setMessage({type: 'error', text: 'Please enter SMS message'});
                    setSendingProgress(null);
                    return;
                }

                if (!selectedMember.hasMobile && (!selectedMember.mobile || selectedMember.mobile.trim() === '')) {
                    setMessage({type: 'error', text: 'Selected member has no mobile number'});
                    setSendingProgress(null);
                    return;
                }

                // Update progress - sending SMS
                setSendingProgress({
                    sent: 0,
                    total: 1,
                    percentage: 50,
                    status: 'Sending SMS via Stratum...'
                });

                await api.post('/admin/quick-send-sms', {
                    membershipNumber: selectedMember.membershipNumber,
                    message: smsMessage,
                    useVariableReplacement: useVariableReplacement
                });

                // Complete progress
                setSendingProgress({
                    sent: 1,
                    total: 1,
                    percentage: 100,
                    status: 'SMS sent successfully!'
                });

                setTimeout(() => {
                    setMessage({type: 'success', text: 'SMS sent successfully!'});
                    setSmsMessage('');
                    setSendingProgress(null);
                }, 1000);
            }
        } catch (error: any) {
            console.error('Send failed:', error);
            setSendingProgress({
                sent: 0,
                total: 1,
                percentage: 0,
                status: 'Error occurred while sending'
            });
            setMessage({type: 'error', text: 'Send failed: ' + (error.response?.data?.message || error.message)});
            setTimeout(() => {
                setSendingProgress(null);
            }, 2000);
        } finally {
            setSending(false);
        }
    };

    return (
        <Layout>
            <div className="min-h-screen bg-gray-50 dark:bg-gray-900 py-8">
                <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="bg-white dark:bg-gray-800 shadow rounded-lg">
                        <div className="px-4 py-5 sm:p-6">
                            <h3 className="text-lg leading-6 font-medium text-gray-900 dark:text-white mb-6">
                                Quick Member Communication
                            </h3>

                            {/* Search Section */}
                            <div className="mb-6">
                                <div className="flex gap-3">
                                    <input
                                        type="text"
                                        placeholder="Search by membership number, name, email, or mobile..."
                                        value={searchTerm}
                                        onChange={(e) => setSearchTerm(e.target.value)}
                                        onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
                                        className="flex-1 border border-gray-300 dark:border-gray-600 rounded-md px-3 py-2 bg-white dark:bg-gray-700 text-gray-900 dark:text-white"
                                    />
                                    <button
                                        onClick={handleSearch}
                                        disabled={loading || !searchTerm.trim()}
                                        className="bg-blue-600 dark:bg-blue-500 text-white px-4 py-2 rounded-md hover:bg-blue-700 dark:hover:bg-blue-600 disabled:opacity-50"
                                    >
                                        {loading ? 'Searching...' : 'Search'}
                                    </button>
                                </div>
                            </div>

                            {/* Search Results */}
                            {searchResults.length > 0 && (
                                <div className="mb-6">
                                    <h4 className="text-md font-medium text-gray-900 dark:text-white mb-3">Search Results</h4>
                                    <div className="grid gap-3">
                                        {searchResults.map((result, index) => (
                                            <div
                                                key={index}
                                                onClick={() => setSelectedMember(result)}
                                                className={`border rounded-lg p-4 cursor-pointer transition-colors dark:bg-gray-700 ${
                                                    selectedMember?.membershipNumber === result.membershipNumber
                                                        ? 'border-blue-500 bg-blue-50 dark:bg-blue-900'
                                                        : 'border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500'
                                                }`}
                                            >
                                                <div className="flex justify-between items-start">
                                                    <div>
                                                        <div className="font-medium text-gray-900 dark:text-white">
                                                            {result.name} ({result.membershipNumber})
                                                        </div>
                                                        <div className="text-sm text-gray-600 dark:text-gray-300">
                                                            {result.email && <span>📧 {result.email}</span>}
                                                            {result.email && result.mobile && <span className="mx-2">•</span>}
                                                            {result.mobile && <span>📱 {result.mobile}</span>}
                                                        </div>
                                                        <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                                                            Region: {result.region || 'N/A'} • Source: {result.source}
                                                        </div>
                                                    </div>
                                                    <div className="flex gap-1">
                                                        {result.hasEmail && (
                                                            <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800">
                                Email
                              </span>
                                                        )}
                                                        {result.hasMobile && (
                                                            <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                                SMS
                              </span>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {/* Communication Section */}
                            {selectedMember && (
                                <div className="border-t pt-6">
                                    <h4 className="text-md font-medium text-gray-900 dark:text-white mb-4">
                                        Send Message to: {selectedMember.name} ({selectedMember.membershipNumber})
                                    </h4>

                                    {/* Communication Type Selector */}
                                    <div className="mb-4">
                                        <div className="flex space-x-4">
                                            <button
                                                onClick={() => setCommunicationType('email')}
                                                disabled={!selectedMember.hasEmail && !selectedMember.email}
                                                className={`px-4 py-2 rounded-md transition-colors ${
                                                    communicationType === 'email'
                                                        ? 'bg-blue-600 text-white'
                                                        : (selectedMember.hasEmail || selectedMember.email)
                                                            ? 'bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600'
                                                            : 'bg-gray-100 dark:bg-gray-800 text-gray-400 dark:text-gray-500 cursor-not-allowed'
                                                }`}
                                            >
                                                📧 Email {!selectedMember.hasEmail && !selectedMember.email && '(N/A)'}
                                            </button>
                                            <button
                                                onClick={() => setCommunicationType('sms')}
                                                disabled={!selectedMember.hasMobile && !selectedMember.mobile}
                                                className={`px-4 py-2 rounded-md transition-colors ${
                                                    communicationType === 'sms'
                                                        ? 'bg-blue-600 text-white'
                                                        : (selectedMember.hasMobile || selectedMember.mobile)
                                                            ? 'bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600'
                                                            : 'bg-gray-100 dark:bg-gray-800 text-gray-400 dark:text-gray-500 cursor-not-allowed'
                                                }`}
                                            >
                                                📱 SMS {!selectedMember.hasMobile && !selectedMember.mobile && '(N/A)'}
                                            </button>
                                        </div>
                                    </div>

                                    {/* Variable Replacement Toggle */}
                                    <div className="mb-4">
                                        <label className="flex items-center">
                                            <input
                                                type="checkbox"
                                                checked={useVariableReplacement}
                                                onChange={(e) => setUseVariableReplacement(e.target.checked)}
                                                className="mr-2"
                                            />
                                            <span className="text-sm font-medium text-gray-700">
                                                Enable variable replacement (personalize message with member data)
                                            </span>
                                        </label>
                                    </div>

                                    {/* Available Variables */}
                                    {useVariableReplacement && (
                                        <div className="mb-4 p-4 bg-gray-50 rounded-lg">
                                            <h5 className="text-sm font-medium text-gray-700 mb-2">Available Variables:</h5>
                                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                                                {availableVariables.map((variable) => (
                                                    <div key={variable.key} className="flex items-center justify-between bg-white p-2 rounded border">
                                                        <div className="flex-1">
                                                            <div className="text-sm font-medium">{variable.label}</div>
                                                            <div className="text-xs text-gray-500">{variable.description}</div>
                                                        </div>
                                                        <button
                                                            onClick={() => insertVariable(variable.key, false)}
                                                            className="ml-2 text-xs bg-blue-100 hover:bg-blue-200 text-blue-800 px-2 py-1 rounded"
                                                        >
                                                            Insert
                                                        </button>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    )}

                                    {/* Email Form */}
                                    {communicationType === 'email' && (
                                        <div className="space-y-4">
                                            {/* Email Provider Selection */}
                                            <div className="mb-4">
                                                <label className="block text-sm font-medium text-gray-700 mb-2">Email Provider</label>
                                                <div className="flex space-x-4">
                                                    <button
                                                        onClick={() => setEmailProvider('STRATUM')}
                                                        className={`px-4 py-2 rounded transition-colors ${
                                                            emailProvider === 'STRATUM'
                                                                ? 'bg-blue-600 text-white'
                                                                : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                                                        }`}
                                                    >
                                                        Stratum (Default)
                                                    </button>
                                                    <button
                                                        onClick={() => setEmailProvider('MAILJET')}
                                                        className={`px-4 py-2 rounded transition-colors ${
                                                            emailProvider === 'MAILJET'
                                                                ? 'bg-green-600 text-white'
                                                                : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                                                        }`}
                                                    >
                                                        Mailjet
                                                    </button>
                                                </div>
                                                <p className="text-xs text-gray-500 mt-1">
                                                    {emailProvider === 'STRATUM'
                                                        ? 'Using Stratum for email delivery (existing system)'
                                                        : 'Using Mailjet for email delivery (new system)'}
                                                </p>
                                            </div>
                                            <div>
                                                <div className="flex items-center justify-between mb-1">
                                                    <label className="block text-sm font-medium text-gray-700">
                                                        Subject
                                                    </label>
                                                    {useVariableReplacement && (
                                                        <div className="flex space-x-1">
                                                            <button
                                                                onClick={() => insertVariable('firstName', true)}
                                                                className="text-xs bg-gray-100 hover:bg-gray-200 px-2 py-1 rounded"
                                                            >
                                                                +First Name
                                                            </button>
                                                            <button
                                                                onClick={() => insertVariable('name', true)}
                                                                className="text-xs bg-gray-100 hover:bg-gray-200 px-2 py-1 rounded"
                                                            >
                                                                +Full Name
                                                            </button>
                                                            <button
                                                                onClick={() => insertVariable('membershipNumber', true)}
                                                                className="text-xs bg-gray-100 hover:bg-gray-200 px-2 py-1 rounded"
                                                            >
                                                                +Member#
                                                            </button>
                                                        </div>
                                                    )}
                                                </div>
                                                <input
                                                    type="text"
                                                    value={emailSubject}
                                                    onChange={(e) => setEmailSubject(e.target.value)}
                                                    placeholder="Email subject..."
                                                    className="w-full border border-gray-300 rounded-md px-3 py-2"
                                                />
                                            </div>
                                            <div>
                                                <div className="flex items-center justify-between mb-1">
                                                    <label className="block text-sm font-medium text-gray-700">
                                                        Content
                                                    </label>
                                                    {useVariableReplacement && (
                                                        <div className="flex space-x-1">
                                                            <button
                                                                onClick={() => insertVariable('registrationLink', false)}
                                                                className="text-xs bg-green-100 hover:bg-green-200 text-green-800 px-2 py-1 rounded"
                                                            >
                                                                +BMM Link
                                                            </button>
                                                            <button
                                                                onClick={() => insertVariable('firstName', false)}
                                                                className="text-xs bg-blue-100 hover:bg-blue-200 text-blue-800 px-2 py-1 rounded"
                                                            >
                                                                +First Name
                                                            </button>
                                                            <button
                                                                onClick={() => insertVariable('name', false)}
                                                                className="text-xs bg-blue-100 hover:bg-blue-200 text-blue-800 px-2 py-1 rounded"
                                                            >
                                                                +Full Name
                                                            </button>
                                                        </div>
                                                    )}
                                                </div>
                                                <textarea
                                                    value={emailContent}
                                                    onChange={(e) => setEmailContent(e.target.value)}
                                                    placeholder="Email content..."
                                                    rows={6}
                                                    className="w-full border border-gray-300 rounded-md px-3 py-2"
                                                />
                                            </div>
                                        </div>
                                    )}

                                    {/* SMS Form */}
                                    {communicationType === 'sms' && (
                                        <div>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                                Message (max 160 characters)
                                            </label>
                                            <textarea
                                                value={smsMessage}
                                                onChange={(e) => setSmsMessage(e.target.value.slice(0, 160))}
                                                placeholder="SMS message..."
                                                rows={4}
                                                className="w-full border border-gray-300 rounded-md px-3 py-2"
                                            />
                                            <div className="text-sm text-gray-500 mt-1">
                                                {smsMessage.length}/160 characters
                                            </div>
                                        </div>
                                    )}

                                    {/* Progress Bar */}
                                    {sendingProgress && (
                                        <div className="mt-4 p-4 bg-blue-50 dark:bg-blue-900 rounded-lg">
                                            <div className="flex justify-between items-center mb-2">
                                                <h4 className="font-medium text-blue-800 dark:text-blue-200">
                                                    {communicationType === 'email' ? '📧 Email' : '📱 SMS'} Sending Progress
                                                </h4>
                                                <span className="text-sm text-blue-600 dark:text-blue-300">
                                                    {sendingProgress.sent} / {sendingProgress.total}
                                                </span>
                                            </div>

                                            {/* Progress Bar */}
                                            <div className="w-full bg-gray-200 rounded-full h-3 mb-3">
                                                <div
                                                    className="bg-gradient-to-r from-blue-500 to-green-500 h-3 rounded-full transition-all duration-500 ease-out"
                                                    style={{ width: `${sendingProgress.percentage}%` }}
                                                ></div>
                                            </div>

                                            <div className="flex justify-between items-center text-sm">
                                                <span className="text-gray-600 dark:text-gray-400">{sendingProgress.status}</span>
                                                <span className="font-medium text-blue-600 dark:text-blue-300">
                                                    {Math.round(sendingProgress.percentage)}%
                                                </span>
                                            </div>

                                            {communicationType === 'email' && emailProvider === 'MAILJET' && (
                                                <div className="mt-2 text-xs text-green-600 dark:text-green-400">
                                                    🚀 Using Mailjet for enhanced delivery
                                                </div>
                                            )}
                                            {communicationType === 'email' && emailProvider === 'STRATUM' && (
                                                <div className="mt-2 text-xs text-blue-600 dark:text-blue-400">
                                                    📨 Using Stratum for delivery
                                                </div>
                                            )}
                                            {communicationType === 'sms' && (
                                                <div className="mt-2 text-xs text-purple-600 dark:text-purple-400">
                                                    📱 Using Stratum for SMS delivery
                                                </div>
                                            )}
                                        </div>
                                    )}

                                    {/* Send Button */}
                                    <div className="mt-6">
                                        <button
                                            onClick={handleSend}
                                            disabled={sending || (communicationType === 'email' && (!emailSubject.trim() || !emailContent.trim())) || (communicationType === 'sms' && !smsMessage.trim())}
                                            className="bg-green-600 text-white px-6 py-2 rounded-md hover:bg-green-700 disabled:opacity-50 transition-colors"
                                        >
                                            {sending ? (
                                                <div className="flex items-center">
                                                    <svg className="animate-spin -ml-1 mr-3 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                                                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                                    </svg>
                                                    Sending...
                                                </div>
                                            ) : (
                                                `Send ${communicationType.toUpperCase()}`
                                            )}
                                        </button>
                                    </div>
                                </div>
                            )}

                            {/* Message Display */}
                            {message && (
                                <div className={`mt-4 p-4 rounded-md ${
                                    message.type === 'success' ? 'bg-green-50 text-green-800' : 'bg-red-50 text-red-800'
                                }`}>
                                    {message.text}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </Layout>
    );
}