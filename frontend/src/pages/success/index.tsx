'use client';
import Link from 'next/link';
import Layout from '@/components/common/Layout';
export default function SuccessPage() {
    return (
        <Layout>
            <div className="container mx-auto px-4 py-12">
                <div className="max-w-md mx-auto text-center">
                    <div className="mb-6 flex justify-center">
                        <div className="w-20 h-20 rounded-full bg-teal-500 flex items-center justify-center">
                            <svg className="h-12 w-12 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                            </svg>
                        </div>
                    </div>
                    <h1 className="text-3xl font-bold text-black dark:text-white mb-6">Special Voting Request Submitted Successfully</h1>
                    <div className="bg-white dark:bg-gray-800 p-8 rounded-lg shadow-md">
                        <p className="text-lg mb-6 text-gray-700 dark:text-gray-300">
                            Your special voting request has been successfully submitted. Thank you for participating in the E tū voting process!
                        </p>
                        <div className="bg-gray-50 dark:bg-gray-700 p-4 rounded-lg mb-6 text-left">
                            <h3 className="font-bold text-black dark:text-white mb-2">What Happens Next?</h3>
                            <ol className="list-decimal list-inside text-sm space-y-2 text-gray-700 dark:text-gray-300">
                                <li>Your information has been added to the voting pool</li>
                                <li>You will receive an email with a voting link when voting opens</li>
                                <li>Click the link and verify your identity with your membership number and verification code</li>
                                <li>Complete the voting process</li>
                            </ol>
                        </div>
                        <p className="text-sm text-gray-600 dark:text-gray-400 mb-6">
                            If you have any questions, please feel free to contact our support team.
                        </p>
                        <div className="flex justify-center">
                            <Link href="/">
                                <button className="bg-orange-500 hover:bg-orange-600 text-white font-medium py-2 px-6 rounded transition-colors">
                                    Return to Home
                                </button>
                            </Link>
                        </div>
                    </div>
                </div>
            </div>
        </Layout>
    );
}