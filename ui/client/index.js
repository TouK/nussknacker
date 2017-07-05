import React from 'react';
import ReactDOM from 'react-dom';
import { Provider } from 'react-redux';
import { AppContainer } from 'react-hot-loader'
import NotificationSystem from 'react-notification-system';
import $ from 'jquery';

import configureStore from './store/configureStore';
import HttpService from './http/HttpService'
import Settings from './http/Settings'
import EspAppRouter from './containers/EspAppRouter';

import "./stylesheets/notifications.styl";

const store = configureStore();
Settings.updateSettings(store);

const render = Component => {
  ReactDOM.render(
    <AppContainer>
      <Provider store={store}>
        <div>
          <NotificationSystem ref={(c) => HttpService.setNotificationSystem(c)} style={false} />
          <Component store={store}/>
        </div>
      </Provider>
    </AppContainer>
    ,
    document.getElementById('rootApp')
  );
};

render(EspAppRouter);

if (module.hot) {
  module.hot.accept('./containers/EspAppRouter', () => { render(EspAppRouter) });
}

