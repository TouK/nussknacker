import React from "react";
import Notifications from "react-notification-system-redux";
import CheckCircleOutlinedIcon from "@mui/icons-material/CheckCircleOutlined";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import WarningAmberOutlinedIcon from "@mui/icons-material/WarningAmberOutlined";
import Notification from "../components/notifications/Notification";
import { Action } from "./reduxTypes";

export function success(message: string): Action {
    return Notifications.success({
        autoDismiss: 10,
        children: <Notification type={"success"} icon={<CheckCircleOutlinedIcon />} message={message} />,
    });
}

export function error(message: string): Action {
    return Notifications.error({
        autoDismiss: 10,
        children: <Notification type={"error"} icon={<InfoOutlinedIcon />} message={message} />,
    });
}

export function info(message: string): Action {
    return Notifications.info({
        autoDismiss: 10,
        children: <Notification type={"info"} icon={<InfoOutlinedIcon />} message={message} />,
    });
}

export function warn(message: string): Action {
    return Notifications.warning({
        autoDismiss: 10,
        children: <Notification type={"warning"} icon={<WarningAmberOutlinedIcon />} message={message} />,
    });
}
