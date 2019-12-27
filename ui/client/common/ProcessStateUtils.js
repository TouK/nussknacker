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
    UNKNOWN: "Unknown state of the process..",
    NOT_DEPLOYED: "The process has never been deployed.",
    DURING_DEPLOY: "The process has been already started and currently is being deployed.",
    RUNNING: "The process is running.",
    CANCELED: "The process has been successfully cancelled.",
    RESTARTING: "The process is restarting...",
    FAILED: "There are some problems with process..",
    FINISHED: "The process completed successfully.",
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
    const pendingTitle = (title) => title + (isStateLoaded === false ? " Loading current state of the process..." : "")

    const action = _.get(process, 'deployment.action', null)

    switch (action) {
      case this.ACTIONS.DEPLOY:
        return pendingTitle("The process has been successfully deployed.")
      case this.ACTIONS.CANCEL:
        return pendingTitle("The process has been successfully canceled.")
      case this.ACTIONS.NOT_DEPLOYED:
        return pendingTitle("The process has never been deployed.")
      default:
        return pendingTitle("Unknown state of the process..")
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
