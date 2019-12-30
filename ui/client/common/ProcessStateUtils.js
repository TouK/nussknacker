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
    DEPLOYED: "DEPLOYED",
    RUNNING: "RUNNING",
    CANCELED: "CANCELED",
    RESTARTING: "RESTARTING",
    FAILED: "FAILED",
    ERROR: "ERROR",
    FINISHED: "FINISHED",
  },

  STATUS_TOOLTIPS: {
    UNKNOWN: "Unknown state of the process..",
    NOT_DEPLOYED: "The process has never been deployed.",
    DURING_DEPLOY: "The process has been already started and currently is being deployed.",
    DEPLOYED: "The process has been successfully deployed.",
    RUNNING: "The process is running.",
    CANCELED: "The process has been successfully cancelled.",
    RESTARTING: "The process is restarting..",
    FAILED: "There are some problems with checking state of process..",
    ERROR: "There are some errors with state of process..",
    FINISHED: "The process completed successfully.",
  },

  STATUS_ICONS: {
    UNKNOWN: InlinedSvgs.iconUnknown,
    NOT_DEPLOYED: InlinedSvgs.iconNotDeployed,
    DURING_DEPLOY: InlinedSvgs.iconDeployRunningAnimated,
    DEPLOYED: InlinedSvgs.iconDeploySuccess,
    RUNNING: InlinedSvgs.iconRunningAnimated,
    CANCELED: InlinedSvgs.iconStoppingSuccess,
    RESTARTING: InlinedSvgs.iconStoppedWorking,
    FAILED: InlinedSvgs.iconFailed,
    ERROR: InlinedSvgs.iconStoppedWorking,
    FINISHED: InlinedSvgs.iconSuccess,
  },

  isRunning(state) {
    return this.getStateStatus(state) === this.STATUSES.RUNNING
  },

  isDeployed(process) {
    return this.getProcessAction(process) === this.ACTIONS.DEPLOY
  },

  getStateTooltip(state) {
    return this.getStatusTooltip(this.getStateStatus(state))
  },

  getStatusTooltip(status) {
    return _.get(this.STATUS_TOOLTIPS, status, this.STATUS_TOOLTIPS[this.STATUSES.UNKNOWN])
  },

  getStateIcon(state) {
    return this.getStatusIcon(this.getStateStatus(state))
  },

  getStatusIcon(status) {
    return _.get(this.STATUS_ICONS, status, this.STATUS_ICONS[this.STATUSES.UNKNOWN])
  },

  getStateStatus(state) {
    const status = _.get(state, 'status', null)

    if (_.isUndefined(state) || (state && status === null)) {
      return this.STATUSES.UNKNOWN
    }

    //TODO: In future it will be nice when API return state with status CANCELED
    if (_.isNull(state)) {
      return this.STATUSES.CANCELED
    }

    return state.status.toUpperCase()
  },

  getProcessStateStatus(process, state) {
    if (state == null) {
      return this.getProcessStatus(process)
    }

    return this.getStateStatus(state)
  },

  getProcessAction(process) {
    return _.get(process, 'deployment.action', null)
  },

  getProcessTooltip(process) {
    return this.getStatusTooltip(this.getProcessStatus(process))
  },

  getProcessStatus(process) {
    const action = this.getProcessAction(process)

    switch(action) {
      case this.ACTIONS.DEPLOY:
        return this.STATUSES.DEPLOYED
      case this.ACTIONS.CANCEL:
        return this.STATUSES.CANCELED
      case this.ACTIONS.NOT_DEPLOYED:
        return this.STATUSES.NOT_DEPLOYED
      default:
        return this.STATUSES.UNKNOWN
    }
  },

  getProcessIcon(process) {
    return this.getStatusIcon(this.getProcessStatus(process))
  }
}
