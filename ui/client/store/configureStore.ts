/* eslint-disable i18next/no-literal-string */
import {applyMiddleware, createStore} from "redux"
import {composeWithDevTools} from "redux-devtools-extension"
import thunk from "redux-thunk"
import {analyticsMiddleware} from "../analytics/AnalyticsMiddleware"
import {persistReducer, persistStore} from "redux-persist"
import storage from "redux-persist/lib/storage" // defaults to localStorage for web
import {reducer} from "../reducers"

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
    // eslint-disable-next-line i18next/no-literal-string
    module.hot.accept("../reducers", () => {
      const nextReducer = require("../reducers").reducer
      store.replaceReducer(persistReducer(persistConfig, nextReducer))
    })
  }

  return {store, persistor}
}
