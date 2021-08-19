import * as VisualizationUrl from "../../common/VisualizationUrl"
import history from "../../history"

export type CloseModalsAction = {
  type: "CLOSE_MODALS",
}

export function closeModals(): CloseModalsAction {
  history.replace({
    pathname: history.location.pathname,
    search: VisualizationUrl.setAndPreserveLocationParams({
      edgeId: null,
      nodeId: null,
    }),
  })

  return {
    type: "CLOSE_MODALS",
  }
}
