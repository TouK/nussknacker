import React, {PropsWithChildren} from "react"
import {Provider} from "react-redux"
import {PersistGate} from "redux-persist/integration/react"
import configureStore from "./configureStore"
import {getLoggedUserId} from "../reducers/selectors/settings"
import {waitForFirstValue} from "./waitForFirstValue"

const {store, persistor} = configureStore()

export const StoreProvider = ({children}: PropsWithChildren<unknown>): JSX.Element => (
  <Provider store={store}>
    <PersistGate loading={null} persistor={persistor}>
      {children}
    </PersistGate>
  </Provider>
)

export {store}

// expose userId getter for analytics
window["__userId"] = waitForFirstValue(store, getLoggedUserId)
