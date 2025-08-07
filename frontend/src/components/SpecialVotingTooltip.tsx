import { useState, ReactNode, useRef } from 'react';

const SpecialVotingTooltip = ({ children }: { children: ReactNode }) => {
    const [isVisible, setIsVisible] = useState(false);
    const timeoutRef = useRef<NodeJS.Timeout | null>(null);

    const handleMouseEnter = () => {
        if (timeoutRef.current) {
            clearTimeout(timeoutRef.current);
        }
        setIsVisible(true);
    };

    const handleMouseLeave = () => {
        if (timeoutRef.current) {
            clearTimeout(timeoutRef.current);
        }
        timeoutRef.current = setTimeout(() => {
            setIsVisible(false);
        }, 300);
    };

    return (
        <span className="relative inline-block">
<button
    type="button"
    className="inline-flex items-center text-blue-500 hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300 underline decoration-dotted underline-offset-2 cursor-help"
    onMouseEnter={handleMouseEnter}
    onMouseLeave={handleMouseLeave}
    onClick={(e) => {
        e.preventDefault();
        setIsVisible(!isVisible);
    }}
>
{children}
    <svg className="w-4 h-4 ml-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
<path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
</svg>
</button>

            {isVisible && (
                <div
                    className="absolute z-50 w-80 p-4 text-sm text-white bg-gray-900 dark:bg-gray-800 rounded-lg shadow-lg border border-gray-700"
                    style={{
                        top: '100%',
                        left: '50%',
                        transform: 'translateX(-50%)',
                        marginTop: '8px'
                    }}
                    onMouseEnter={handleMouseEnter}
                    onMouseLeave={handleMouseLeave}
                >

                    <div className="absolute -top-2 left-1/2 transform -translate-x-1/2 w-4 h-4 bg-gray-900 dark:bg-gray-800 border-l border-t border-gray-700 rotate-45"></div>

                    <div className="text-orange-400 text-base font-semibold mb-3 flex items-center">
                        <svg className="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                        Special Voting Eligibility
                    </div>

                    <div className="text-gray-200 mb-3 leading-relaxed">
                        According to E tū's rules, special votes are typically granted under these circumstances:
                    </div>

                    <div className="bg-gray-800 dark:bg-gray-700 rounded-md p-3 mb-3">
                        <ul className="list-none text-gray-300 space-y-2 text-xs">
                            <li className="flex items-start">
                                <span className="inline-block w-2 h-2 bg-orange-400 rounded-full mt-1.5 mr-2 flex-shrink-0"></span>
                                <span>You have a disability that prevents full participation in the meeting</span>
                            </li>
                            <li className="flex items-start">
                                <span className="inline-block w-2 h-2 bg-orange-400 rounded-full mt-1.5 mr-2 flex-shrink-0"></span>
                                <span>You are ill or infirm and cannot attend the meeting</span>
                            </li>
                            <li className="flex items-start">
                                <span className="inline-block w-2 h-2 bg-orange-400 rounded-full mt-1.5 mr-2 flex-shrink-0"></span>
                                <span>You live outside a 32-kilometre radius from the meeting location</span>
                            </li>
                            <li className="flex items-start">
                                <span className="inline-block w-2 h-2 bg-orange-400 rounded-full mt-1.5 mr-2 flex-shrink-0"></span>
                                <span>Your employer requires you to work during the meeting time</span>
                            </li>
                        </ul>
                    </div>

                    <div className="text-xs text-gray-400 italic border-t border-gray-700 pt-2 flex items-center">
                        <svg className="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                        Special vote applications must be submitted at least 14 days before the meeting date.
                    </div>
                </div>
            )}
        </span>
    );
};

export default SpecialVotingTooltip;
