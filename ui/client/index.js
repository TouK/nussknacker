import React from 'react';
import ReactDOM from 'react-dom';
import {Provider} from 'react-redux';
import {AppContainer} from 'react-hot-loader'
import NotificationSystem from 'react-notification-system';
import {Router} from 'react-router-dom'
import configureStore from './store/configureStore';
import HttpService from './http/HttpService'
import Settings from './http/Settings'
import EspApp from './containers/EspApp';
import Modal from "react-modal";
import history from "./history"

import "./stylesheets/notifications.styl";

import registerServiceWorker from './registerServiceWorker';

const store = configureStore()
Settings.updateSettings(store)

Modal.setAppElement("#root")
ReactDOM.render(
  <AppContainer>
    <Provider store={store}>
      <div>
        <NotificationSystem ref={(c) => HttpService.setNotificationSystem(c)} style={false} />
        <Router history={history}>
          <EspApp />
        </Router>
      </div>
    </Provider>
  </AppContainer>,
  document.getElementById('root')
)

registerServiceWorker()