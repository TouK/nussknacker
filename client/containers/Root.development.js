import React from 'react';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import DevTools from './DevTools';
import configureStore from '../store/configureStore.develpoment';
import NotificationSystem from 'react-notification-system';
import HttpService from '../http/HttpService'
import Settings from '../http/Settings'
import EspAppRouter from './EspAppRouter';
import $ from 'jquery';


const store = configureStore();

//TODO: jakos inaczej to ograc??
// var user = "reader";
var user = "admin";
$.ajaxSetup({
  headers: {
    'Authorization': "Basic " + btoa(`${user}:${user}`)
  }
});

Settings.updateSettings(store)

export default class Root extends React.Component {

  render() {
    return (
      <Provider store={store}>
        <div>
          <NotificationSystem ref={(c) => HttpService.setNotificationSystem(c)} />
          <EspAppRouter store={store}/>
          <DevTools/>
        </div>
      </Provider>
    );
  }
}
