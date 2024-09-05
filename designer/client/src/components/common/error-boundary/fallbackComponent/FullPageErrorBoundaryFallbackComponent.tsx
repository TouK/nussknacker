import { t } from "i18next";
import { LoadingButton } from "../../../../windowManager/LoadingButton";
import React from "react";
import { DefaultFullScreenMessage } from "../DefaultFullScreenMessage";

export const FullPageErrorBoundaryFallbackComponent = () => {
    return (
        <DefaultFullScreenMessage
            message={t("error.UnexpectedError.message", "Unexpected error occurred")}
            description={t(
                "error.UnexpectedError.description",
                "Please refresh the page. If the problem persists, please contact your system administrator.",
            )}
        >
            <LoadingButton title={t("InitializeError.buttonLabel", "Refresh the page")} action={() => window.location.reload()} />
        </DefaultFullScreenMessage>
    );
};
