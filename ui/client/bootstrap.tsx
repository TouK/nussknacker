import React, {Suspense} from "react"
import ReactDOM from "react-dom"
import ErrorBoundary from "react-error-boundary"
import Modal from "react-modal"
import {Provider} from "react-redux"
import {Router} from "react-router-dom"
import {PersistGate} from "redux-persist/integration/react"
import LoaderSpinner from "./components/Spinner"

import Notifications from "./containers/Notifications"
import {NkApp} from "./containers/NussknackerApp"
import NussknackerInitializer from "./containers/NussknackerInitializer"
import {SettingsProvider} from "./containers/SettingsInitializer"
import {NkThemeProvider} from "./containers/theme"
import history from "./history"
import "./i18n"
import configureStore from "./store/configureStore"
import "./stylesheets/notifications.styl"

const {store, persistor} = configureStore()
const rootContainer = document.getElementById("root")

Modal.setAppElement(rootContainer)

const Root = () => (
  <Suspense fallback={<LoaderSpinner show/>}>
    <ErrorBoundary>
      <Provider store={store}>
        <PersistGate loading={null} persistor={persistor}>
          <Router history={history}>
            <NkThemeProvider>
              <SettingsProvider>
                <NussknackerInitializer>
                  <Notifications/>
                  <NkApp/>
                </NussknackerInitializer>
              </SettingsProvider>
            </NkThemeProvider>
          </Router>
        </PersistGate>
      </Provider>
    </ErrorBoundary>
  </Suspense>
)

ReactDOM.render(<Root/>, rootContainer)

