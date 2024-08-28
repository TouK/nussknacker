import React, { PropsWithChildren } from "react";
import { useSelector } from "react-redux";
import { NotFound } from "./errors/NotFound";
import { RootState } from "../reducers";
import { RootErrorPage } from "../components/common/RootErrorBoundary";
import { t } from "i18next";

// TODO: Instead of handling errors locally, we should bubble them into the top element which is RootErrorBoundary.
//       Then we should prepare correct message and description depending on error type.
export function ErrorHandler({ children }: PropsWithChildren<unknown>): JSX.Element {
    const error = useSelector((state: RootState) => state.httpErrorHandler.error);

    if (!error) {
        return <>{children}</>;
    }

    if (!error.response) {
        throw error;
    }

    switch (error.response.status) {
        case 404:
            return <NotFound message={error.response.data.toString()} />;
        default:
            return (
                <RootErrorPage
                    message={t("error.ServerError.defaultMessage", "Internal Server Error")}
                    description={t(
                        "error.ServerError.defaultDescription",
                        "An unexpected error seems to have occurred. Please contact with system administrators.",
                    )}
                />
            );
    }
}
