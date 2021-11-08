import React, { PropsWithChildren } from "react";

export function DefaultSuspense({ children }: PropsWithChildren<unknown>): JSX.Element {
    return <React.Suspense fallback={"loading..."}>{children}</React.Suspense>;
}
