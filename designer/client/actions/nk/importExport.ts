import HttpService from "../../http/HttpService"
import {importProcess} from "./index"
import {withoutHackOfEmptyEdges} from "../../components/graph/GraphPartialsInTS/EdgeUtils"
import {Process, ProcessId} from "../../types"
import {ProcessVersionId} from "../../components/Process/types"
import {ThunkAction} from "../reduxTypes"

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

