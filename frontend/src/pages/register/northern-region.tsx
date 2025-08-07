import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';
import Link from 'next/link';

export default function NorthernRegionBMM() {
    const router = useRouter();

    useEffect(() => {
        // Redirect to main BMM template with Northern Region parameter
        const token = router.query.token as string;
        if (token) {
            router.replace(`/register/bmm-template?token=${token}&region=Northern%20Region`);
        } else {
            router.replace('/register/bmm-template?region=Northern%20Region');
        }
    }, [router]);

    return (
        <div className="min-h-screen bg-gradient-to-br from-green-50 to-green-100 dark:from-gray-900 dark:to-gray-800 flex items-center justify-center">
            <div className="text-center">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-green-600 mx-auto mb-4"></div>
                <p className="text-green-800 dark:text-green-300">Redirecting to Northern Region BMM Registration...</p>
                <Link href="/register/bmm-template?region=Northern%20Region" className="text-green-600 hover:text-green-800 mt-4 inline-block">
                    Click here if not redirected automatically
                </Link>
            </div>
        </div>
    );
}