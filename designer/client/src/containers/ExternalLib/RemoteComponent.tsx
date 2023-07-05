import { ModuleString, ModuleUrl } from "./types";
import { useExternalLib } from "./hooks";
import React from "react";
import { splitUrl } from "./tools";
import { NkThemeProvider } from "../theme";
import { MuiThemeProvider } from "../muiThemeProvider";
import { ExternalModule } from "./ExternalModule";
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
    const divElement = createRoot(rootContainer);
    const [urlValue, scopeValue, scriptValue] = splitUrl(url);
    divElement.render(
        <NkThemeProvider>
            <MuiThemeProvider>
                <RemoteComponent url={urlValue} scope={scopeValue} scriptOrigin={scriptValue} {...props} />
            </MuiThemeProvider>
        </NkThemeProvider>,
    );
};

window["loadExternalReactModule"] = loadExternalReactModule;
