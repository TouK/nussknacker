import React from 'react'
import { render } from 'react-dom'
import ReactDOM from 'react-dom'
import { browserHistory, Router, Route } from 'react-router'
import { Link } from 'react-router'
import joint from 'jointjs'
import 'jointjs/dist/joint.css'
import _ from 'lodash'
import classNames from 'classnames'
import { Processes } from './Processes'
import { Visualization } from './Visualization'
import UserPanel from '../components/UserPanel'

import '../stylesheets/mainMenu.styl'
import hamburgerOpen from '../assets/img/menu-open.svg'
import hamburgerClosed from '../assets/img/menu-closed.svg'


export const App = React.createClass({
    getInitialState: function() {
      return { userPanelOpened: false };
    },

    toggleUserPanel: function() {
      this.setState({ userPanelOpened: !this.state.userPanelOpened });
    },

    render: function() {
        var userPanelOpenedClass = classNames({
          'is-opened': this.state.userPanelOpened && _.get(this.props, 'routes[1].showHamburger', false)
        })
        return (
          <div id="app-container">
            <nav id="main-menu" className="navbar navbar-default">
              <div id="git" className="hide">{JSON.stringify(GIT)}</div>
              {(() => {
                if (_.get(this.props, 'routes[1].showHamburger', false)) {
                  return (
                    <div id="toggle-user-panel" onClick={this.toggleUserPanel}>
                      <img src={this.state.userPanelOpened ? hamburgerOpen : hamburgerClosed} />
                    </div>
                  )
                }
              })()}
              <div className="container-fluid">
                <div className="navbar-header">
                  <Link id="brand-name" className="navbar-brand" to={App.path}>
                    <span id="app-logo" className="vert-middle">{App.header_a}</span>
                    <span id="app-name" className="vert-middle">{App.header_b}</span>
                  </Link>
                </div>

                <div className="collapse navbar-collapse">
                  <ul id="menu-items" className="nav navbar-nav navbar-right nav-pills nav-stacked">
                    <li><Link to={Home.path}>{Home.header}</Link></li>
                    <li><Link to={Processes.path}>{Processes.header}</Link></li>
                  </ul>
                </div>
              </div>
            </nav>
            <main>
              <UserPanel className={userPanelOpenedClass}/>
              <div id="working-area" className={userPanelOpenedClass}>
                {this.props.children}
              </div>
            </main>
          </div>
        )
    }
});

App.title = 'Home'
App.path = '/'
App.header_a = 'ESP'
App.header_b = 'Event Stream Processing'

export const Home = () => (
  <div className="Page">
      <h1>Welcome to {App.header_b}</h1>
  </div>
);

Home.title = 'Home'
Home.path = '/home'
Home.header = 'Home'
