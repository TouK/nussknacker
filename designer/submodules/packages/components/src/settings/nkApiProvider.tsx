import { lazy } from "@loadable/component";
import * as Cfg from "nussknackerUi/config";
import HttpService from "nussknackerUi/HttpService";
import React, { PropsWithChildren } from "react";
import { DefaultSuspense } from "../common";

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
