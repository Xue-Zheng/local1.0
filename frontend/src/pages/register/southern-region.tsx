import { useEffect } from 'react';
import { useRouter } from 'next/router';
import Link from 'next/link';

export default function SouthernRegionBMM() {
    const router = useRouter();

    useEffect(() => {
        // Redirect to main BMM template with Southern Region parameter
        const token = router.query.token as string;
        if (token) {
            router.replace(`/register/bmm-template?token=${token}&region=Southern%20Region`);
        } else {
            router.replace('/register/bmm-template?region=Southern%20Region');
        }
    }, [router]);

    return (
        <div className="min-h-screen bg-gradient-to-br from-purple-50 to-purple-100 dark:from-gray-900 dark:to-gray-800 flex items-center justify-center">
            <div className="text-center">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-purple-600 mx-auto mb-4"></div>
                <p className="text-purple-800 dark:text-purple-300">Redirecting to Southern Region BMM Registration...</p>
                <p className="text-sm text-purple-700 dark:text-purple-400 mt-2">
                    Special voting options available for South Island members
                </p>
                <Link href="/register/bmm-template?region=Southern%20Region" className="text-purple-600 hover:text-purple-800 mt-4 inline-block">
                    Click here if not redirected automatically
                </Link>
            </div>
        </div>
    );
}