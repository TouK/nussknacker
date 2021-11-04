import loadable from "@loadable/component";
import React, { PropsWithChildren, useCallback } from "react";
import { useNkSettingsQuery } from "./useNkSettingsQuery";

const NkAuthInit = loadable(async () => {
    const { AuthInitializer } = await import("nussknackerUi/Auth");
    return AuthInitializer;
});

export function Auth({ children }: PropsWithChildren<unknown>): JSX.Element {
    const { data: settings } = useNkSettingsQuery();

    const authFulfilled = useCallback(async () => {
        console.debug("auth done!");
    }, []);

    return (
        <NkAuthInit authenticationSettings={settings?.authentication} onAuthFulfilled={authFulfilled}>
            {children}
        </NkAuthInit>
    );
}
