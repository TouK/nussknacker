import HttpService from "../../http/HttpService"
import {importProcess} from "./index"
import {reportEvent} from "./reportEvent"
import {withoutEmptyEdges} from "../../components/graph/GraphPartialsInTS/EdgeUtils";
import {Process, ProcessId} from "../../types";
import {ProcessVersionId} from "../../components/Process/types";
import {ThunkAction} from "../reduxTypes";

export function importFiles(files: File[], processId: ProcessId): ThunkAction {
  return (dispatch) => {
    files.forEach(
      file => dispatch(importProcess(processId, file)),
    )
    return {
      type: "IMPORT_FILES",
    }
  }
}

export function exportProcessToJSON(process: Process, versionId: ProcessVersionId): ThunkAction {
  return (dispatch) => {
    let noEmptyEdges = withoutEmptyEdges(process)
    HttpService.exportProcess(noEmptyEdges, versionId)

    return dispatch(reportEvent({
      category: "right_panel",
      action: "button_click",
      name: "export_to_json",
    }))
  }
}

export function exportProcessToPdf(processId, versionId, data): ThunkAction {
  return (dispatch) => {
    HttpService.exportProcessToPdf(processId, versionId, data)

    return dispatch(reportEvent({
      category: "right_panel",
      action: "button_click",
      name: "export_to_pdf",
    }))
  }
}
