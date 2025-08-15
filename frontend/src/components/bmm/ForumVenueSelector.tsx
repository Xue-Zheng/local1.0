import React from 'react';
import { forumVenueOptions, hasMultipleVenues } from '@/config/venueMapping';

interface ForumVenueSelectorProps {
    forumDesc: string;
    selectedVenue?: string;
    onVenueChange?: (venue: string) => void;
    className?: string;
}

export default function ForumVenueSelector({
                                               forumDesc,
                                               selectedVenue,
                                               onVenueChange,
                                               className = ''
                                           }: ForumVenueSelectorProps) {

    // 如果没有多个venue选项，直接显示forum名称
    if (!hasMultipleVenues(forumDesc)) {
        return <span className={className}>{forumDesc}</span>;
    }

    // 获取venue选项
    const venues = forumVenueOptions[forumDesc as keyof typeof forumVenueOptions] || [];
    const currentVenue = selectedVenue || forumDesc;

    // 如果是只读模式（没有onChange），显示当前值
    if (!onVenueChange) {
        return <span className={className}>{currentVenue}</span>;
    }

    // 显示下拉框
    return (
        <select
            value={currentVenue}
            onChange={(e) => onVenueChange(e.target.value)}
            className={`border border-gray-300 rounded px-2 py-1 ${className}`}
        >
            {venues.map(venue => (
                <option key={venue} value={venue}>
                    {venue}
                </option>
            ))}
        </select>
    );
}