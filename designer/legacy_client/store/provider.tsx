import React, {PropsWithChildren} from "react"
import {Provider} from "react-redux"
import configureStore from "./configureStore"

const store = configureStore()

export const StoreProvider = ({children}: PropsWithChildren<unknown>): JSX.Element => {
  return (
    <Provider store={store}>
      {children}
    </Provider>
  )
}
