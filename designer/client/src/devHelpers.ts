import { AxiosError } from "axios";

export const isProd = process.env.NODE_ENV === "production";
export const isVisualTesting = window["Cypress"];
export const isDev = !isProd && !isVisualTesting;

export function withLogs<A extends unknown[], R>(fn: (...args: A) => R, message?: string): (...args: A) => R {
    return function (...args) {
        const result = fn(...args);
        if (isDev) console.debug(message || fn.name, args, result);
        return result;
    };
}

export function handleAxiosError(error: AxiosError): string {
    if (error.response?.data && typeof error.response.data === "string") {
        return error.response.data;
    }

    const httpStatusCode = error.response?.status;
    switch (httpStatusCode) {
        case 400:
            return "Bad Request: The server could not understand the request.";
        case 401:
            return "Unauthorized: Authentication is required.";
        case 403:
            return "Forbidden: You do not have permission to access this resource.";
        case 404:
            return "Not Found: The requested resource could not be found.";
        case 500:
            return "Internal Server Error: Something went wrong on the server.";
        case 503:
            return "Service Unavailable: The server is currently unavailable.";
        default:
            return "An unknown error occurred. Please try again.";
    }
}
