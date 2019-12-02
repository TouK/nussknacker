import { createStore, applyMiddleware } from 'redux';
import { composeWithDevTools } from 'redux-devtools-extension';
import thunk from 'redux-thunk';

import { reducer } from '../reducers';
import {userTracker} from "../tracking/UserTracker"

export default function configureStore() {

  const store = createStore(
    reducer,
    composeWithDevTools(
      applyMiddleware(userTracker, thunk)
    ),
  );

  if (module.hot) {
    module.hot.accept('../reducers', () =>
      store.replaceReducer(reducer)
    );
  }

  return store;
}
