import {reportEvent} from "./reportEvent"

export function showMetrics(processId) {
  return (dispatch) => {
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

