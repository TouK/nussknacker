import _ from 'lodash'

import InlinedSvgs from "../assets/icons/InlinedSvgs"

export default {
  ACTIONS: {
    NOT_DEPLOYED: null,
    DEPLOY: "DEPLOY",
    CANCEL: "CANCEL",
  },

  STATUSES: {
    UNKNOWN: "UNKNOWN",
    NOT_DEPLOYED: "NOT_DEPLOYED",
    DURING_DEPLOY: "DURING_DEPLOY",
    RUNNING: "RUNNING",
    CANCELED: "CANCELED",
    RESTARTING: "RESTARTING",
    FAILED: "FAILED",
    FINISHED: "FINISHED",
  },

  TOOLTIPS: {
    UNKNOWN: "Unknown state of process.",
    NOT_DEPLOYED: "Process has been never deployed.",
    DURING_DEPLOY: "Process has been already started and currently is during deploying.",
    RUNNING: "Process successful deployed and currently is running.",
    CANCELED: "Process is successfully canceled.",
    RESTARTING: "Process is restarting...",
    FAILED: "There are some problems with process..",
    FINISHED: "Process completed successfully.",
  },

  STATUS_ICONS: {
    UNKNOWN: InlinedSvgs.iconUnknown,
    NOT_DEPLOYED: InlinedSvgs.iconNotDeployed,
    DURING_DEPLOY: InlinedSvgs.iconDeployRunningAnimated,
    RUNNING: InlinedSvgs.iconRunningAnimated,
    CANCELED: InlinedSvgs.iconStoppingSuccess,
    RESTARTING: InlinedSvgs.iconStoppedWorking,
    FAILED: InlinedSvgs.iconFailed,
    FINISHED: InlinedSvgs.iconSuccess,
  },

  isRunning(state) {
    return state != null && state.status.toUpperCase() === this.STATUSES.RUNNING
  },

  isStopped(state) {
    return state != null && state.status.toUpperCase() === this.STATUSES.CANCELED
  },

  isDeployed(process) {
    const action = _.get(process, 'deployment.action')
    return action != null && action.toUpperCase() === this.ACTIONS.DEPLOY
  },

  isCanceled(process) {
    const action = _.get(process, 'deployment.action')
    return action != null && action.toUpperCase() === this.ACTIONS.CANCEL
  },

  getStateTooltip(state) {
    const status = this.getStateStatus(state)
    return _.get(this.TOOLTIPS, status, this.TOOLTIPS[this.STATUSES.UNKNOWN])
  },

  getStateIcon(state) {
    const status = this.getStateStatus(state)
    return _.get(this.STATUS_ICONS, status, InlinedSvgs.iconUnknown)
  },

  getStateStatus(state) {
    if (_.isUndefined(state)) {
      return this.STATUSES.UNKNOWN
    }

    if (_.isNull(state)) {
      return this.STATUSES.CANCELED
    }

    return state.status
  },

  getProcessTooltip(process, isStateLoaded) {
    const pendingTitle = (title) => title + (isStateLoaded === false ? " Loading actually state of process..." : "")

    const action = _.get(process, 'deployment.action', null)

    switch (action) {
      case this.ACTIONS.DEPLOY:
        return pendingTitle("Process was successfully deployed.")
      case this.ACTIONS.CANCEL:
        return pendingTitle("Process was successfully canceled.")
      case this.ACTIONS.NOT_DEPLOYED:
        return pendingTitle("Process was never deployed.")
      default:
        return pendingTitle("Unknown state of process.")
    }
  },

  getProcessIcon(process) {
    const action = _.get(process, 'deployment.action', null)

    switch(action) {
      case this.ACTIONS.DEPLOY:
        return InlinedSvgs.iconDeploySuccess
      case this.ACTIONS.CANCEL:
        return InlinedSvgs.iconStoppingSuccess
      case this.ACTIONS.NOT_DEPLOYED:
        return InlinedSvgs.iconNotDeployed
      default:
        return InlinedSvgs.iconUnknown
    }
  }
}
