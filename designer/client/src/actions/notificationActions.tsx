import React from "react";
import Notifications from "react-notification-system-redux";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Error";
import InfoIcon from "@mui/icons-material/Info";
import Notification from "../components/notifications/Notification";
import { Action } from "./reduxTypes";

export function success(message: string): Action {
    return Notifications.success({
        autoDismiss: 10,
        children: (
            <Notification
                type={"success"}
                icon={<CheckCircleIcon sx={{ color: "#333333", alignSelf: "center" }} fontSize="inherit" />}
                message={message}
            />
        ),
    });
}

export function error(message: string, error?: string, showErrorText?: boolean): Action {
    const details = showErrorText && error ? error : null;
    return Notifications.error({
        autoDismiss: 10,
        children: (
            <Notification
                type={"error"}
                icon={<ErrorIcon sx={{ color: "#333333", alignSelf: "center" }} fontSize="inherit" />}
                message={message}
                details={details}
            />
        ),
    });
}

export function info(message: string): Action {
    return Notifications.info({
        autoDismiss: 100,
        children: (
            <Notification
                type={"info"}
                icon={<InfoIcon sx={{ color: "#333333", alignSelf: "center" }} fontSize="inherit" />}
                message={message}
            />
        ),
    });
}
