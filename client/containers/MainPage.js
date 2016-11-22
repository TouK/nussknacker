import React from "react";
import {render} from "react-dom";
import {browserHistory, Router, Route, Link} from "react-router";
import _ from "lodash";
import Processes from "./Processes";
import "../stylesheets/mainMenu.styl";
import hamburgerOpen from "../assets/img/menu-open.svg";
import hamburgerClosed from "../assets/img/menu-closed.svg";
import Metrics from "./Metrics";
import DragArea from "../components/DragArea";
import {connect} from "react-redux";
import ActionsUtils from "../actions/ActionsUtils";

const App_ = React.createClass({

    toggleUserPanel: function() {
      this.props.actions.toggleLeftPanel(!this.props.leftPanelIsOpened)
    },

    render: function() {
      return (
          <div id="app-container">
            <nav id="main-menu" className="navbar navbar-default">
              <div id="git" className="hide">{JSON.stringify(GIT)}</div>
              {(() => {
                if (_.get(this.props, 'routes[1].showHamburger', false)) {
                  return (
                    <div id="toggle-user-panel" onClick={this.toggleUserPanel}>
                      <img src={this.props.leftPanelIsOpened ? hamburgerOpen : hamburgerClosed} />
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
                    <li><Link to={Metrics.basePath}>{Metrics.header}</Link></li>
                  </ul>
                </div>
              </div>
            </nav>
            <main>
              <DragArea>
                <div id="working-area" className={this.props.leftPanelIsOpened ? 'is-opened' : null}>
                  {this.props.children}
                </div>
              </DragArea>
            </main>
          </div>
        )
    }
});

function mapState(state) {
  return {
    leftPanelIsOpened: state.ui.leftPanelIsOpened
  };
}

export const App = connect(mapState, ActionsUtils.mapDispatchWithEspActions)(App_);

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
