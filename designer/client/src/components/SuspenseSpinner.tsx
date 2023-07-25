import React, { PropsWithChildren } from "react";
import LoaderSpinner from "./Spinner";

export const SuspenseSpinner: React.FC<PropsWithChildren<unknown>> = ({ children }) => (
    <React.Suspense fallback={<LoaderSpinner show={true} />}>{children}</React.Suspense>
);
