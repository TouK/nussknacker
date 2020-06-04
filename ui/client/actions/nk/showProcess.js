import history from "../../history"
import * as VisualizationUrl from "../../common/VisualizationUrl"

export const goToProcess = processId => history.push(VisualizationUrl.visualizationUrl(processId))

export function showProcess(processId) {
  return (dispatch) => {
    goToProcess(processId)

    return dispatch({
      type: "SHOW_PROCESS",
      processId: processId,
    })
  }
}
