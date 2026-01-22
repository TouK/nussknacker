import CheckCircleOutlinedIcon from "@mui/icons-material/CheckCircleOutlined";
import DangerousOutlinedIcon from "@mui/icons-material/DangerousOutlined";
import i18next from "i18next";
import React, { useCallback, useEffect, useRef } from "react";
import { default as ReactNotifications } from "react-notification-system-redux";
import { bindActionCreators } from "redux";

import { markBackendNotificationRead, updateBackendNotifications } from "../actions/nk/notifications";
import { loadProcessState } from "../actions/nk/process";
import { fetchProcessDefinition } from "../actions/nk/processDefinitionData";
import { getScenarioActivities } from "../actions/nk/scenarioActivities";
import NotificationActions from "../actions/notificationActions";
import type { ThunkAction } from "../actions/reduxTypes";
import Notification from "../components/notifications/Notification";
import HttpService from "../http/HttpService/instance";
import { getProcessingType, getProcessName, getProcessVersionId, isFragment } from "../reducers/selectors/graph";
import { getBackendNotifications, getNotifications } from "../reducers/selectors/other";
import { useAppDispatch, useAppSelector } from "../store/storeHelpers";
import { useChangeConnectionError } from "./connectionErrorProvider/ConnectionErrorProvider";
import { useInterval } from "./Interval";

const prepareNotification =
    ({ id, message, type }: BackendNotification): ThunkAction =>
    (dispatch) => {
        if (!type) {
            //if we don't display notification, we assume that it's already processed
            return dispatch(markBackendNotificationRead(id));
        }
        return dispatch(
            ReactNotifications.show(
                {
                    autoDismiss: type == "error" ? 0 : 10,
                    uid: id,
                    children: (
                        <Notification
                            type={type}
                            icon={type == "error" ? <DangerousOutlinedIcon /> : <CheckCircleOutlinedIcon />}
                            message={message}
                        />
                    ),
                    onRemove: () => dispatch(markBackendNotificationRead(id)),
                },
                type,
            ),
        );
    };

const handleRefresh =
    (
        toRefresh: DataToRefresh,
        currentScenarioName: string,
        processVersionId: number,
        currentProcessingType: string,
        currentIsFragment: boolean,
    ): ThunkAction =>
    (dispatch) => {
        if (toRefresh.indexOf("activity") >= 0 && currentScenarioName) {
            dispatch(getScenarioActivities(currentScenarioName));
        }
        if (toRefresh.indexOf("state") >= 0 && currentScenarioName && processVersionId) {
            dispatch(loadProcessState(currentScenarioName, processVersionId));
        }
        if (toRefresh.indexOf("creator") >= 0 && currentProcessingType && currentIsFragment != null) {
            dispatch(fetchProcessDefinition(currentProcessingType, currentIsFragment));
        }
        return;
    };

const prepareNotifications =
    (
        notifications: BackendNotification[],
        scenarioName: string,
        processVersionId: number,
        currentProcessingType: string,
        currentIsFragment: boolean,
    ): ThunkAction =>
    (dispatch, getState) => {
        const state = getState();
        const { processedNotificationIds } = getBackendNotifications(state);
        const reactNotifications = getNotifications(state);

        const onlyUnreadPredicate = ({ id }: BackendNotification) => {
            const isProcessed = processedNotificationIds.includes(id);
            const isDisplayed = reactNotifications.some(({ uid }) => uid === id);
            return !isProcessed && !isDisplayed;
        };

        const unreadNotifications = notifications.filter(onlyUnreadPredicate);

        unreadNotifications.forEach((notification) => {
            dispatch(prepareNotification(notification));
        });

        const uniqueEventsToRefresh = Array.from(
            new Set(
                unreadNotifications
                    .filter((notification) => !notification.scenarioName || notification.scenarioName === scenarioName)
                    .flatMap((notification) => notification.toRefresh),
            ),
        );

        uniqueEventsToRefresh.forEach((refreshItem) => {
            dispatch(handleRefresh(refreshItem, scenarioName, processVersionId, currentProcessingType, currentIsFragment));
        });
    };

export function Notifications(): React.JSX.Element {
    const reactNotifications = useAppSelector(getNotifications);
    const dispatch = useAppDispatch();
    const { handleChangeConnectionError } = useChangeConnectionError();

    useEffect(() => HttpService.setNotificationActions(bindActionCreators(NotificationActions, dispatch)));

    const currentScenarioName = useAppSelector(getProcessName);
    const processVersionId = useAppSelector(getProcessVersionId);
    const currentProcessingType = useAppSelector(getProcessingType);
    const currentIsFragment = useAppSelector(isFragment);
    const errorShown = useRef(null);

    const refresh = useCallback(() => {
        HttpService.loadBackendNotifications(currentScenarioName)
            .then((notifications) => {
                errorShown.current = false;
                handleChangeConnectionError(null);
                dispatch(updateBackendNotifications(notifications.map(({ id }) => id)));
                dispatch(
                    prepareNotifications(notifications, currentScenarioName, processVersionId, currentProcessingType, currentIsFragment),
                );
            })
            .catch((error) => {
                const isNetworkAccess = navigator.onLine;
                const possibleServerNotAvailableHttpStatuses = [502, 503, 504];

                if (!isNetworkAccess) {
                    handleChangeConnectionError("NO_NETWORK_ACCESS");
                } else if (possibleServerNotAvailableHttpStatuses.some((status) => status === error?.response?.status)) {
                    handleChangeConnectionError("NO_BACKEND_ACCESS");
                } else {
                    if (errorShown.current) return;
                    errorShown.current = true;
                    dispatch(
                        NotificationActions.error(
                            i18next.t("notification.error.cannotFetchBackendNotifications", "Cannot fetch backend notification"),
                        ),
                    );
                }
            });
    }, [currentScenarioName, processVersionId, currentProcessingType, currentIsFragment, dispatch, handleChangeConnectionError]);
    useInterval(refresh, {
        refreshTime: 2000,
        ignoreFirst: true,
    });

    //noAnimation=false breaks onRemove somehow :/
    return <ReactNotifications notifications={reactNotifications} style={false} noAnimation={true} />;
}

type NotificationType = "info" | "error" | "success";

type DataToRefresh = "activity" | "state" | "creator";

export type BackendNotification = {
    id: string;
    type?: NotificationType;
    message?: string;
    toRefresh: DataToRefresh[];
    scenarioName?: string;
};
