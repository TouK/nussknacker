import { lazy } from "@loadable/component";
import * as Cfg from "nussknackerUi/config";
import HttpService from "nussknackerUi/HttpService";
import IconsModule from "nussknackerUi/ComponentIcon";
import React, { PropsWithChildren } from "react";

const Config = lazy.lib(() => import("nussknackerUi/config"));
export const NkConfigContext = React.createContext<typeof Cfg>(null);

const Api = lazy.lib(() => import("nussknackerUi/HttpService"));
export const NkApiContext = React.createContext<typeof HttpService>(null);

const Icons = lazy.lib(() => import("nussknackerUi/ComponentIcon"));
export const NkIconsContext = React.createContext<typeof IconsModule>(null);

export function NkApiProvider({ children }: PropsWithChildren<unknown>): JSX.Element {
    return (
        <React.Suspense fallback={null}>
            <Config>
                {(configModule) => (
                    <NkConfigContext.Provider value={configModule}>
                        <Icons>
                            {(module) => (
                                <NkIconsContext.Provider value={module}>
                                    <Api>
                                        {(apiModule) => <NkApiContext.Provider value={apiModule.default}>{children}</NkApiContext.Provider>}
                                    </Api>
                                </NkIconsContext.Provider>
                            )}
                        </Icons>
                    </NkConfigContext.Provider>
                )}
            </Config>
        </React.Suspense>
    );
}
