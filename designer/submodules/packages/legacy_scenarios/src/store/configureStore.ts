/* eslint-disable i18next/no-literal-string */
import {createStore} from "redux"
import {reducer} from "../reducers/settings"

export default () => {
  const store = createStore(reducer)

  if (module.hot) {
    module.hot.accept("../reducers/settings", () => {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const nextReducer = require("../reducers/settings").reducer
      store.replaceReducer(nextReducer)
    })
  }

  return store
}

