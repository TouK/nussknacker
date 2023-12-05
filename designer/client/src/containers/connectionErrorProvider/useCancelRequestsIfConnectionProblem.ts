import { useEffect } from "react";
import api from "../../api";
import { ConnectionError } from "./ConnectionErrorProvider";

export const useCancelRequestsIfConnectionProblem = (connectionError: ConnectionError) => {
    useEffect(() => {
        const interceptorRequestId = api.interceptors.request.use(
            (config) => {
                const controller = new AbortController();

                if (connectionError && !config.url.includes("/notifications")) {
                    controller.abort();
                }

                return { ...config, signal: controller.signal };
            },
            function (error) {
                return Promise.reject(error);
            },
        );

        return () => {
            api.interceptors.request.eject(interceptorRequestId);
        };
    }, [connectionError]);
};
