import {bindActionCreators, Dispatch} from "redux"
import {$TodoType} from "./migrationTypes"
import * as EspActions from "./nk"
import * as NotificationActions from "./notificationActions"
import {Action} from "./reduxTypes"
import * as UndoRedoActions from "./undoRedoActions"
import assignUser from "./nk/assignUser"

export default {
  mapDispatchWithEspActions(dispatch: Dispatch<Action>): $TodoType {
    return {
      actions: bindActionCreators({...EspActions, assignUser}, dispatch),
      undoRedoActions: bindActionCreators(UndoRedoActions, dispatch),
      notificationActions: bindActionCreators(NotificationActions, dispatch),
    }
  },
}
