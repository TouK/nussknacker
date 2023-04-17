import {bindActionCreators, Dispatch} from "redux"
import * as EspActions from "./nk"
import {Action} from "./reduxTypes"
import * as UndoRedoActions from "./undoRedoActions"

export const mapDispatchWithEspActions = function (dispatch: Dispatch<Action>) {
  return {
    actions: bindActionCreators(EspActions, dispatch),
    undoRedoActions: bindActionCreators(UndoRedoActions, dispatch),
  }
}

export default {mapDispatchWithEspActions}
