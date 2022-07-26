import React, {useCallback, useEffect} from 'react';
import Notifications, {default as ReactNotifications} from "react-notification-system-redux"
import {useDispatch, useSelector} from "react-redux"
import HttpService from "../http/HttpService"
import * as NotificationActions from "../actions/notificationActions"
import {bindActionCreators, Dispatch} from "redux";
import {getBackendNotifications, getNotifications} from "../reducers/selectors/other";
import {useInterval} from "./Interval";
import Notification from "../components/notifications/Notification";
import InlinedSvgs from "../assets/icons/InlinedSvgs";
import {v4 as uuid4} from "uuid";
import {markBackendNotificationRead, updateBackendNotifications} from "../actions/nk/notifications";

function prepare(backendNotification: BackendNotification, dispatch: Dispatch<any>) {
    return Notifications.error({
        //uid: backendNotification.id,
        message: backendNotification.message,
        autoDismiss: 0,
        children: [
            <Notification icon={InlinedSvgs.tipsError} message={backendNotification.id} key={uuid4()}/>,
        ],
        onRemove: (notification) => dispatch(markBackendNotificationRead(notification.uid.toString()))
    })
}

export function NuNotifications(): JSX.Element {

    const readNotifications = useSelector(getBackendNotifications)
    const reactNotifications = useSelector(getNotifications)
    const dispatch = useDispatch()
    useEffect(() => HttpService.setNotificationActions(bindActionCreators(NotificationActions, dispatch)))
    const onlyUnreadPredicate = (be: BackendNotification) => true//!readNotifications.processedNotificationIds.includes(be.id)

    const refresh = useCallback(() => {
        HttpService.loadBackendNotifications().then(notifications => {
            dispatch(updateBackendNotifications(notifications.map(n => n.id)))
            notifications.filter(onlyUnreadPredicate).forEach(beNotification => {
                dispatch(prepare(beNotification, dispatch))
            })
        })
    }, [])
    useInterval(refresh, {refreshTime: 2000, ignoreFirst: false})

    return <ReactNotifications notifications={reactNotifications} style={false}/>
}


export type BackendNotification = {
    id: string,
    type: "info" | "warning"
    message?: string
}
