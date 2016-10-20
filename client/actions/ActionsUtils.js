import { bindActionCreators } from 'redux';
import * as EspActions from './actions';

export default {

  mapDispatchWithEspActions(dispatch) {
    return {
      actions: bindActionCreators(EspActions, dispatch)
    };
  }

}