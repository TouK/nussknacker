import type {Dispatch} from "redux"
import {bindActionCreators} from "redux"
import * as EspActions from "./esp"
import * as NotificationActions from "./notificationActions"
import type {Action} from "./types"
import * as UndoRedoActions from "./undoRedoActions"

export default {
  mapDispatchWithEspActions(dispatch: Dispatch<Action>) {
    return {
      actions: bindActionCreators(EspActions, dispatch),
      undoRedoActions: bindActionCreators(UndoRedoActions, dispatch),
      notificationActions: bindActionCreators(NotificationActions, dispatch),
    }
  },
}
