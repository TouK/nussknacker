/* eslint-disable i18next/no-literal-string */
import {applyMiddleware, createStore} from "redux"
import {composeWithDevTools} from "redux-devtools-extension"
import thunk from "redux-thunk"
import {reducer} from "../reducers"
import {createStateSyncMiddleware, initMessageListener} from "redux-state-sync"

export default function configureStore() {

  const store = createStore(
    reducer,
    composeWithDevTools(
      applyMiddleware(
        thunk,
        createStateSyncMiddleware({
          whitelist: [],
        }),
      ),
    ),
  )
  initMessageListener(store)

  if (module.hot) {
    module.hot.accept("../reducers", () => {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const nextReducer = require("../reducers").reducer
      store.replaceReducer(nextReducer)
    })
  }

  return {store}
}

