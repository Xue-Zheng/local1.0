import React, { useState, useRef } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { useReactToPrint } from 'react-to-print';

interface EventMember {
    id: number;
    name: string;
    membershipNumber: string;
    primaryEmail: string;
    mobilePhone: string;
    eventId: number;
    eventName: string;
    eventCode: string;
    token: string;
    region?: string;
    assignedVenue?: string;
    venueAddress?: string;
    assignedDate?: string;
    assignedSession?: string;
    timeSpan?: string;
    ticketToken?: string;
}

interface Event {
    id: number;
    name: string;
    eventCode: string;
    description: string;
    date: string;
    eventType: string;
}

interface QRTicketGeneratorProps {
    event: Event;
    members: EventMember[];
}

const QRTicketGenerator: React.FC<QRTicketGeneratorProps> = ({ event, members }) => {
    const [selectedMembers, setSelectedMembers] = useState<number[]>([]);
    const [selectAll, setSelectAll] = useState(false);
    const printRef = useRef<HTMLDivElement>(null);

    const handlePrint = useReactToPrint({
        contentRef: printRef,
        documentTitle: `${event.name} - QR Code tickets`,
    });

    const handleSelectAll = () => {
        if (selectAll) {
            setSelectedMembers([]);
        } else {
            setSelectedMembers(members.map(m => m.id));
        }
        setSelectAll(!selectAll);
    };

    const handleMemberSelect = (memberId: number) => {
        if (selectedMembers.includes(memberId)) {
            setSelectedMembers(selectedMembers.filter(id => id !== memberId));
        } else {
            setSelectedMembers([...selectedMembers, memberId]);
        }
    };

    const selectedMemberDetails = members.filter(m => selectedMembers.includes(m.id));

    const formatEventDate = (dateString: string) => {
        try {
            const date = new Date(dateString);
            return date.toLocaleDateString('en-US', {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
                weekday: 'long'
            });
        } catch {
            return dateString;
        }
    };

    return (
        <div className="space-y-6">
            {/* Select Members */}
            <div className="bg-white shadow rounded-lg p-6">
                <div className="flex justify-between items-center mb-4">
                    <h3 className="text-lg font-medium text-gray-900">Select Members for Ticket Generation</h3>
                    <div className="flex items-center space-x-4">
                        <label className="flex items-center">
                            <input
                                type="checkbox"
                                checked={selectAll}
                                onChange={handleSelectAll}
                                className="h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300 rounded"
                            />
                            <span className="ml-2 text-sm text-gray-700">Select All</span>
                        </label>
                        <span className="text-sm text-gray-500">
              Selected {selectedMembers.length} / {members.length}
            </span>
                    </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 max-h-96 overflow-y-auto">
                    {members.map((member) => (
                        <div
                            key={member.id}
                            className={`p-3 border rounded-lg cursor-pointer transition-colors ${
                                selectedMembers.includes(member.id)
                                    ? 'border-indigo-500 bg-indigo-50'
                                    : 'border-gray-200 hover:border-gray-300'
                            }`}
                            onClick={() => handleMemberSelect(member.id)}
                        >
                            <div className="flex items-center">
                                <input
                                    type="checkbox"
                                    checked={selectedMembers.includes(member.id)}
                                    onChange={() => {}}
                                    className="h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300 rounded mr-3"
                                />
                                <div className="flex-1 min-w-0">
                                    <p className="text-sm font-medium text-gray-900 truncate">
                                        {member.name}
                                    </p>
                                    <p className="text-xs text-gray-500">
                                        {member.membershipNumber}
                                    </p>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>

                {selectedMembers.length > 0 && (
                    <div className="mt-4 flex justify-end">
                        <button
                            onClick={handlePrint}
                            className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700"
                        >
                            <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
                            </svg>
                            Print Tickets ({selectedMembers.length})
                        </button>
                    </div>
                )}
            </div>

            {/* Print preview and generate ticket */}
            <div style={{ display: 'none' }}>
                <div ref={printRef} className="space-y-4">
                    {selectedMemberDetails.map((member, index) => (
                        <div key={member.id} className={`${index > 0 ? 'page-break-before' : ''}`}>
                            <div className="bg-white border-2 border-gray-300 rounded-lg overflow-hidden w-full max-w-md mx-auto">
                                {/* Event ticket header - matching ticket page style */}
                                <div className="bg-gradient-to-r from-blue-900 to-blue-700 text-white p-6 text-center">
                                    <div className="mb-2">
                                        <h1 className="text-2xl font-bold">E tū Event System</h1>
                                        <p className="text-lg">Member Event Ticket</p>
                                    </div>
                                    <div className="mt-4 border-t border-blue-600 pt-4">
                                        <h2 className="text-xl font-semibold mb-2">{event.name || '2025 E tū Biennial Membership Meeting'}</h2>

                                        {/* Venue and Address */}
                                        {member.assignedVenue && (
                                            <div className="mb-2">
                                                <p className="text-base font-medium">{member.assignedVenue}</p>
                                                {member.venueAddress && (
                                                    <p className="text-sm">{member.venueAddress}</p>
                                                )}
                                            </div>
                                        )}

                                        {/* Date */}
                                        {member.assignedDate && (
                                            <p className="text-base mb-1">{member.assignedDate}</p>
                                        )}

                                        {/* Session Time */}
                                        {member.assignedSession && (
                                            <p className="text-lg font-medium mb-1">Meeting starts: {member.assignedSession}</p>
                                        )}

                                        {/* Travel Time Span */}
                                        {member.timeSpan && (
                                            <p className="text-sm">Travel time span: {member.timeSpan}</p>
                                        )}
                                    </div>
                                </div>

                                {/* Member Information */}
                                <div className="p-6">
                                    <div className="mb-4">
                                        <p className="text-gray-600 text-sm">Name:</p>
                                        <p className="text-xl font-bold text-gray-900">{member.name}</p>
                                    </div>
                                    <div className="mb-4">
                                        <p className="text-gray-600 text-sm">Membership Number:</p>
                                        <p className="text-xl font-bold text-gray-900">{member.membershipNumber}</p>
                                    </div>
                                    {member.region && (
                                        <div className="mb-6">
                                            <p className="text-gray-600 text-sm">Region:</p>
                                            <p className="text-lg font-semibold text-gray-900">{member.region}</p>
                                        </div>
                                    )}

                                    {/* QR Code */}
                                    <div className="flex justify-center mb-6">
                                        <QRCodeSVG
                                            value={JSON.stringify({
                                                token: member.ticketToken || member.token,
                                                membershipNumber: member.membershipNumber,
                                                name: member.name,
                                                type: 'event_checkin',
                                                checkinUrl: `https://events.etu.nz/api/checkin/${member.ticketToken || member.token}`
                                            })}
                                            size={200}
                                            level="H"
                                            marginSize={4}
                                        />
                                    </div>

                                    <div className="text-center">
                                        <p className="text-sm font-medium text-gray-900">Scan this QR code for quick check-in at the venue</p>
                                        <p className="text-xs mt-2 text-gray-600">Ticket ID: {member.ticketToken || member.token}</p>
                                    </div>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
};

export default QRTicketGenerator;