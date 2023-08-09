import React from "react";
import Notifications from "react-notification-system-redux";
import TipsError from "../assets/img/icons/tipsError.svg";
import TipsInfo from "../assets/img/icons/tipsInfo.svg";
import TipsSuccess from "../assets/img/icons/tipsWarning.svg";
import Notification from "../components/notifications/Notification";
import { Action } from "./reduxTypes";

export function success(message: string): Action {
    return Notifications.success({
        autoDismiss: 10,
        children: <Notification icon={<TipsSuccess />} message={message} />,
    });
}

export function error(message: string, error?: string, showErrorText?: boolean): Action {
    const details = showErrorText && error ? error : null;
    return Notifications.error({
        autoDismiss: 10,
        children: <Notification icon={<TipsError />} message={message} details={details} />,
    });
}

export function info(message: string): Action {
    return Notifications.info({
        autoDismiss: 10,
        children: <Notification icon={<TipsInfo />} message={message} />,
    });
}
