import { combineReducers } from 'redux';
import _ from 'lodash'

import UndoRedoReducer from '../undoredo/UndoRedoReducer'
import { reducer as settings } from './settings';
import { reducer as ui } from './ui';
import { reducer as graph } from './graph';
import { reducer as processActivity } from './processActivity';

const espUndoableConfig = {
  blacklist: ["CLEAR_PROCESS", "PROCESS_LOADING", "URL_CHANGED"]
}

export const reducer = combineReducers({
  graphReducer: UndoRedoReducer.espUndoable(graph, espUndoableConfig),
  settings,
  ui,
  processActivity,
});

export default reducer;
