import {combineReducers} from 'redux'

import {undoRedoReducer as undoRedo} from './undoRedo'
import {reducer as settings} from './settings'
import {reducer as ui} from './ui'
import {reducer as graph} from './graph'
import {reducer as processActivity} from './processActivity'
import {reducer as httpErrorHandler} from './httpErrorHandler'

export const reducer = combineReducers({
  httpErrorHandler,
  graphReducer: undoRedo(graph),
  settings,
  ui,
  processActivity
})

export default reducer
