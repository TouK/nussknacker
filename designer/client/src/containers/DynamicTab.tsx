import * as queryString from "query-string";
import React, { memo, useMemo } from "react";
import ErrorBoundary from "../components/common/ErrorBoundary";
import { ExternalModule, splitUrl } from "./ExternalLib";
import { ModuleString, ModuleUrl } from "./ExternalLib/types";
import { MuiThemeProvider } from "./muiThemeProvider";
import { NotFound } from "./errors/NotFound";
import SystemUtils from "../common/SystemUtils";
import ScopedCssBaseline from "@mui/material/ScopedCssBaseline";
import { useNavigate, useParams } from "react-router-dom";
import { RemoteComponent } from "./ExternalLib/RemoteComponent";

export type DynamicTabData = {
    title: string;
    id: string;
    // expected:
    //  * url of working app - to include in iframe
    //  * url ({module}/{path}@{host}/{remoteEntry}.js) of hosted remoteEntry js file (module federation) with default exported react component - included as component
    //  * url of internal route in NK
    url: string;
    requiredPermission?: string;
    type: "Local" | "IFrame" | "Remote" | "Url";
    addAccessTokenInQueryParam?: boolean;
};

export interface RemoteComponentProps {
    /**
     * @deprecated not needed and used anymore
     */
    basepath: string;
    navigate: (path: string) => void;
}

export const RemoteModuleTab = <CP extends RemoteComponentProps>({
    url,
    componentProps,
}: {
    url: ModuleUrl;
    componentProps: CP;
}): JSX.Element => {
    const [urlValue, scope] = useMemo(() => splitUrl(url), [url]);
    return (
        <ErrorBoundary FallbackComponent={() => <NotFound />}>
            <MuiThemeProvider>
                <ScopedCssBaseline style={{ flex: 1, overflow: "hidden" }}>
                    <RemoteComponent url={urlValue} scope={scope} {...componentProps} />
                </ScopedCssBaseline>
            </MuiThemeProvider>
        </ErrorBoundary>
    );
};

export const IframeTab = ({ tab }: { tab: Pick<DynamicTabData, "addAccessTokenInQueryParam" | "url"> }) => {
    const { addAccessTokenInQueryParam, url } = tab;
    const accessToken = addAccessTokenInQueryParam && SystemUtils.getAccessToken();
    return (
        <iframe src={queryString.stringifyUrl({ url, query: { iframe: true, accessToken } })} width="100%" height="100%" frameBorder="0" />
    );
};

function useExtednedComponentProps<P extends Record<string, any>>(props: P) {
    const navigate = useNavigate();
    const { "*": rest } = useParams<{ "*": string }>();
    return useMemo(
        () => ({
            basepath: window.location.pathname.replace(rest, ""),
            navigate,
            ...props,
        }),
        [navigate, props, rest],
    );
}

export const DynamicTab = memo(function DynamicComponent<
    P extends {
        tab: Pick<DynamicTabData, "addAccessTokenInQueryParam" | "url" | "type">;
    },
>({ tab, ...props }: P): JSX.Element {
    const componentProps = useExtednedComponentProps(props);
    switch (tab.type) {
        case "Remote":
            return <RemoteModuleTab url={tab.url} componentProps={componentProps} />;
        case "IFrame":
            return <IframeTab tab={tab} />;
    }
});
