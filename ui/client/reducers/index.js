import {reducer as notifications} from "react-notification-system-redux"
import {combineReducers} from "redux"
import {reducer as graph} from "./graph"
import {reducer as httpErrorHandler} from "./httpErrorHandler"
import {reducer as processActivity} from "./processActivity"
import {reducer as settings} from "./settings"
import {reducer as ui} from "./ui"

import {undoRedoReducer as undoRedo} from "./undoRedo"

export const reducer = combineReducers({
  httpErrorHandler,
  graphReducer: undoRedo(graph),
  settings,
  ui,
  processActivity,
  notifications,
})

export default reducer
