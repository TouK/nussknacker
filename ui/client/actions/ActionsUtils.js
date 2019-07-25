import { bindActionCreators } from 'redux'
import * as EspActions from './actions'
import * as UndoRedoActions from './undoRedoActions'

export default {
  mapDispatchWithEspActions(dispatch) {
    return {
      actions: bindActionCreators(EspActions, dispatch),
      undoRedoActions: bindActionCreators(UndoRedoActions, dispatch)
    }
  }
}