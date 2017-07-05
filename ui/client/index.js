import React from 'react';
import ReactDOM from 'react-dom';
import { Provider } from 'react-redux';
import NotificationSystem from 'react-notification-system';
import $ from 'jquery';

import configureStore from './store/configureStore';
import HttpService from './http/HttpService'
import Settings from './http/Settings'
import EspAppRouter from './containers/EspAppRouter';

import "./stylesheets/notifications.styl";

const store = configureStore();
Settings.updateSettings(store);

ReactDOM.render(
    <Provider store={store}>
      <div>
        <NotificationSystem ref={(c) => HttpService.setNotificationSystem(c)} style={false} />
        <EspAppRouter store={store}/>
      </div>
    </Provider>
  ,
  document.getElementById('rootApp')
);
