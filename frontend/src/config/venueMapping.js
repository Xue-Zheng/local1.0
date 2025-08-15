// Venue mapping configuration for special forums
// Based on bmm-venues-config.json forumVenueMapping

export const forumVenueOptions = {
    "Greymouth": ["Hokitika", "Reefton", "Greymouth"]
    // Whangarei removed - now uses standard venue assignment
};

export const hasMultipleVenues = (forumDesc) => {
    return forumVenueOptions.hasOwnProperty(forumDesc);
};

export const getVenueOptions = (forumDesc) => {
    return forumVenueOptions[forumDesc] || [forumDesc];
};