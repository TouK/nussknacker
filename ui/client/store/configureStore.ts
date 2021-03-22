/* eslint-disable i18next/no-literal-string */
import {applyMiddleware, createStore} from "redux"
import {composeWithDevTools} from "redux-devtools-extension"
import thunk from "redux-thunk"
import {analyticsMiddleware} from "../analytics/AnalyticsMiddleware"
import {persistStore} from "redux-persist"
import {reducer} from "../reducers"
import {ThunkDispatch} from "../actions/reduxTypes"
import {useDispatch} from "react-redux"

export default function configureStore() {

  const store = createStore(
    reducer,
    composeWithDevTools(
      applyMiddleware(analyticsMiddleware, thunk),
    ),
  )
  const persistor = persistStore(store)

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
