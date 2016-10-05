import React from 'react';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import { browserHistory, Router, Route, Link } from 'react-router'

import { App, Home } from './MainPage';
import { Processes } from './Processes';
import Visualization from './Visualization';
import { Metrics } from './Metrics';

import 'bootstrap/dist/css/bootstrap.css';
import '../assets/fonts/fonts.less'
import '../app.styl'

export default class EspAppRouter extends React.Component {

  render() {
    browserHistory.listen(location => {
      if (location.action == "PUSH") {
        this.props.store.dispatch({type: "URL_CHANGED"})
      }
    })
    return (
      <Router history={browserHistory} >
        <Route path={App.path} component={App}>
          <Route path={Home.path} component={Home} />
          <Route path={Processes.path} component={Processes} />
          <Route showHamburger={true} path={Visualization.path} component={Visualization} />
          <Route path={Metrics.path} component={Metrics} />
        </Route>
      </Router>
    );
  }
}
