import React from 'react';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import configureStore from '../store/configureStore.production';
import NotificationSystem from 'react-notification-system';
import HttpService from '../http/HttpService'

import EspAppRouter from './EspAppRouter';

const store = configureStore();

setInterval(HttpService.fetchLoggedUser().then((user) => store.dispatch({type: "LOGGED_USER", user: user })), 10000);

export default class Root extends React.Component {

  render() {
    return (
      <Provider store={store}>
        <div>
          <NotificationSystem ref={(c) => HttpService.setNotificationSystem(c)} />
          <EspAppRouter/>
        </div>
      </Provider>
    );
  }
}
