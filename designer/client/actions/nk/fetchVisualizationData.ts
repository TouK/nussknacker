import {ThunkAction} from "../reduxTypes"
import {fetchProcessToDisplay} from "./process"
import {displayProcessActivity} from "./displayProcessActivity"
import {fetchProcessDefinition} from "./processDefinitionData"
import {handleHTTPError} from "./errors"
import {loadProcessToolbarsConfiguration} from "./loadProcessToolbarsConfiguration"

export function fetchVisualizationData(processName: string): ThunkAction {
  return async dispatch => {
    try {
      //TODO: move fetchProcessToDisplay to ts
      const {fetchedProcessDetails} = await dispatch(fetchProcessToDisplay(processName)) as any
      const {name, json, processingType} = fetchedProcessDetails
      await dispatch(loadProcessToolbarsConfiguration(name))
      dispatch(displayProcessActivity(name))
      await dispatch(fetchProcessDefinition(processingType, json.properties?.isSubprocess))
      return fetchedProcessDetails
    } catch (error) {
      dispatch(handleHTTPError(error))
    }
  }
}
