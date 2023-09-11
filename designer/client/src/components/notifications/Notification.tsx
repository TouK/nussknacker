import React, { ReactElement } from "react";
import { Alert, AlertColor, Snackbar } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";

interface Props {
    icon: ReactElement;
    message?: string;
    details?: string;
    type: AlertColor;
}

export default function Notification({ icon, message, details, type }: Props): JSX.Element {
    return (
        <Snackbar
            sx={{ minWidth: 290 }}
            anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
            open={true}
            autoHideDuration={10000}
            onClose={() => ({})}
            key={"bottom" + "right"}
        >
            <Alert icon={icon} sx={{ width: "100%" }} onClose={() => ({})} severity={type} action={<CloseIcon fontSize="small" />}>
                {message}
                <br />
                {details}
            </Alert>
        </Snackbar>
    );
}
