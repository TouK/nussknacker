import { ModuleString, ModuleUrl } from "./types";
import { useExternalLib } from "./hooks";
import React from "react";
import { splitUrl } from "./tools";
import ReactDOM from "react-dom";
import { NkThemeProvider } from "../theme";
import { MuiThemeProvider } from "../muiThemeProvider";
import { ExternalModule } from "./ExternalModule";

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
    ReactDOM.render(
        <NkThemeProvider>
            <MuiThemeProvider>
                <RemoteComponent url={urlValue} scope={scopeValue} scriptOrigin={scriptValue} {...props} />
            </MuiThemeProvider>
        </NkThemeProvider>,
        rootContainer
    );
};

window["loadExternalReactModule"] = loadExternalReactModule;
