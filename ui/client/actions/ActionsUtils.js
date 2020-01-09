import {bindActionCreators} from "redux"
import * as EspActions from "./esp"
import * as NotificationActions from "./notificationActions"
import * as UndoRedoActions from "./undoRedoActions"

export default {
  mapDispatchWithEspActions(dispatch) {
    return {
      actions: bindActionCreators(EspActions, dispatch),
      undoRedoActions: bindActionCreators(UndoRedoActions, dispatch),
      notificationActions: bindActionCreators(NotificationActions, dispatch)
    }
  }
}
