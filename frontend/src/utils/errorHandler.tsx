import { toast } from 'react-toastify';

export interface ApiError {
    status: string;
    message: string;
    data?: any;
    timestamp?: string;
}

export interface ErrorHandlerOptions {
    showToast?: boolean;
    defaultMessage?: string;
    customHandler?: (error: any) => void;
}

export class ErrorHandler {
    /**
     * 统一处理API错误
     */
    static handleApiError(error: any, options: ErrorHandlerOptions = {}): string {
        const {
            showToast = true,
            defaultMessage = 'An error occurred, please try again',
            customHandler
        } = options;

        let errorMessage = defaultMessage;

        if (customHandler) {
            customHandler(error);
            return errorMessage;
        }

        // 网络错误
        if (!error.response) {
            if (error.code === 'NETWORK_ERROR' || error.message === 'Network Error') {
                errorMessage = 'Network connection failed, please check your internet connection';
            } else if (error.code === 'ECONNABORTED') {
                errorMessage = 'Request timeout, please try again';
            } else {
                errorMessage = 'Network error, please try again later';
            }
        }
        // HTTP状态错误
        else {
            const { status, data } = error.response;

            switch (status) {
                case 400:
                    errorMessage = data?.message || 'Invalid request parameters';
                    break;
                case 401:
                    errorMessage = 'Authentication failed, please login again';
                    // 可以在这里添加自动跳转到登录页面的逻辑
                    if (typeof window !== 'undefined' && window.location.pathname.includes('/admin/')) {
                        setTimeout(() => {
                            window.location.href = '/admin/login';
                        }, 1500);
                    }
                    break;
                case 403:
                    errorMessage = 'Access denied, insufficient permissions';
                    break;
                case 404:
                    errorMessage = data?.message || 'Requested resource not found';
                    break;
                case 409:
                    errorMessage = data?.message || 'Data conflict, please check for duplicates';
                    break;
                case 413:
                    errorMessage = 'File size too large, please try a smaller file';
                    break;
                case 422:
                    errorMessage = data?.message || 'Data validation failed';
                    break;
                case 429:
                    errorMessage = 'Too many requests, please try again later';
                    break;
                case 500:
                    errorMessage = data?.message || 'Server internal error, please try again later';
                    break;
                case 502:
                    errorMessage = 'Server gateway error, please try again later';
                    break;
                case 503:
                    errorMessage = 'Server temporarily unavailable, please try again later';
                    break;
                default:
                    errorMessage = data?.message || `Server error (${status}), please try again later`;
            }
        }

        if (showToast) {
            toast.error(errorMessage);
        }

        console.error('API Error:', {
            url: error.config?.url,
            method: error.config?.method,
            status: error.response?.status,
            message: errorMessage,
            originalError: error
        });

        return errorMessage;
    }

    /**
     * 处理表单验证错误
     */
    static handleValidationError(error: any): Record<string, string> {
        const errors: Record<string, string> = {};

        if (error.response?.data?.data && typeof error.response.data.data === 'object') {
            return error.response.data.data;
        }

        return errors;
    }

    /**
     * 静态部署特有的错误处理
     */
    static handleStaticDeploymentError(error: any): string {
        // 处理静态部署时可能出现的CORS错误
        if (error.code === 'ERR_NETWORK' || error.message?.includes('CORS')) {
            return 'Unable to connect to server. Please check if the API server is running and accessible.';
        }

        // 处理基础URL配置错误
        if (error.config?.baseURL && error.code === 'ENOTFOUND') {
            return 'API server address not found. Please check the server configuration.';
        }

        return this.handleApiError(error, { showToast: false });
    }

    /**
     * 异步操作包装器，自动处理错误
     */
    static async withErrorHandling<T>(
        operation: () => Promise<T>,
        options: ErrorHandlerOptions = {}
    ): Promise<{ data?: T; error?: string }> {
        try {
            const data = await operation();
            return { data };
        } catch (error) {
            const errorMessage = this.handleApiError(error, options);
            return { error: errorMessage };
        }
    }
}

// 导出便捷方法
export const handleError = ErrorHandler.handleApiError;
export const withErrorHandling = ErrorHandler.withErrorHandling;