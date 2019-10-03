import React from 'react'
import ReactDOM from 'react-dom'
import {Provider} from 'react-redux'
import {AppContainer} from 'react-hot-loader'
import {Router} from 'react-router-dom'
//https://webpack.js.org/guides/public-path/#on-the-fly
import "./config"
import configureStore from './store/configureStore'
import Settings from './http/Settings'
import EspApp from './containers/EspApp'
import Modal from "react-modal"
import history from "./history"

import "./stylesheets/notifications.styl"

import registerServiceWorker from './registerServiceWorker'
import Notifications from "./containers/Notifications"

const store = configureStore()
Settings.updateSettings(store)

Modal.setAppElement("#root")
ReactDOM.render(
  <AppContainer>
    <Provider store={store}>
      <div>
        <Notifications/>
        <Router history={history}>
          <EspApp />
        </Router>
      </div>
    </Provider>
  </AppContainer>,
  document.getElementById('root')
)

registerServiceWorker()