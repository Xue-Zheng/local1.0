import type { NextConfig } from "next";

const nextConfig: NextConfig = {
    reactStrictMode: true,
    // output: 'export',
    trailingSlash: true,
    images: {
        unoptimized: true
    },
    // 注意：静态导出模式下headers配置不会生效
    // CORS需要在服务器端处理
};
export default nextConfig;

// import type { NextConfig } from "next";
//
// const nextConfig: NextConfig = {
//     reactStrictMode: true,
//     output: 'export',
//     trailingSlash: true,
//     images: {
//         unoptimized: true
//     },
//     // 注意：静态导出模式下headers配置不会生效
//     // CORS需要在服务器端处理
// };
// nextConfig.allowedDevOrigins = [
//     'localhost',
//     'http://localhost:3000',
//     'http://10.0.9.238:3000',
//     'https://events.etu.nz'
// ];
// export default nextConfig;
// import type { NextConfig } from "next";
//
// const nextConfig: NextConfig = {
//     reactStrictMode: true,
//     output: 'export',
//
//     async headers() {
//         return [
//             {
//                 source: "/(.*)",
//                 headers: [
//                     {
//                         key: "Access-Control-Allow-Origin",
//                         value: "*", // all
//                     },
//                     {
//                         key: "Access-Control-Allow-Methods",
//                         value: "GET,POST,PUT,DELETE,OPTIONS,PATCH",
//                     },
//                     {
//                         key: "Access-Control-Allow-Headers",
//                         value: "X-Requested-With, Content-Type, Authorization, X-CSRF-Token",
//                     },
//                 ],
//             },
//         ];
//     },
// };
// nextConfig.allowedDevOrigins = [
//     'localhost',
//     'http://localhost:3000',
//     'http://10.0.9.238:3000',
//     'https://events.etu.nz'
// ];
// export default nextConfig;
//
// // import type { NextConfig } from "next";
// //
// // const nextConfig: NextConfig = {
// //     reactStrictMode: true,
// //     output: 'export',
// //     trailingSlash: true,
// //     images: {
// //         unoptimized: true
// //     },
// //     // 注意：静态导出模式下headers配置不会生效
// //     // CORS需要在服务器端处理
// // };
// // nextConfig.allowedDevOrigins = [
// //     'localhost',
// //     'http://localhost:3000',
// //     'http://10.0.9.238:3000',
// //     'https://events.etu.nz'
// // ];
// // export default nextConfig;