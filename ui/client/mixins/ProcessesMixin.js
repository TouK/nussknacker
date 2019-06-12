import * as VisualizationUrl from "../common/VisualizationUrl";
import _ from "lodash";
import history from "./../history"
import Processes from "../containers/Processes";

const ProcessesMixin = {
  processStatusClass: (process, statusesLoaded, statuses) => {
    const processName = process.name;
    const shouldRun = process.currentlyDeployedAt.length > 0;
    const statusesKnown = statusesLoaded;
    const isRunning = statusesKnown && _.get(statuses[processName], 'isRunning');
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

  showMetrics: (process) => {
    history.push('/metrics/' + process.name);
  },

  showProcess: (process) => {
    history.push(VisualizationUrl.visualizationUrl(Processes.path, process.name));
  }
};

export default ProcessesMixin