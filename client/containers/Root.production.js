import React from 'react';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import configureStore from '../store/configureStore.production';
import NotificationSystem from 'react-notification-system';
import HttpService from '../http/HttpService'
import Settings from '../http/Settings'

import EspAppRouter from './EspAppRouter';

const store = configureStore();

Settings.updateSettings(store)
//TODO: wywalic??
setInterval(() => Settings.updateSettings(store), 60000);

export default class Root extends React.Component {

  render() {
    return (
      <Provider store={store}>
        <div>
          <NotificationSystem ref={(c) => HttpService.setNotificationSystem(c)} />
          <EspAppRouter store={store}/>
        </div>
      </Provider>
    );
  }
}
