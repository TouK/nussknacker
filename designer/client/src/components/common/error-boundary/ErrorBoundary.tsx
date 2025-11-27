import i18next from "i18next";
import React from "react";
import type { ErrorBoundaryProps } from "react-error-boundary";
import { ErrorBoundary as ErrorBoundaryLibrary } from "react-error-boundary";

import { FullPageErrorBoundaryFallbackComponent } from "./fallbackComponent/FullPageErrorBoundaryFallbackComponent";

export const messages = {
    unexpectedErrorTitle: () => i18next.t("unexpectedError.title", "An unexpected error occurred"),
    unexpectedErrorText: () => i18next.t("unexpectedError.text", "If the problem persists, please contact the administrator."),
    sectionUnavailable: () => i18next.t("unexpectedError.sectionUnavailable", "Section unavailable."),
};

export function ErrorBoundary({
    children,
    FallbackComponent,
}: Partial<Pick<ErrorBoundaryProps, "children" | "FallbackComponent">>): React.JSX.Element {
    return (
        <ErrorBoundaryLibrary FallbackComponent={FallbackComponent || FullPageErrorBoundaryFallbackComponent}>
            {children}
        </ErrorBoundaryLibrary>
    );
}
