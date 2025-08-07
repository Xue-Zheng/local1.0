import { useEffect } from 'react';
import { useRouter } from 'next/router';
import Link from 'next/link';

export default function CentralRegionBMM() {
    const router = useRouter();

    useEffect(() => {
        // Redirect to main BMM template with Central Region parameter
        const token = router.query.token as string;
        if (token) {
            router.replace(`/register/bmm-template?token=${token}&region=Central%20Region`);
        } else {
            router.replace('/register/bmm-template?region=Central%20Region');
        }
    }, [router]);

    return (
        <div className="min-h-screen bg-gradient-to-br from-blue-50 to-blue-100 dark:from-gray-900 dark:to-gray-800 flex items-center justify-center">
            <div className="text-center">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
                <p className="text-blue-800 dark:text-blue-300">Redirecting to Central Region BMM Registration...</p>
                <Link href="/register/bmm-template?region=Central%20Region" className="text-blue-600 hover:text-blue-800 mt-4 inline-block">
                    Click here if not redirected automatically
                </Link>
            </div>
        </div>
    );
}