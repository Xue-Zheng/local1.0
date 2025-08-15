import api from './api';

export interface VerificationRequest {
    membershipNumber: string;
    verificationCode: string;
    token: string;
    eventId?: number;
}

export interface FinancialFormData {
    name: string;
    primaryEmail: string;
    dob?: string;
    address?: string;
    phoneHome?: string;
    telephoneMobile?: string;
    phoneWork?: string;
    employer?: string;
    payrollNumber?: string;
    siteCode?: string;
    employmentStatus?: string;
    department?: string;
    jobTitle?: string;
    location?: string;
    isConfirmed?: boolean;
    // BMM Preferences
    bmmPreferences?: {
        preferredVenues: string[];
        preferredTimes: string[];
        attendanceWillingness: string; // yes, no
        workplaceInfo?: string; // workplace name and location
        meetingFormat: string;
        specialVoteInterest?: string; // yes, no, not-sure (Southern Region only)
        additionalComments?: string;
        suggestedVenue?: string; // User-suggested venue with details
    };
}

export interface AttendanceRequest {
    isAttending: boolean;
    isSpecialVote?: boolean;
    absenceReason?: string;
}

export interface EventMember {
    // isSpecialMember field removed
    name: string;
    primaryEmail: string;
    membershipNumber: string;
    dob?: string;
    address?: string;
    phoneHome?: string;
    telephoneMobile?: string;
    phoneWork?: string;
    employer?: string;
    payrollNumber?: string;
    siteCode?: string;
    employmentStatus?: string;
    department?: string;
    jobTitle?: string;
    location?: string;
    hasRegistered: boolean;
    isAttending: boolean;
    isSpecialVote: boolean;
    absenceReason?: string;
    hasEmail: boolean;
    hasMobile: boolean;
    eventId: number;
    eventName: string;
    eventType: string;
    regionDesc?: string; // Northern Region, Central Region, Southern Region
    // BMM Preferences fields
    preferredVenues?: string;
    preferredTimes?: string;
    attendanceWillingness?: string;
    workplaceInfo?: string;
    meetingFormat?: string;
    specialVoteInterest?: string;
    additionalComments?: string;
    suggestedVenue?: string;
    assignedVenue?: string;
    assignedRegion?: string;
}

export interface Event {
    id: number;
    name: string;
    eventCode: string;
    description?: string;
    eventType: string;
    eventDate?: string;
    venue?: string;
    isActive: boolean;
    isVotingEnabled: boolean;
    registrationOpen: boolean;
    totalMembers: number;
    registeredMembers: number;
    attendingMembers: number;
    specialVoteMembers?: number;
    votedMembers?: number;
    checkedInMembers?: number;
}

export interface ApiResponse<T> {
    primaryEmail: string;
    name: string;
    status: string;
    message: string;
    data: T;
    timestamp: string;
}

export const verifyMember = async (data: VerificationRequest): Promise<ApiResponse<EventMember>> => {
    const response = await api.post<ApiResponse<EventMember>>('/event-registration/verify', data);
    return response.data;
};

export const getMemberByToken = async (token: string): Promise<ApiResponse<EventMember>> => {
    const response = await api.get<ApiResponse<EventMember>>(`/event-registration/member/${token}`);
    return response.data;
};

export const submitFinancialForm = async (token: string, data: FinancialFormData): Promise<ApiResponse<EventMember>> => {
    const response = await api.post<ApiResponse<EventMember>>(`/event-registration/update-form/${token}`, data);
    return response.data;
};

export const updateAttendance = async (token: string, data: AttendanceRequest): Promise<ApiResponse<EventMember>> => {
    const response = await api.post<ApiResponse<EventMember>>(`/event-registration/attendance/${token}`, data);
    return response.data;
};

export const getUpcomingEvents = async (): Promise<ApiResponse<Event[]>> => {
    const response = await api.get<ApiResponse<Event[]>>('/admin/events/upcoming');
    return response.data;
};


export const getActiveEvents = async (): Promise<ApiResponse<Event[]>> => {
    const response = await api.get<ApiResponse<Event[]>>('/admin/events');
    return response.data;
};

export const checkInMember = async (token: string): Promise<ApiResponse<any>> => {
    const response = await api.post<ApiResponse<any>>(`/checkin/${token}`);
    return response.data;
};