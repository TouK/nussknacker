/* eslint-disable i18next/no-literal-string */
import React, {Suspense} from "react"
import ReactDOM from "react-dom"
import {Provider} from "react-redux"
import {AppContainer} from "react-hot-loader"
import {Router} from "react-router-dom"
//https://webpack.js.org/guides/public-path/#on-the-fly
import "./config"
import configureStore from "./store/configureStore"
import EspApp from "./containers/EspApp"
import Modal from "react-modal"
import history from "./history"

import "./stylesheets/notifications.styl"

import Notifications from "./containers/Notifications"
import NussknackerInitializer from "./containers/NussknackerInitializer"

import "./i18n";

const store = configureStore()
const rootContainer = document.getElementById("root");

Modal.setAppElement(rootContainer)
ReactDOM.render(
  <AppContainer>
    <Suspense fallback={<div>Loading...</div>}>
      <Provider store={store}>
        <Router history={history}>
          <NussknackerInitializer>
            <Notifications />
            <EspApp />
          </NussknackerInitializer>
        </Router>
      </Provider>
    </Suspense>
  </AppContainer>,
  rootContainer
)
