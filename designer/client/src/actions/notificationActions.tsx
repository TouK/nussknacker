import React from "react";
import Notifications from "react-notification-system-redux";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import DangerousIcon from "@mui/icons-material/Dangerous";
import InfoIcon from "@mui/icons-material/Info";
import Notification from "../components/notifications/Notification";
import { Action } from "./reduxTypes";

export function success(message: string): Action {
    return Notifications.success({
        autoDismiss: 10,
        children: <Notification type={"success"} icon={<CheckCircleIcon />} message={message} />,
    });
}

export function error(message: string): Action {
    return Notifications.error({
        autoDismiss: 10,
        children: <Notification type={"error"} icon={<DangerousIcon />} message={message} />,
    });
}

export function info(message: string): Action {
    return Notifications.info({
        autoDismiss: 10,
        children: <Notification type={"info"} icon={<InfoIcon />} message={message} />,
    });
}
