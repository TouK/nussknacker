import React from "react";
import Notifications from "react-notification-system-redux";
import CheckCircleOutlinedIcon from "@mui/icons-material/CheckCircleOutlined";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import Notification from "../components/notifications/Notification";
import { Action } from "./reduxTypes";

export function success(message: string): Action {
    return Notifications.success({
        autoDismiss: 10,
        children: <Notification type={"success"} icon={<CheckCircleOutlinedIcon />} message={message} />,
    });
}

//TODO please take a look at this method and my changes, am I wrong or was it incomplete (without `error` and `showErrorText`) and had incomplete logic
export function error(message: string, error?: string, showErrorText?: boolean): Action {
    return Notifications.error({
        autoDismiss: 10,
        children: <Notification type={"error"} icon={<InfoOutlinedIcon />} message={showErrorText && error ? error : message} />,
    });
}

export function info(message: string): Action {
    return Notifications.info({
        autoDismiss: 10,
        children: <Notification type={"info"} icon={<InfoOutlinedIcon />} message={message} />,
    });
}
