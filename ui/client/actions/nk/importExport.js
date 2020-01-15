import HttpService from "../../http/HttpService"
import {importProcess} from "./index"
import {reportEvent} from "./reportEvent"

export function importFiles(files, processId) {
  return (dispatch) => {
    files.forEach(
        file => dispatch(importProcess(processId, file)),
    )

    return ({
      type: "IMPORT_FILES",
    })
  }
}

export function exportProcessToJSON(process, versionId) {
  return (dispatch) => {
    HttpService.exportProcess(process, versionId)

    dispatch(reportEvent({
      category: "right_panel",
      action: "button_click",
      name: "export_to_json",
    }))

    return dispatch({
      type: "EXPORT_PROCESS_TO_JSON",
    })
  }
}

export function exportProcessToPdf(processId, versionId, data, businessView) {
  return (dispatch) => {
    HttpService.exportProcessToPdf(processId, versionId, data, businessView)

    dispatch(reportEvent({
      category: "right_panel",
      action: "button_click",
      name: "export_to_pdf",
    }))

    return dispatch({
      type: "EXPORT_PROCESS_TO_PDF",
    })
  }
}
