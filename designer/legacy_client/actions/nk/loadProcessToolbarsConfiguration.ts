import {ThunkAction} from "../reduxTypes"
import HttpService from "../../http/HttpService"

export function loadProcessToolbarsConfiguration(processId): ThunkAction {
  return (dispatch) =>
    HttpService.fetchProcessToolbarsConfiguration(processId).then((response) => dispatch({
      type: "PROCESS_TOOLBARS_CONFIGURATION_LOADED",
      data: response.data,
    }))
}
