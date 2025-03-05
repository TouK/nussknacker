import React, { ReactElement } from "react";
import { Alert, AlertColor } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import { CopyTooltip } from "./copyTooltip";
import { useTranslation } from "react-i18next";

interface Props {
    icon: ReactElement;
    message?: string;
    details?: string;
    type: AlertColor;
}

export default function Notification({ icon, message, type }: Props): JSX.Element {
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
