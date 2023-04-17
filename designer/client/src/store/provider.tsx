import React, {PropsWithChildren} from "react"
import {Provider} from "react-redux"
import {PersistGate} from "redux-persist/integration/react"
import configureStore from "./configureStore"

const {store, persistor} = configureStore()

export const StoreProvider = ({children}: PropsWithChildren<unknown>): JSX.Element => (
  <Provider store={store}>
    <PersistGate loading={null} persistor={persistor}>
      {children}
    </PersistGate>
  </Provider>
)

export {store}
