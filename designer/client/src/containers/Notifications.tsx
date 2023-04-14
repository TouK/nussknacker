import React, {useCallback, useEffect} from "react"
import {default as ReactNotifications} from "react-notification-system-redux"
import {useDispatch, useSelector} from "react-redux"
import HttpService from "../http/HttpService"
import * as NotificationActions from "../actions/notificationActions"
import {bindActionCreators, Dispatch} from "redux"
import {getBackendNotifications, getNotifications} from "../reducers/selectors/other"
import {useInterval} from "./Interval"
import Notification from "../components/notifications/Notification"
import {ReactComponent as TipsSuccess} from "../assets/img/icons/tipsSuccess.svg"
import {ReactComponent as TipsError} from "../assets/img/icons/tipsError.svg"
import {v4 as uuid4} from "uuid"
import {markBackendNotificationRead, updateBackendNotifications} from "../actions/nk/notifications"
import {displayCurrentProcessVersion, displayProcessActivity, loadProcessState} from "../actions/nk"
import {getProcessId} from "../reducers/selectors/graph"

function prepareNotification(backendNotification: BackendNotification, dispatch: Dispatch<any>) {
  const autoDismiss = backendNotification.type == "error" ? 0 : 10
  const icon = backendNotification.type == "error" ? <TipsError/> : <TipsSuccess/>
  return ReactNotifications.show({
    autoDismiss: autoDismiss,
    uid: backendNotification.id,
    children: [
      <Notification icon={icon} message={backendNotification.message} key={uuid4()}/>,
    ],
    onRemove: () => {
      dispatch(markBackendNotificationRead(backendNotification.id))
    },
  }, backendNotification.type)
}

function handleRefresh(beNotification: BackendNotification, currentScenarioName, dispatch: Dispatch<any>) {
  const scenarioName = beNotification.scenarioName
  if (scenarioName && scenarioName == currentScenarioName) {
    beNotification.toRefresh.forEach(data => {
      switch (data) {
        case "versions":
          dispatch(displayCurrentProcessVersion(beNotification.scenarioName))
          break
        case "activity":
          dispatch(displayProcessActivity(beNotification.scenarioName))
          break
        case "state":
          dispatch(loadProcessState(beNotification.scenarioName))
      }
    })

  }
}

export function Notifications(): JSX.Element {

  const readNotifications = useSelector(getBackendNotifications)
  const reactNotifications = useSelector(getNotifications)
  const dispatch = useDispatch()
  useEffect(() => HttpService.setNotificationActions(bindActionCreators(NotificationActions, dispatch)))

  const currentScenarioName = useSelector(getProcessId)

  const refresh = useCallback(() => {
    const onlyUnreadPredicate = (be: BackendNotification) => !readNotifications.processedNotificationIds.includes(be.id) && !reactNotifications.map(k => k.uid).includes(be.id)

    HttpService.loadBackendNotifications().then(notifications => {
      dispatch(updateBackendNotifications(notifications.map(n => n.id)))
      notifications.filter(onlyUnreadPredicate).forEach(beNotification => {
        if (beNotification.type) {
          dispatch(prepareNotification(beNotification, dispatch))
        } else {
          //if we don't display notification, we assume that it's already processes
          dispatch(markBackendNotificationRead(beNotification.id))
        }
        handleRefresh(beNotification, currentScenarioName, dispatch)
      })
    })
  }, [readNotifications])
  useInterval(refresh, {refreshTime: 2000, ignoreFirst: true})

  //noAnimation=false breaks onRemove somehow :/
  return <ReactNotifications notifications={reactNotifications} style={false} noAnimation={true}/>
}

export type BackendNotification = {
  id: string,
  type?: "info" | "error" | "success",
  message?: string,
  toRefresh: DataToRefresh[],
  scenarioName?: string,
}

export type DataToRefresh = "versions" | "activity" | "state"
