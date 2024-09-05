import React from "react";
import { useRouteError } from "react-router-dom";
import { AxiosError } from "axios";
import { NotFound } from "../NotFound";
import { FullPageErrorBoundaryFallbackComponent } from "./FullPageErrorBoundaryFallbackComponent";

export const RouteErrorFallbackComponent = () => {
    const error = useRouteError();

    if (error instanceof AxiosError && error?.response?.status === 404) {
        return <NotFound message={error.response.data.toString()} />;
    }

    return <FullPageErrorBoundaryFallbackComponent />;
};
