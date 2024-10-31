import React from "react";
import { useTranslation } from "react-i18next";
import WarningNotFound from "./images/warning-occurred.svg";
import { DefaultFullScreenMessage } from "./DefaultFullScreenMessage";

export function NotFound(props: { message?: string }): JSX.Element {
    const { t } = useTranslation();

    const message = props.message || t("error.NotFound.defaultMessage", "That page can’t be found...");
    const description = t(
        "error.NotFound.description",
        "It looks like nothing was found at this location.\n" +
            "Maybe try one of the links in the menu or press back to go to the previous page.",
    );

    return <DefaultFullScreenMessage message={message} description={description} Image={WarningNotFound} />;
}
