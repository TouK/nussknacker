import { ModuleString, ModuleUrl, ScriptUrl } from "./types";
import { useExternalLib } from "./hooks";
import React from "react";
import { splitUrl } from "./tools";
import ReactDOM from "react-dom";
import { ExternalModule, ExternalModuleProps } from "./ExternalModule";

function Component<P>({
    scope,
    ...props
}: {
    scope: ModuleString;
} & P) {
    const {
        module: { default: Component },
    } = useExternalLib(scope);
    return <Component {...props} />;
}

export type RemoteComponentProps<P extends NonNullable<unknown>> = P & {
    url: ModuleUrl;
    scope: ModuleString;
};

export function RemoteComponent<P extends NonNullable<unknown>>({
    url,
    buildHash,
    fallback,
    ...props
}: RemoteComponentProps<P> & Pick<ExternalModuleProps, "fallback" | "buildHash">) {
    return (
        <ExternalModule url={url} fallback={fallback} buildHash={buildHash}>
            <Component {...props} />
        </ExternalModule>
    );
}

export const getExternalReactModuleLoader =
    (Wrapper: React.FunctionComponent<React.PropsWithChildren<unknown>>, getAuthToken?: () => Promise<string>) =>
    <P extends NonNullable<unknown>>(url: string, props: P) => {
        const rootContainer = document.createElement(`div`);
        document.body.appendChild(rootContainer);
        const [urlValue, scopeValue, scriptValue] = splitUrl(url as ModuleUrl);
        ReactDOM.render(
            <Wrapper>
                <RemoteComponent<
                    P & {
                        scriptOrigin: ScriptUrl;
                        getAuthToken?: () => Promise<string>;
                    }
                >
                    url={urlValue}
                    scope={scopeValue}
                    scriptOrigin={scriptValue}
                    getAuthToken={getAuthToken}
                    fallback={null}
                    {...props}
                />
            </Wrapper>,
            rootContainer,
        );
    };
