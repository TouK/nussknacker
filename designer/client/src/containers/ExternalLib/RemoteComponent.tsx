import { ModuleString, ModuleUrl } from "./types";
import { useExternalLib } from "./hooks";
import React from "react";
import { splitUrl } from "./tools";
import { ExternalModule } from "./ExternalModule";
import SystemUtils from "../../common/SystemUtils";
import { NuThemeProvider } from "../theme/nuThemeProvider";
import { createRoot } from "react-dom/client";

function Component<P>({ scope, ...props }: { scope: ModuleString } & P) {
    const {
        module: { default: Component },
    } = useExternalLib(scope);
    return <Component {...props} />;
}

export function RemoteComponent<P>({ url, ...props }: { url: ModuleUrl; scope: ModuleString } & P) {
    return (
        <ExternalModule url={url}>
            <Component {...props} />
        </ExternalModule>
    );
}

export const loadExternalReactModule = (url, props) => {
    const rootContainer = document.createElement(`div`);
    document.body.appendChild(rootContainer);
    const [urlValue, scopeValue, scriptValue] = splitUrl(url);
    const root = createRoot(rootContainer);
    root.render(
        <NuThemeProvider>
            <RemoteComponent url={urlValue} scope={scopeValue} scriptOrigin={scriptValue} {...props} />
        </NuThemeProvider>,
    );
};

export const loadExternalReactModuleWithAuth = (url, props) => {
    const getAuthToken: () => Promise<string> = () => SystemUtils.asyncAuthorizationToken();
    loadExternalReactModule(url, { getAuthToken, ...props });
};

window["loadExternalReactModule"] = loadExternalReactModule;
window["loadExternalReactModuleWithAuth"] = loadExternalReactModuleWithAuth;
