import * as VisualizationUrl from "../common/VisualizationUrl";
import {browserHistory} from "react-router";
import _ from "lodash";

const ProcessesMixin = {
  processStatusClass: (process, statusesLoaded, statuses) => {
    const processId = process.id;
    const shouldRun = process.currentlyDeployedAt.length > 0;
    const statusesKnown = statusesLoaded;
    const isRunning = statusesKnown && _.get(statuses[processId], 'isRunning');
    if (isRunning) {
      return "status-running";
    } else if (shouldRun) {
      return statusesKnown ? "status-notrunning" : "status-unknown"
    } else return null;
  },

  processStatusTitle: processStatusClass => {
    if (processStatusClass === "status-running") {
      return "Running"
    } else if (processStatusClass === "status-notrunning") {
      return "Not running"
    } else if (processStatusClass === "status-unknown") {
      return "Unknown state"
    } else {
      return null
    }
  },

  showMetrics: process => {
    browserHistory.push('/metrics/' + process.id)
  },

  showProcess: process => {
    browserHistory.push(VisualizationUrl.visualizationUrl(process.id))
  }
}

export default ProcessesMixin