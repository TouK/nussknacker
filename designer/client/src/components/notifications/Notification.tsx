import React, { ReactElement } from "react";
import { Alert, AlertColor, Snackbar } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";

interface Props {
    icon: ReactElement;
    message?: string;
    details?: string;
    type: AlertColor;
    open?: boolean;
}

export default function Notification({ open, icon, message, details, type }: Props): JSX.Element {
    return (
        <Snackbar key={message} anchorOrigin={{ vertical: "bottom", horizontal: "right" }} open={open}>
            <Alert icon={icon} severity={type} action={<CloseIcon fontSize="small" />}>
                {message}
                <br />
                {details}
            </Alert>
        </Snackbar>
    );
}
