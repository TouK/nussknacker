import {pathForProcess} from "../../containers/Metrics"
import history from "../../history"
import {reportEvent} from "./reportEvent"

export function showMetrics(processId) {
  return (dispatch) => {
    history.push(pathForProcess(processId))

    dispatch(reportEvent({
      category: "right_panel",
      action: "button_click",
      name: "metrics",
    }))

    return dispatch({
      type: "SHOW_METRICS",
      processId: processId,
    })
  }
}

