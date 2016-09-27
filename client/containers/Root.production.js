import React from 'react';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import configureStore from '../store/configureStore.production';

import EspAppRouter from './EspAppRouter';

const store = configureStore();

export default class Root extends React.Component {

  render() {
    return (
      <Provider store={store}>
        <div>
          <EspAppRouter/>
        </div>
      </Provider>
    );
  }
}
