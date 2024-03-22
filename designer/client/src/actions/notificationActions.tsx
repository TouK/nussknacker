import React from "react";
import Notifications from "react-notification-system-redux";
import CheckCircleOutlinedIcon from "@mui/icons-material/CheckCircleOutlined";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import Notification from "../components/notifications/Notification";
import { Action } from "./reduxTypes";
import { alpha } from "@mui/material";

export function success(message: string): Action {
    return Notifications.success({
        autoDismiss: 10,
        children: (
            <Notification
                type={"success"}
                icon={<CheckCircleOutlinedIcon sx={(theme) => ({ color: alpha(theme.palette.common.black, 0.54) })} />}
                message={message}
            />
        ),
    });
}

// export function error(message: string): Action {
//     return Notifications.error({
//         autoDismiss: 10,
//         children: (
//             <Notification
//                 type={"error"}
//                 icon={<InfoOutlinedIcon sx={(theme) => ({ color: alpha(theme.palette.common.black, 0.54) })} />}
//                 message={message}
//             />
//         ),
//     });
// }

export function error(message: string): Action {
    return Notifications.info({
        autoDismiss: 10000,
        children: (
            <Notification
                type={"info"}
                icon={<InfoOutlinedIcon sx={(theme) => ({ color: alpha(theme.palette.common.black, 0.54) })} />}
                message={message}
            />
        ),
    });
}
