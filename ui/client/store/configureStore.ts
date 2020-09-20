/* eslint-disable i18next/no-literal-string */
import {applyMiddleware, createStore} from "redux"
import {composeWithDevTools} from "redux-devtools-extension"
import thunk from "redux-thunk"
import {analyticsMiddleware} from "../analytics/AnalyticsMiddleware"
import {persistReducer, persistStore} from "redux-persist"
import storage from "redux-persist/lib/storage" // defaults to localStorage for web
import {reducer} from "../reducers"
import {ThunkDispatch} from "../actions/reduxTypes"
import {useDispatch} from "react-redux"

const persistConfig = {
  key: "ROOT",
  whitelist: ["toolbars"],
  storage,
}

export default function configureStore() {

  const store = createStore(
    persistReducer(persistConfig, reducer),
    composeWithDevTools(
      applyMiddleware(analyticsMiddleware, thunk),
    ),
  )
  const persistor = persistStore(store)

  if (module.hot) {
    module.hot.accept("../reducers", () => {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const nextReducer = require("../reducers").reducer
      store.replaceReducer(persistReducer(persistConfig, nextReducer))
    })
  }

  return {store, persistor}
}

export function useThunkDispatch() {
  return useDispatch<ThunkDispatch>()
}
