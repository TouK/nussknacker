import React, { PropsWithChildren } from "react";
import { I18nextProvider } from "react-i18next";
import { QueryClientProvider } from "react-query";
import { HistoryProvider } from "../common";
import i18n from "./i18n";
import { NkApiProvider } from "./nkApiProvider";
import { queryClient } from "../store";
import { CacheProvider } from "@emotion/react";
import createCache from "@emotion/cache";
import { prefixer } from "stylis";
import { BrowserRouter as Router } from "react-router-dom";

// copy/paste from https://github.com/drenckpohl/stylis-plugin-extra-scope/blob/stylis-v4/src/index.js
function createExtraScopePlugin(...extra) {
    const scopes = extra.map((scope) => `${scope.trim()} `);

    return (element) => {
        if (element.type !== "rule") {
            return;
        }

        if (element.root?.type === "@keyframes") {
            return;
        }

        if (!element.parent || (element.props.length === 1 && element.value.charCodeAt(0) !== 58) || !element.length) {
            element.props = element.props.flatMap((prop) => scopes.map((scope) => scope + prop));
        }
    };
}

const emotionCache = createCache({
    key: "components",
    stylisPlugins: [
        // default
        prefixer,
        // temporary solution to increase specificity of all emotion/mui styles
        createExtraScopePlugin("body"),
    ],
});

export function RootProviders({ children, basepath }: PropsWithChildren<{ basepath?: string }>): JSX.Element {
    return (
        <CacheProvider value={emotionCache}>
            {/*@see https://github.com/i18next/react-i18next/issues/1379*/}
            <I18nextProvider i18n={i18n as any}>
                <QueryClientProvider client={queryClient}>
                    <NkApiProvider>
                        <Router basename={basepath}>
                            <HistoryProvider>{children}</HistoryProvider>
                        </Router>
                    </NkApiProvider>
                </QueryClientProvider>
            </I18nextProvider>
        </CacheProvider>
    );
}
