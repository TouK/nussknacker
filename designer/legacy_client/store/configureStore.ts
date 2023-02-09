/* eslint-disable i18next/no-literal-string */
import {applyMiddleware, createStore} from "redux"
import {composeWithDevTools} from "redux-devtools-extension"
import thunk from "redux-thunk"
import {persistStore} from "redux-persist"
import {reducer} from "../reducers"
import {ThunkDispatch} from "../actions/reduxTypes"
import {useDispatch} from "react-redux"
import {createStateSyncMiddleware, initMessageListener} from "redux-state-sync"

export default function configureStore() {

  const store = createStore(
    reducer,
    composeWithDevTools(
      applyMiddleware(
        thunk,
        createStateSyncMiddleware({
          whitelist: [
            "TOGGLE_SETTINGS",
            "SET_SETTINGS",
          ],
        }),
      ),
    ),
  )
  const persistor = persistStore(store)
  initMessageListener(store)

  if (module.hot) {
    module.hot.accept("../reducers", () => {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const nextReducer = require("../reducers").reducer
      store.replaceReducer(nextReducer)
    })
  }

  return {store, persistor}
}

export function useThunkDispatch() {
  return useDispatch<ThunkDispatch>()
}
