import {bindActionCreators, Dispatch} from "redux"
import * as EspActions from "./nk"
import * as NotificationActions from "./notificationActions"
import {Action} from "./reduxTypes"
import * as UndoRedoActions from "./undoRedoActions"

export const mapDispatchWithEspActions = function (dispatch: Dispatch<Action>) {
  return {
    actions: bindActionCreators(EspActions, dispatch),
    undoRedoActions: bindActionCreators(UndoRedoActions, dispatch),
    notificationActions: bindActionCreators(NotificationActions, dispatch),
  }
}

export default {mapDispatchWithEspActions}

export type EspActionsProps = ReturnType<typeof mapDispatchWithEspActions>
