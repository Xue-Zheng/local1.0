'use client';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/router';

interface AdminAuthGuardProps {
    children: React.ReactNode;
}

const AdminAuthGuard = ({ children }: AdminAuthGuardProps) => {
    const router = useRouter();
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        const checkAuth = () => {
            const token = localStorage.getItem('adminToken');

            if (!token) {
                // No token found, redirect to login immediately
                router.push('/admin/login');
                return;
            }

            // Token exists, allow access
            setIsAuthenticated(true);
            setIsLoading(false);
        };

        checkAuth();
    }, [router]);

    // Show loading state while checking authentication
    if (isLoading) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900">
                <div className="text-center">
                    <div className="inline-block animate-spin rounded-full h-12 w-12 border-t-4 border-purple-500 border-solid"></div>
                    <p className="mt-4 text-lg text-gray-700 dark:text-gray-300">Checking authentication...</p>
                </div>
            </div>
        );
    }

    // Don't render anything if not authenticated
    if (!isAuthenticated) {
        return null;
    }

    // Render children if authenticated
    return <>{children}</>;
};

export default AdminAuthGuard;