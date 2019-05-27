import React from 'react';
import {browserHistory, IndexRoute, Route, Router} from 'react-router'

import {App} from './MainPage';
import Processes from './Processes';
import Archive from './Archive';
import SubProcesses from './SubProcesses';
import Visualization from './Visualization';
import Metrics from './Metrics';
import Search from './Search';
import Signals from './Signals';
import AdminPage from './AdminPage';
import { hot } from 'react-hot-loader/root';

import 'bootstrap/dist/css/bootstrap.css';
import '../assets/fonts/fonts.less'
import '../app.styl'

class EspAppRouter extends React.Component {

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
          <Route path={Archive.path} component={Archive} />
          <Route path={Processes.path} component={Processes} />
          <Route path={Visualization.path} component={Visualization} />
          <Route path={Metrics.path} component={Metrics} />
          <Route path={Search.path} component={Search} />
          <Route path={Signals.path} component={Signals} />
          <Route path={AdminPage.path} component={AdminPage} />
          <IndexRoute component={Processes} />
        </Route>
      </Router>
    );
  }
}

export default hot(EspAppRouter)
