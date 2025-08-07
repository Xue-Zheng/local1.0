import { ThemeProvider } from 'next-themes';
import { AppProps } from 'next/app';
import '../styles/globals.css';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import NProgress from 'nprogress';
import 'nprogress/nprogress.css';
import { useRouter } from 'next/router';
import { useEffect } from 'react';
function MyApp({ Component, pageProps }: AppProps) {
    const router = useRouter();
    useEffect(() => {
        const handleStart = () => {
            NProgress.start();
        };
        const handleStop = () => {
            NProgress.done();
        };
        router.events.on('routeChangeStart', handleStart);
        router.events.on('routeChangeComplete', handleStop);
        router.events.on('routeChangeError', handleStop);
        return () => {
            router.events.off('routeChangeStart', handleStart);
            router.events.off('routeChangeComplete', handleStop);
            router.events.off('routeChangeError', handleStop);
        };
    }, [router]);
    return (
        <ThemeProvider attribute="class">
            <Component {...pageProps} />
            <ToastContainer position="top-right" autoClose={5000} hideProgressBar={false} />
        </ThemeProvider>
    );
}
export default MyApp;