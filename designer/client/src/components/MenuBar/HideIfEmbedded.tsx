import React, { PropsWithChildren } from "react";
import { useSearchQuery } from "../../containers/hooks/useSearchQuery";

export function HideIfEmbedded({ children }: PropsWithChildren<unknown>) {
    /**
     * In some cases (eg. docker demo) we serve Grafana and Kibana from nginx proxy, from root app url, and when service responds with error
     * then React app catches this and shows error page. To make it render only error, without app menu, we have mark iframe
     * requests with special query parameter so that we can recognize them and skip menu rendering.
     */
    // TODO: replace with "embedded"
    const [{ iframe: isLoadAsIframe }] = useSearchQuery<{ iframe: boolean }>();
    return isLoadAsIframe ? null : <>{children}</>;
}
