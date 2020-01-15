import _ from "lodash"

// TODO: all this mappings of tooltips, icons etc. below are walk-around for missing first fetch of process states,
//       and should be moved to backend when it will be fixed eventually
const iconCanceled = "/assets/states/stopping-success.svg"
const iconDeployed = "/assets/states/deploy-success.svg"
const iconNotDeployed = "/assets/states/not-deployed.svg"
const iconErrorState = "/assets/states/error.svg"
const iconUnknown = "/assets/states/status-unknown.svg"

import {absoluteBePath} from "./UrlUtils"

export default {
  STATUSES: {
    UNKNOWN: "UNKNOWN",
    NOT_DEPLOYED: "NOT_DEPLOYED",
    RUNNING: "RUNNING",
    CANCELED: "CANCELED",
    ERROR: "ERROR",
  },

  ACTIONS: {
    NOT_DEPLOYED: null,
    DEPLOY: "DEPLOY",
    CANCEL: "CANCEL",
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
    ERROR: iconErrorState,
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
    return absoluteBePath(_.get(this.DEFAULT_STATUS_ICONS, status, this.DEFAULT_STATUS_ICONS[this.STATUSES.UNKNOWN]))
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

    return absoluteBePath(icon)
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
  },
}
