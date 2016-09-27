import React from 'react';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import DevTools from './DevTools';
import configureStore from '../store/configureStore.develpoment';

import EspAppRouter from './EspAppRouter';

const store = configureStore();

export default class Root extends React.Component {

  render() {
    return (
      <Provider store={store}>
        <div>
          <EspAppRouter/>
          <DevTools/>
        </div>
      </Provider>
    );
  }
}
