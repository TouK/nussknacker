import React from 'react';
import ReactDOM from 'react-dom';
import {Provider} from 'react-redux';
import {AppContainer} from 'react-hot-loader'
import NotificationSystem from 'react-notification-system';

import configureStore from './store/configureStore';
import HttpService from './http/HttpService'
import Settings from './http/Settings'
import EspAppRouter from './containers/EspAppRouter';
import Modal from "react-modal";

import "./stylesheets/notifications.styl";

const createRootApp = () =>{
  const rootApp = document.createElement('div');
  rootApp.setAttribute("id", "rootApp")
  document.body.append(rootApp)
  return rootApp
}

const store = configureStore();
Settings.updateSettings(store);

const render = (Component, root) => {
  Modal.setAppElement("#rootApp");
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
    root
  );
};

const root = createRootApp();
render(EspAppRouter, root);
