import _ from "lodash"

import {BACKEND_STATIC_URL} from "../config"
import iconDeployed from "../assets/img/states/deployed.svg"
import iconNotDeployed from "../assets/img/states/not-deployed.svg"
import iconUnknown from "../assets/img/states/state-unknown.svg"
import iconErrorState from "../assets/img/states/state-error.svg"
import iconCanceled from "../assets/img/states/canceled.svg"
import urljoin from "url-join"

const localIconPatternRegexp = /^((http|https|ftp):\/\/)/

export default {
  STATUSES: {
    UNKNOWN: "UNKNOWN",
    NOT_DEPLOYED: "NOT_DEPLOYED",
    RUNNING: "RUNNING",
    CANCELED: "CANCELED",
    ERROR: "ERROR"
  },

  ACTIONS: {
    NOT_DEPLOYED: null,
    DEPLOY: "DEPLOY",
    CANCEL: "CANCEL"
  },

  DEFAULT_STATUS_TOOLTIPS: {
    UNKNOWN: "Unknown state of the process..",
    NOT_DEPLOYED: "The process has never been deployed.",
    RUNNING: "The process has been successfully deployed.",
    CANCELED: "The process has been successfully cancelled.",
    ERROR: "There are some errors with state of process..",
  },

  DEFAULT_STATUS_ICONS: {
    UNKNOWN: iconUnknown,
    NOT_DEPLOYED: iconNotDeployed,
    RUNNING: iconDeployed,
    CANCELED: iconCanceled,
    ERROR: iconErrorState
  },

  isRunning(state) {
    return this.getStateStatus(state) === this.STATUSES.RUNNING
  },

  isDeployed(process) {
    return this.getProcessAction(process) === this.ACTIONS.DEPLOY
  },

  getStatusTooltip(status) {
    return _.get(this.DEFAULT_STATUS_TOOLTIPS, status, this.DEFAULT_STATUS_TOOLTIPS[this.STATUSES.UNKNOWN])
  },

  getStatusIcon(status) {
    return _.get(this.DEFAULT_STATUS_ICONS, status, this.DEFAULT_STATUS_ICONS[this.STATUSES.UNKNOWN])
  },

  getStateStatus(state) {
    const status = _.get(state, "status", null)

    if (status === null) {
      return this.STATUSES.UNKNOWN
    }

    return state.status.toUpperCase()
  },

  getStateIcon(state) {
    const icon = _.get(state, "icon")

    if (icon == null) {
      return this.getStatusIcon(this.getStateStatus(state))
    }

    //When icon doesn't include http / https / ftp then we assume it should be served from our backend static url
    if (!localIconPatternRegexp.test(icon)) {
      return urljoin(BACKEND_STATIC_URL, icon)
    }

    return icon
  },

  getStateTooltip(state) {
    const errors = _.get(state, "errorMessage")
    const tooltip = _.get(state, "tooltip")

    if (tooltip) {
      return tooltip + (errors != null ? errors : "")
    }

    return this.getStatusTooltip(this.getStateStatus(state))
  },

  getProcessAction(process) {
    return _.get(process, "deployment.action", null)
  },

  getProcessTooltip(process) {
    return this.getStatusTooltip(this.mapProcessDeploymentToStatus(process))
  },

  getProcessIcon(process) {
    return this.getStatusIcon(this.mapProcessDeploymentToStatus(process))
  },

  /**
   * Function map process deployment action to state status,
   * because it's easier to show default icon / tooltip by status
   *
   * @param process
   * @returns {string}
   */
  mapProcessDeploymentToStatus(process) {
    const action = this.getProcessAction(process)

    switch(action) {
      case this.ACTIONS.DEPLOY:
        return this.STATUSES.RUNNING
      case this.ACTIONS.CANCEL:
        return this.STATUSES.CANCELED
      case this.ACTIONS.NOT_DEPLOYED:
        return this.STATUSES.NOT_DEPLOYED
      default:
        return this.STATUSES.UNKNOWN
    }
  }
}
