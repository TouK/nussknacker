import * as queryString from "query-string";
import React, { memo, useMemo } from "react";
import { ErrorBoundary } from "../components/common/error-boundary";
import { ModuleUrl, splitUrl } from "@touk/federated-component";
import { NotFound } from "../components/common/error-boundary/NotFound";
import SystemUtils from "../common/SystemUtils";
import ScopedCssBaseline from "@mui/material/ScopedCssBaseline";
import { useNavigate, useParams } from "react-router-dom";
import { RemoteComponent } from "../components/RemoteComponent";

export type BaseTab = {
    tab: BaseTabData;
};

export type BaseTabData = {
    // expected:
    //  * url of working app - to include in iframe
    //  * url ({module}/{path}@{host}/{remoteEntry}.js) of hosted remoteEntry js file (module federation) with default exported react component - included as component
    //  * url of internal route in NK
    url: string;
    type: "Local" | "IFrame" | "Remote" | "Url";
    addAccessTokenInQueryParam?: boolean;
    accessTokenInQuery?: {
        enabled: boolean;
        parameterName: string;
    };
};

export type DynamicTabData = BaseTabData & {
    title: string;
    id: string;
    requiredPermission?: string;
    spacerBefore?: boolean;
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
        <ScopedCssBaseline
            style={{
                flex: 1,
                overflow: "hidden",
            }}
        >
            <RemoteComponent url={urlValue} scope={scope} {...componentProps} />
        </ScopedCssBaseline>
    );
};

export const IframeTab = ({ tab }: BaseTab) => {
    const { addAccessTokenInQueryParam, accessTokenInQuery, url } = tab;
    const accessToken = (addAccessTokenInQueryParam || accessTokenInQuery?.enabled) && SystemUtils.getAccessToken();
    const accessTokenParam = {};
    if (accessToken != null) {
        // accessToken name is for backward compatibility reasons
        const accessTokenParamName = addAccessTokenInQueryParam ? "accessToken" : accessTokenInQuery.parameterName;
        accessTokenParam[accessTokenParamName] = accessToken;
    }
    return (
        <iframe
            src={queryString.stringifyUrl({
                url,
                query: { iframe: true, ...accessTokenParam },
            })}
            width="100%"
            height="100%"
            frameBorder="0"
        />
    );
};

function useExtednedComponentProps<P extends Record<string, any>>(props: P) {
    const navigate = useNavigate();
    const { "*": rest } = useParams<{
        "*": string;
    }>();
    return useMemo(
        () => ({
            basepath: window.location.pathname.replace(rest, ""),
            navigate,
            ...props,
        }),
        [navigate, props, rest],
    );
}

export const DynamicTab = memo(function DynamicComponent<P extends BaseTab>({ tab, ...props }: P): JSX.Element {
    const componentProps = useExtednedComponentProps(props);
    switch (tab.type) {
        case "Remote":
            return <RemoteModuleTab url={tab.url as ModuleUrl} componentProps={componentProps} />;
        case "IFrame":
            return <IframeTab tab={tab} />;
    }
});
