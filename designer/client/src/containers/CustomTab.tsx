import React from "react";
import { Navigate, useLocation, useParams } from "react-router-dom";

import { CustomTabPage } from "./CustomTabPage";

// redirect passing all params
export function StarRedirect({ to, push }: { to: string; push?: boolean }) {
    const { "*": star = "" } = useParams<{ "*": string }>();
    const { hash, search } = useLocation();
    const rest = `${star}${hash}${search}`;
    return <Navigate to={rest.length ? `${to}/${rest}` : to} replace={!push} />;
}

export function CustomTab(): React.JSX.Element {
    const { id } = useParams<{ id: string }>();
    return <CustomTabPage id={id} />;
}
