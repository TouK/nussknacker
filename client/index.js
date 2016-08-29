import React from 'react';
import { render } from 'react-dom';
import { browserHistory, Router, Route, Link } from 'react-router'
import { AppContainer } from 'react-hot-loader';
import { App, Home } from './containers/MainPage';
import { Processes } from './containers/Processes';
import { Visualization } from './containers/Visualization';

import 'bootstrap/dist/css/bootstrap.css';
import './assets/fonts/fonts.less'
import './app.styl'

render((
    <Router history={browserHistory} >
        <Route path={App.path} component={App}>
            <Route path={Home.path} component={Home} />
            <Route path={Processes.path} component={Processes} />
            <Route showHamburger={true} path={Visualization.path} component={Visualization} />
        </Route>
    </Router>
), document.getElementById('rootApp'));
