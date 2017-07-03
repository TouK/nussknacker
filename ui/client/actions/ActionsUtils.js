import { bindActionCreators } from 'redux';
import * as EspActions from './actions';
import * as UndoRedoActions from '../undoredo/UndoRedoActions';

export default {

  mapDispatchWithEspActions(dispatch) {
    return {
      actions: bindActionCreators(EspActions, dispatch),
      undoRedoActions: bindActionCreators(UndoRedoActions, dispatch)
    };
  }

}