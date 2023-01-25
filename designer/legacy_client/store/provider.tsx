import React, {PropsWithChildren} from "react"
import {Provider} from "react-redux"
import configureStore from "./configureStore"

const store = configureStore()

export const StoreProvider = ({children}: PropsWithChildren<unknown>): JSX.Element => (
  <Provider store={store}>
    {children}
  </Provider>
)
