import _ from 'lodash'

//TODO: display e.g. warnings, use it in Visualization (left panel)
const getStatusClass = (processState, shouldRun, statusesLoaded) => {

  const isRunning = _.get(processState, 'isRunning');
  if (isRunning) {
    return "status-running"
  } else if (shouldRun) {
    return statusesLoaded ? "status-notrunning" : "status-unknown"
  }
  return null;

}

const getStatusMessage = (processState, shouldRun, loaded) => {

  const isRunning = _.get(processState, 'isRunning');

  if (isRunning) {
    return "Running"
  } else if (shouldRun) {
    return loaded ? "Unknown state" : `Not running: ${(processState || {}).errorMessage}`
  }
  return null;

}


export {
  getStatusClass,
  getStatusMessage,
}