import { useEffect } from "react";

import api from "../../api";
import type { ConnectionError } from "./ConnectionErrorProvider";

function anySignal(...signals: AbortSignal[]): AbortSignal {
    const abortController = new AbortController();
    signals.forEach((signal) => {
        signal.addEventListener("abort", () => abortController.abort(signal.reason));
    });
    return abortController.signal;
}

export const useCancelRequestsIfConnectionProblem = (connectionError: ConnectionError) => {
    useEffect(() => {
        const interceptorRequestId = api.interceptors.request.use(
            (config) => {
                const controller = new AbortController();

                if (connectionError && !config.url.includes("/notifications")) {
                    controller.abort();
                }

                const signal = config.signal ? anySignal(config.signal as AbortSignal, controller.signal) : controller.signal;
                return { ...config, signal };
            },
            (error) => {
                return Promise.reject(error);
            },
        );

        return () => {
            api.interceptors.request.eject(interceptorRequestId);
        };
    }, [connectionError]);
};
