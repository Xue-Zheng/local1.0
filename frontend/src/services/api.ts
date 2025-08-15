import axios from 'axios';

// const API_URL = process.env.NEXT_PUBLIC_API_URL || (
//     process.env.NODE_ENV === 'production'
//         ? 'https://events.etu.nz/api' // Production
//         : 'http://localhost:8082/api' // Use
// );

const API_URL = process.env.NEXT_PUBLIC_API_URL || (
    process.env.NODE_ENV === 'production'
        ? 'https://events.etu.nz/api' // Production
        : 'http://localhost:8082/api' // Development
);

// const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082/api';
// const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://10.0.9.238:8082/api';

const api = axios.create({
    baseURL: API_URL,
    headers: {
        'Content-Type': 'application/json',
    },
});

api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('adminToken');
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        console.error("API Request error:", error);
        return Promise.reject(error);
    }
);

api.interceptors.response.use(
    (response) => {
        console.log("API Response success:", response.status);
        return response;
    },
    (error) => {
        console.error("API Response error:", error);
        if (error.response && error.response.status === 401) {
            localStorage.removeItem('adminToken');
            localStorage.removeItem('adminName');
            window.location.href = '/admin/login';
        }
        return Promise.reject(error);
    }
);

// 创建专门的管理员API实例
export const adminApi = axios.create({
    baseURL: `${API_URL}/admin`,
    headers: {
        'Content-Type': 'application/json',
    },
});

// 管理员API拦截器
adminApi.interceptors.request.use(
    (config) => {
        console.log("Admin API Request to:", config.url);
        const token = localStorage.getItem('adminToken');
        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        console.error("Admin API Request error:", error);
        return Promise.reject(error);
    }
);

adminApi.interceptors.response.use(
    (response) => {
        console.log("Admin API Response success:", response.status);
        return response;
    },
    (error) => {
        console.error("Admin API Response error:", error);
        if (error.response && error.response.status === 401) {
            localStorage.removeItem('adminToken');
            localStorage.removeItem('adminName');
            window.location.href = '/admin/login';
        }
        return Promise.reject(error);
    }
);

export default api;