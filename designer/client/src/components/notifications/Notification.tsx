import React, { ReactElement } from "react";
import { Alert, AlertColor } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";

interface Props {
    icon: ReactElement;
    message?: string;
    details?: string;
    type: AlertColor;
}

export default function Notification({ icon, message, type }: Props): JSX.Element {
    return (
        <Alert icon={icon} severity={type} action={<CloseIcon sx={{ fontSize: 12 }} />}>
            {message}
        </Alert>
    );
}
