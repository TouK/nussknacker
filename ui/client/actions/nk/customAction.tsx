import {reportEvent} from "./reportEvent";

export function customAction(processId) {
  return (dispatch) => {
    dispatch(reportEvent({
      category: "right_panel",
      action: "button_click",
      name: "custom-action",
    }))

    return dispatch({
      type: "CUSTOM_ACTION",
      processId: processId,
    })
  }
}