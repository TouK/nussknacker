import React from 'react';
import { render } from 'react-dom';
import { Provider } from 'react-redux';
import { browserHistory, Router, Route, IndexRoute, Link } from 'react-router'

import { App } from './MainPage';
import Processes from './Processes';
import SubProcesses from './SubProcesses';
import Visualization from './Visualization';
import Metrics from './Metrics';
import Search from './Search';
import Signals from './Signals';
import ProcessSearch from './ProcessSearch';

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
          <Route path={SubProcesses.path} component={SubProcesses} />
          <Route path={Processes.path} component={Processes} />
          <Route path={Visualization.path} component={Visualization} />
          <Route path={Metrics.path} component={Metrics} />
          <Route path={Search.path} component={Search} />
          <Route path={Signals.path} component={Signals} />
          <Route path={ProcessSearch.path} component={ProcessSearch} />
          <IndexRoute component={Processes} />
        </Route>
      </Router>
    );
  }
}
