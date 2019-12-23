import _ from 'lodash'

export default {
  ACTION_DEPLOY: "DEPLOY",
  ACTION_CANCEL: "CANCEL",

  isDeployed(process) {
    const action = _.get(process, 'deployment.action')
    return action != null && action.toUpperCase() === this.ACTION_DEPLOY
  },

  isCanceled(deployment) {
    const action = _.get(deployment, 'deployment.action')
    return action != null && action.toUpperCase() === this.ACTION_CANCEL
  },

  //TODO: display e.g. warnings, use it in Visualization (left panel)
  getStatusClass(processState, shouldRun, statusesLoaded) {
    const isRunning = _.get(processState, 'isRunning')
    if (isRunning) {
      return "status-running"
    } else if (shouldRun) {
      return statusesLoaded ? "status-notrunning" : "status-unknown"
    }
    return null
  },

  getStatusMessage(processState, shouldRun, loaded) {
    const isRunning = _.get(processState, 'isRunning')
    if (isRunning) {
      return "Running"
    } else if (shouldRun) {
      const message = (processState || {}).errorMessage || "Not found in engine"
      return loaded ? `Not running: ${message}` : "Unknown state"
    }

    return null
  }
}
