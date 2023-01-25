/* eslint-disable i18next/no-literal-string */
import {applyMiddleware, createStore} from "redux"
import {composeWithDevTools} from "redux-devtools-extension"
import thunk from "redux-thunk"
import {reducer} from "../reducers/settings"

export default function configureStore() {
  const store = createStore(
    reducer,
    composeWithDevTools(
      applyMiddleware(
        thunk,
      ),
    ),
  )

  if (module.hot) {
    module.hot.accept("../reducers/settings", () => {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const nextReducer = require("../reducers/settings").reducer
      store.replaceReducer(nextReducer)
    })
  }

  return store
}

