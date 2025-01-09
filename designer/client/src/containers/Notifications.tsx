import React, { useCallback, useEffect } from "react";
import { default as ReactNotifications } from "react-notification-system-redux";
import { useDispatch, useSelector } from "react-redux";
import HttpService from "../http/HttpService";
import * as NotificationActions from "../actions/notificationActions";
import { bindActionCreators } from "redux";
import { getBackendNotifications, getNotifications } from "../reducers/selectors/other";
import { useInterval } from "./Interval";
import Notification from "../components/notifications/Notification";
import CheckCircleOutlinedIcon from "@mui/icons-material/CheckCircleOutlined";
import DangerousOutlinedIcon from "@mui/icons-material/DangerousOutlined";
import { markBackendNotificationRead, updateBackendNotifications } from "../actions/nk/notifications";
import { fetchProcessDefinition, loadProcessState } from "../actions/nk";
import { getProcessingType, getProcessName, getProcessVersionId, isFragment } from "../reducers/selectors/graph";
import { useChangeConnectionError } from "./connectionErrorProvider";
import i18next from "i18next";
import { ThunkAction } from "../actions/reduxTypes";
import { getScenarioActivities } from "../actions/nk/scenarioActivities";

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
        { scenarioName, toRefresh }: BackendNotification,
        currentScenarioName: string,
        processVersionId: number,
        currentProcessingType: string,
        currentIsFragment: boolean,
    ): ThunkAction =>
    (dispatch) => {
        if (scenarioName && scenarioName !== currentScenarioName) {
            return;
        }
        toRefresh.forEach((data) => {
            switch (data) {
                case "activity":
                    return dispatch(getScenarioActivities(scenarioName));
                case "state":
                    return dispatch(loadProcessState(scenarioName, processVersionId));
                case "creator":
                    return dispatch(fetchProcessDefinition(currentProcessingType, currentIsFragment));
            }
        });
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

        notifications.filter(onlyUnreadPredicate).forEach((notification) => {
            dispatch(prepareNotification(notification));
            dispatch(handleRefresh(notification, scenarioName, processVersionId, currentProcessingType, currentIsFragment));
        });
    };

export function Notifications(): JSX.Element {
    const reactNotifications = useSelector(getNotifications);
    const dispatch = useDispatch();
    const { handleChangeConnectionError } = useChangeConnectionError();

    useEffect(() => HttpService.setNotificationActions(bindActionCreators(NotificationActions, dispatch)));

    const currentScenarioName = useSelector(getProcessName);
    const processVersionId = useSelector(getProcessVersionId);
    const currentProcessingType = useSelector(getProcessingType);
    const currentIsFragment = useSelector(isFragment);

    const refresh = useCallback(() => {
        HttpService.loadBackendNotifications(currentScenarioName)
            .then((notifications) => {
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
