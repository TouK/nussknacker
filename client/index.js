import React from 'react';
import { render } from 'react-dom';
import { browserHistory, Router, Route, Link } from 'react-router'
import { AppContainer } from 'react-hot-loader';
import { App, Home, TodoApp } from './containers/MainPage';
import { Processes } from './containers/Processes';
import { Visualization } from './containers/Visualization';

import 'todomvc-app-css/index.css'; /*fixme wyrzucic przy usuwanio todoapp*/
import 'bootstrap/dist/css/bootstrap.css';
import './assets/fonts/fonts.less'
import './app.styl'

render((
    <Router history={browserHistory} >
        <Route path={App.path} component={App}>
            <Route path={Home.path} component={Home} />
            <Route path={Processes.path} component={Processes} />
            <Route path={Visualization.path} component={Visualization} />
            <Route path={TodoApp.path} component={TodoApp} />
        </Route>
    </Router>
), document.getElementById('rootApp'));


if (module.hot) {
  module.hot.accept('./containers/TodoAppRoot', () => {
    const RootContainer = require('./containers/TodoAppRoot').default;
    render(
      <AppContainer>
        <RootContainer
          store={ store }
        />
      </AppContainer>,
      document.getElementById('rootApp')
    );
  });
}
