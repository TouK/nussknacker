import { lazy } from "@loadable/component";
import type * as Cfg from "nussknackerUi/config";
import type HttpService from "nussknackerUi/HttpService";
import type { PropsWithChildren } from "react";
import React from "react";

import { DefaultSuspense } from "../common/defaultSuspense";

const Config = lazy.lib(() => import("nussknackerUi/config"));
export const NkConfigContext = React.createContext<typeof Cfg>(null);

const Api = lazy.lib(() => import("nussknackerUi/HttpService"));
export const NkApiContext = React.createContext<typeof HttpService>(null);

export function NkApiProvider({ children }: PropsWithChildren<unknown>): JSX.Element {
    return (
        <DefaultSuspense>
            <Config>
                {(configModule) => (
                    <NkConfigContext.Provider value={configModule}>
                        <Api>{(apiModule) => <NkApiContext.Provider value={apiModule.default}>{children}</NkApiContext.Provider>}</Api>
                    </NkConfigContext.Provider>
                )}
            </Config>
        </DefaultSuspense>
    );
}
