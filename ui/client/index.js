/* eslint-disable i18next/no-literal-string */
import React, {Suspense} from "react"
import ReactDOM from "react-dom"
import {AppContainer} from "react-hot-loader"
import Modal from "react-modal"
import {PersistGate} from "redux-persist/integration/react"
import {Provider} from "react-redux"
import {Router} from "react-router-dom"
//https://webpack.js.org/guides/public-path/#on-the-fly
import "./config"
import EspApp from "./containers/EspApp"

import Notifications from "./containers/Notifications"
import NussknackerInitializer from "./containers/NussknackerInitializer"
import history from "./history"

import "./i18n"
import configureStore from "./store/configureStore"

import "./stylesheets/notifications.styl"

const {store, persistor} = configureStore()
const rootContainer = document.getElementById("root")

Modal.setAppElement(rootContainer)
ReactDOM.render(
  <AppContainer>
    <Suspense fallback={<div>Loading...</div>}>
      <Provider store={store}>
        <PersistGate loading={null} persistor={persistor}>
          <Router history={history}>
            <NussknackerInitializer>
              <Notifications/>
              <EspApp/>
            </NussknackerInitializer>
          </Router>
        </PersistGate>
      </Provider>
    </Suspense>
  </AppContainer>,
  rootContainer,
)
