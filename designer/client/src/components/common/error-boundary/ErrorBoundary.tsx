import i18next from "i18next";
import type { PropsWithChildren } from "react";
import React from "react";
import type { ErrorBoundaryProps } from "react-error-boundary";
import { ErrorBoundary as ErrorBoundaryLibrary } from "react-error-boundary";

import { FullPageErrorBoundaryFallbackComponent } from "./fallbackComponent";

export const messages = {
    unexpectedErrorTitle: () => i18next.t("unexpectedError.title", "An unexpected error occurred"),
    unexpectedErrorText: () => i18next.t("unexpectedError.text", "If the problem persists, please contact the administrator."),
    sectionUnavailable: () => i18next.t("unexpectedError.sectionUnavailable", "Section unavailable."),
};

export function ErrorBoundary({ children, FallbackComponent }: PropsWithChildren<Partial<ErrorBoundaryProps>>): JSX.Element {
    return (
        <ErrorBoundaryLibrary FallbackComponent={FallbackComponent || FullPageErrorBoundaryFallbackComponent}>
            {children}
        </ErrorBoundaryLibrary>
    );
}
