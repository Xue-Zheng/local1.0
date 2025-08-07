import React, { useState, useEffect } from 'react';
import api from '@/services/api';
import { toast } from 'react-hot-toast';

interface RegionMember {
    id: number;
    membershipNumber: string;
    name: string;
    primaryEmail: string;
    telephoneMobile: string;
    regionDesc: string;
    workplace: string;
    employer: string;
    hasRegistered: boolean;
    isAttending: boolean;
    isSpecialVote: boolean;
    absenceReason: string;
    emailSent: boolean;
    smsSent: boolean;
    hasValidEmail: boolean;
    hasValidMobile: boolean;
    contactMethod: string;
    formSubmissionTime: string;
    createdAt: string;
}

interface RegionMembersListProps {
    region: string;
    onClose: () => void;
}

export default function RegionMembersList({ region, onClose }: RegionMembersListProps) {
    const [loading, setLoading] = useState(true);
    const [members, setMembers] = useState<RegionMember[]>([]);
    const [selectedStatus, setSelectedStatus] = useState<string>('');
    const [currentPage, setCurrentPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [selectedMember, setSelectedMember] = useState<any>(null);
    const [showMemberDetails, setShowMemberDetails] = useState(false);

    const statusOptions = [
        { value: '', label: 'All Members' },
        { value: 'registered', label: 'Registered' },
        { value: 'not_registered', label: 'Not Registered' },
        { value: 'attending', label: 'Attending' },
        { value: 'not_attending', label: 'Not Attending' },
        { value: 'special_vote', label: 'Special Vote Applied' },
        { value: 'sms_only', label: 'SMS Only (No Email)' }
    ];

    const fetchMembers = async (status: string = '', page: number = 0) => {
        setLoading(true);
        try {
            let url = `/admin/bmm/region/${encodeURIComponent(region)}/members?page=${page}&size=20`;
            if (status) {
                url += `&status=${status}`;
            }

            const response = await api.get(url);
            if (response.data.status === 'success') {
                setMembers(response.data.data.members);
                setTotalPages(response.data.data.totalPages);
                setCurrentPage(page);
            }
        } catch (error) {
            console.error('Failed to fetch members:', error);
            toast.error('Failed to load members');
        } finally {
            setLoading(false);
        }
    };

    const fetchMemberPreferences = async (membershipNumber: string) => {
        try {
            const response = await api.get(`/admin/bmm/member/${membershipNumber}/preferences`);
            if (response.data.status === 'success') {
                setSelectedMember(response.data.data);
                setShowMemberDetails(true);
            }
        } catch (error) {
            console.error('Failed to fetch member preferences:', error);
            toast.error('Failed to load member details');
        }
    };

    useEffect(() => {
        fetchMembers(selectedStatus, 0);
    }, [region, selectedStatus]);

    const getContactBadge = (member: RegionMember) => {
        switch (member.contactMethod) {
            case 'both':
                return <span className="px-2 py-1 bg-green-100 text-green-800 text-xs rounded">📧📱 Both</span>;
            case 'email_only':
                return <span className="px-2 py-1 bg-blue-100 text-blue-800 text-xs rounded">📧 Email Only</span>;
            case 'sms_only':
                return <span className="px-2 py-1 bg-orange-100 text-orange-800 text-xs rounded">📱 SMS Only</span>;
            default:
                return <span className="px-2 py-1 bg-red-100 text-red-800 text-xs rounded">❌ No Contact</span>;
        }
    };

    const getStatusBadge = (member: RegionMember) => {
        if (!member.hasRegistered) {
            return <span className="px-2 py-1 bg-gray-100 text-gray-800 text-xs rounded">⏳ Not Registered</span>;
        }
        if (member.isAttending) {
            return <span className="px-2 py-1 bg-green-100 text-green-800 text-xs rounded">✅ Attending</span>;
        }
        if (member.isAttending === false) {
            if (member.isSpecialVote) {
                return <span className="px-2 py-1 bg-purple-100 text-purple-800 text-xs rounded">🗳️ Special Vote</span>;
            }
            return <span className="px-2 py-1 bg-red-100 text-red-800 text-xs rounded">❌ Not Attending</span>;
        }
        return <span className="px-2 py-1 bg-yellow-100 text-yellow-800 text-xs rounded">🔄 Registered</span>;
    };

    return (
        <div className="bg-white rounded-lg shadow-md">
            <div className="p-6 border-b">
                <div className="flex justify-between items-center">
                    <h2 className="text-xl font-bold text-gray-900">
                        📋 {region} Members
                    </h2>
                    <div className="flex space-x-3">
                        <select
                            value={selectedStatus}
                            onChange={(e) => setSelectedStatus(e.target.value)}
                            className="border rounded-lg px-3 py-2"
                        >
                            {statusOptions.map(option => (
                                <option key={option.value} value={option.value}>
                                    {option.label}
                                </option>
                            ))}
                        </select>
                        <button
                            onClick={onClose}
                            className="text-gray-600 hover:text-gray-800 px-3 py-2"
                        >
                            ✕ Close
                        </button>
                    </div>
                </div>
            </div>

            <div className="p-6">
                {loading ? (
                    <div className="text-center py-8">
                        <div className="inline-block animate-spin rounded-full h-8 w-8 border-t-2 border-blue-500"></div>
                        <p className="mt-2 text-gray-600">Loading members...</p>
                    </div>
                ) : (
                    <>
                        <div className="overflow-x-auto">
                            <table className="min-w-full divide-y divide-gray-200">
                                <thead className="bg-gray-50">
                                <tr>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Member
                                    </th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Contact
                                    </th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Status
                                    </th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Workplace
                                    </th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                                        Actions
                                    </th>
                                </tr>
                                </thead>
                                <tbody className="bg-white divide-y divide-gray-200">
                                {members.map((member) => (
                                    <tr key={member.id} className="hover:bg-gray-50">
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div>
                                                <div className="text-sm font-medium text-gray-900">
                                                    {member.name}
                                                </div>
                                                <div className="text-sm text-gray-500">
                                                    {member.membershipNumber}
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div className="space-y-1">
                                                {getContactBadge(member)}
                                                <div className="text-xs text-gray-500">
                                                    {member.primaryEmail && (
                                                        <div>📧 {member.primaryEmail}</div>
                                                    )}
                                                    {member.telephoneMobile && (
                                                        <div>📱 {member.telephoneMobile}</div>
                                                    )}
                                                </div>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div className="space-y-1">
                                                {getStatusBadge(member)}
                                                {member.absenceReason && (
                                                    <div className="text-xs text-gray-500">
                                                        Reason: {member.absenceReason}
                                                    </div>
                                                )}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <div className="text-sm text-gray-900">{member.workplace}</div>
                                            <div className="text-sm text-gray-500">{member.employer}</div>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm">
                                            <button
                                                onClick={() => fetchMemberPreferences(member.membershipNumber)}
                                                className="text-blue-600 hover:text-blue-800"
                                            >
                                                View Details
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>

                        {/* Pagination */}
                        {totalPages > 1 && (
                            <div className="flex justify-between items-center mt-4">
                                <div className="text-sm text-gray-700">
                                    Page {currentPage + 1} of {totalPages}
                                </div>
                                <div className="flex space-x-2">
                                    <button
                                        onClick={() => fetchMembers(selectedStatus, currentPage - 1)}
                                        disabled={currentPage === 0}
                                        className="px-3 py-1 bg-gray-200 rounded disabled:opacity-50"
                                    >
                                        Previous
                                    </button>
                                    <button
                                        onClick={() => fetchMembers(selectedStatus, currentPage + 1)}
                                        disabled={currentPage >= totalPages - 1}
                                        className="px-3 py-1 bg-gray-200 rounded disabled:opacity-50"
                                    >
                                        Next
                                    </button>
                                </div>
                            </div>
                        )}
                    </>
                )}
            </div>

            {/* Member Details Modal */}
            {showMemberDetails && selectedMember && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full m-4 max-h-[90vh] overflow-y-auto">
                        <div className="p-6">
                            <div className="flex justify-between items-center mb-4">
                                <h3 className="text-lg font-bold text-gray-900">
                                    Member Details: {selectedMember.name}
                                </h3>
                                <button
                                    onClick={() => setShowMemberDetails(false)}
                                    className="text-gray-400 hover:text-gray-600"
                                >
                                    ✕
                                </button>
                            </div>

                            <div className="space-y-4">
                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700">Membership Number</label>
                                        <p className="text-sm text-gray-900">{selectedMember.membershipNumber}</p>
                                    </div>
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700">Region</label>
                                        <p className="text-sm text-gray-900">{selectedMember.regionDesc}</p>
                                    </div>
                                </div>

                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700">Email</label>
                                        <p className="text-sm text-gray-900">{selectedMember.primaryEmail || 'Not provided'}</p>
                                    </div>
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700">Mobile</label>
                                        <p className="text-sm text-gray-900">{selectedMember.telephoneMobile || 'Not provided'}</p>
                                    </div>
                                </div>

                                <div className="grid grid-cols-2 gap-4">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700">Workplace</label>
                                        <p className="text-sm text-gray-900">{selectedMember.workplace || 'Not provided'}</p>
                                    </div>
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700">Employer</label>
                                        <p className="text-sm text-gray-900">{selectedMember.employer || 'Not provided'}</p>
                                    </div>
                                </div>

                                <div className="flex space-x-4">
                                    {getStatusBadge(selectedMember)}
                                    {getContactBadge(selectedMember)}
                                </div>

                                {selectedMember.absenceReason && (
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700">Absence Reason</label>
                                        <p className="text-sm text-gray-900">{selectedMember.absenceReason}</p>
                                    </div>
                                )}

                                {/* BMM Preferences */}
                                {selectedMember.bmmPreferences && (
                                    <div className="border-t pt-4">
                                        <h4 className="font-medium text-gray-900 mb-2">BMM Preferences</h4>
                                        <div className="space-y-2 text-sm">
                                            {selectedMember.bmmPreferences.preferredVenues && (
                                                <div>
                                                    <span className="font-medium">Preferred Venues:</span>
                                                    <div className="ml-2">
                                                        {selectedMember.bmmPreferences.preferredVenues.join(', ')}
                                                    </div>
                                                </div>
                                            )}
                                            {selectedMember.bmmPreferences.preferredTimes && (
                                                <div>
                                                    <span className="font-medium">Preferred Times:</span>
                                                    <div className="ml-2">
                                                        {selectedMember.bmmPreferences.preferredTimes.join(', ')}
                                                    </div>
                                                </div>
                                            )}
                                            {selectedMember.bmmPreferences.attendanceWillingness && (
                                                <div>
                                                    <span className="font-medium">Attendance Willingness:</span>
                                                    <span className="ml-2">{selectedMember.bmmPreferences.attendanceWillingness}</span>
                                                </div>
                                            )}
                                            {selectedMember.bmmPreferences.specialVoteInterest && (
                                                <div>
                                                    <span className="font-medium">Special Vote Interest:</span>
                                                    <span className="ml-2">{selectedMember.bmmPreferences.specialVoteInterest}</span>
                                                </div>
                                            )}
                                            {selectedMember.bmmPreferences.suggestedVenue && (
                                                <div>
                                                    <span className="font-medium">Suggested Venue:</span>
                                                    <div className="ml-2 p-2 bg-gray-50 rounded">
                                                        {selectedMember.bmmPreferences.suggestedVenue}
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                )}

                                <div className="flex justify-between text-xs text-gray-500 border-t pt-2">
                                    <span>Created: {new Date(selectedMember.createdAt).toLocaleString()}</span>
                                    {selectedMember.formSubmissionTime && (
                                        <span>Submitted: {new Date(selectedMember.formSubmissionTime).toLocaleString()}</span>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}