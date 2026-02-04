import fuse from "fuse.js";
import { KBarProvider } from "kbar";
import type { PropsWithChildren } from "react";
import React from "react";

fuse.config.useExtendedSearch = true;

const actions = [];

export function CommandBarProvider({ children }: PropsWithChildren<unknown>): React.JSX.Element {
    return (
        <KBarProvider actions={actions} options={{ enableHistory: false }}>
            {children}
        </KBarProvider>
    );
}
