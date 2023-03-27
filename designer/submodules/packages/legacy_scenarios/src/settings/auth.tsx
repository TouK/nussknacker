import loadable from "@loadable/component";
import { LinearProgress } from "@mui/material";
import React, { PropsWithChildren, useCallback } from "react";
import { useNkSettingsQuery } from "./useNkSettingsQuery";

const NkAuthInit = loadable(async () => {
    const { AuthInitializer } = await import("nussknackerUi/Auth");
    return AuthInitializer;
});

export function Auth({ children }: PropsWithChildren<unknown>): JSX.Element {
    const { data: settings, isLoading } = useNkSettingsQuery();

    const authFulfilled = useCallback(async () => {
        console.debug("auth done!");
    }, []);

    return (
        <>
            {isLoading && <LinearProgress color="info" />}
            <NkAuthInit authenticationSettings={settings?.authentication} onAuthFulfilled={authFulfilled}>
                {children}
            </NkAuthInit>
        </>
    );
}
