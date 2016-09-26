import React from 'react';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import { browserHistory, Router, Route, Link } from 'react-router'
import { App, Home } from './containers/MainPage';
import { Processes } from './containers/Processes';
import Visualization from './containers/Visualization';
import { Metrics } from './containers/Metrics';
import configureStore from './store/configureStore';
import DevTools from './containers/DevTools';

import 'bootstrap/dist/css/bootstrap.css';
import './assets/fonts/fonts.less'
import './app.styl'


const store = configureStore();
render((
  <Provider store={store}>
    <div>
      <Router history={browserHistory} >
        <Route path={App.path} component={App}>
            <Route path={Home.path} component={Home} />
            <Route path={Processes.path} component={Processes} />
            <Route showHamburger={true} path={Visualization.path} component={Visualization} />
            <Route path={Metrics.path} component={Metrics} />
        </Route>
      </Router>
      <DevTools/>
    </div>
  </Provider>
), document.getElementById('rootApp'));
