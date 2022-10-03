import React, { PropsWithChildren } from "react";
import { Auth } from "./auth";
import { RootProviders } from "./rootProviders";

export function RootProvidersWithAuth({ children }: PropsWithChildren<unknown>): JSX.Element {
    return (
        <RootProviders>
            <Auth>{children}</Auth>
        </RootProviders>
    );
}
