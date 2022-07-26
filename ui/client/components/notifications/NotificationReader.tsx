import api from "../../api";
import {useInterval} from "../../containers/Interval";
import {useCallback, useState} from "react";
import HttpService, {HealthCheckResponse, HealthState} from "../../http/HttpService";


export function NotificationReader() : JSX.Element {

    /*
    useInterval(updateState, {refreshTime, ignoreFirst: false})

    const [{state, ...data}, setState] = useState<Notification[]>({state: HealthState.ok})
    const updateState = useCallback(async () => {
      setState(await HttpService.fetchHealthCheckProcessDeployment())
    }, [])


    if (this.notificationReload) {
      clearInterval(this.notificationReload)
    }
    //TODO: configuration?
    this.notificationReload = setInterval(() => this._loadNotifications(), 10000)

    notificationReload = null
    processedNotificationIds: string[] = []

    _loadNotifications() {

      api.get<Notification[]>("/notifications").then(response => {
          let allNotifications = response.data;
          allNotifications
              .filter(not => !this.processedNotificationIds.includes(not.id))
              .forEach(notification =>  notification.type === "info" ? this.addInfo(notification.message) : this.addErrorMessage(notification.message, null, false))
          this.processedNotificationIds = allNotifications.map(not => not.id)
      })
    }
      */


    return null;
}