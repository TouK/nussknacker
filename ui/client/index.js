/* eslint-disable i18next/no-literal-string */
import React, {Suspense} from "react"
import ReactDOM from "react-dom"
import ErrorBoundary from "react-error-boundary"
import Modal from "react-modal"
import {Provider} from "react-redux"
import {Router} from "react-router-dom"
import {PersistGate} from "redux-persist/integration/react"
//https://webpack.js.org/guides/public-path/#on-the-fly
import "./config"
import {NkApp} from "./containers/NussknackerApp"

import Notifications from "./containers/Notifications"
import NussknackerInitializer from "./containers/NussknackerInitializer"
import {NkThemeProvider} from "./containers/theme"
import history from "./history"

import "./i18n"
import configureStore from "./store/configureStore"

import "./stylesheets/notifications.styl"

const {store, persistor} = configureStore()
const rootContainer = document.getElementById("root")

Modal.setAppElement(rootContainer)
ReactDOM.render(
  (
    <div className="test">
      <Suspense fallback={<div>Loading...</div>}>
        <ErrorBoundary>
          <Provider store={store}>
            <PersistGate loading={null} persistor={persistor}>
              <Router history={history}>
                <NkThemeProvider>
                  <NussknackerInitializer>
                    <Notifications/>
                    <NkApp/>
                  </NussknackerInitializer>
                </NkThemeProvider>
              </Router>
            </PersistGate>
          </Provider>
        </ErrorBoundary>
      </Suspense>
    </div>
  ),
  rootContainer,
)

