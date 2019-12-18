import React from 'react'
import ReactDOM from 'react-dom'
import {Provider} from 'react-redux'
import {AppContainer} from 'react-hot-loader'
import {Router} from 'react-router-dom'
//https://webpack.js.org/guides/public-path/#on-the-fly
import "./config"
import configureStore from './store/configureStore'
import EspApp from './containers/EspApp'
import Modal from "react-modal"
import history from "./history"

import "./stylesheets/notifications.styl"

import Notifications from "./containers/Notifications"
import NussknackerInitializer from "./containers/NussknackerInitializer"

const store = configureStore()

Modal.setAppElement("#root")
ReactDOM.render(
  <AppContainer>
    <Provider store={store}>
      <Router history={history}>
        <NussknackerInitializer>
          <Notifications />
          <EspApp />
        </NussknackerInitializer>
      </Router>
    </Provider>
  </AppContainer>,
  document.getElementById('root')
)