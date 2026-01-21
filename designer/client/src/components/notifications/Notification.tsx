import CloseIcon from "@mui/icons-material/Close";
import type { AlertColor } from "@mui/material";
import { Alert, Button, Collapse, Typography } from "@mui/material";
import type { ReactElement } from "react";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import { CopyTooltip } from "./copyTooltip";

interface Props {
    icon: ReactElement;
    message?: string;
    details?: string;
    type: AlertColor;
}

export default function Notification({ icon, message, details, type }: Props): React.JSX.Element {
    const { t } = useTranslation();
    const [showDetails, setShowDetails] = useState(false);

    const alertContent = (
        <Alert icon={icon} severity={type} action={<CloseIcon sx={{ fontSize: 12 }} />}>
            {message}
            {details && (
                <>
                    <Button
                        size="small"
                        onClick={(e) => {
                            e.stopPropagation();
                            setShowDetails((prev) => !prev);
                        }}
                        sx={{ textTransform: "none", p: 0, mt: 1, minWidth: "auto", fontWeight: "bold", color: "currentcolor" }}
                    >
                        {showDetails ? t("notification.hideDetails", "Hide details") : t("notification.showDetails", "Show details")}
                    </Button>
                    <Collapse in={showDetails}>
                        <Typography variant={"body2"} style={{ marginTop: 8 }}>
                            {details}
                        </Typography>
                    </Collapse>
                </>
            )}
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
