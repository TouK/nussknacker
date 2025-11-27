import CloseIcon from "@mui/icons-material/Close";
import type { AlertColor } from "@mui/material";
import { Alert } from "@mui/material";
import type { ReactElement } from "react";
import React from "react";
import { useTranslation } from "react-i18next";

import { CopyTooltip } from "./copyTooltip";

interface Props {
    icon: ReactElement;
    message?: string;
    details?: string;
    type: AlertColor;
}

export default function Notification({ icon, message, type }: Props): React.JSX.Element {
    const { t } = useTranslation();

    const alertContent = (
        <Alert icon={icon} severity={type} action={<CloseIcon sx={{ fontSize: 12 }} />}>
            {message}
        </Alert>
    );

    return type === "error" ? (
        <CopyTooltip text={message} title={t("error.copyMessage", "Copy message to clipboard")}>
            {alertContent}
        </CopyTooltip>
    ) : (
        alertContent
    );
}
