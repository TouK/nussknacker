import React from "react";
import {render} from "react-dom";
import {browserHistory, Router, Route, Link} from "react-router";
import _ from "lodash";
import Processes from "./Processes";
import "../stylesheets/mainMenu.styl";
import "../stylesheets/main.styl";

import Metrics from "./Metrics";
import Search from "./Search";
import Signals from "./Signals";
import DragArea from "../components/DragArea";
import {connect} from "react-redux";
import ActionsUtils from "../actions/ActionsUtils";
import Dialogs from "../components/modals/Dialogs";


const App_ = React.createClass({

  toggleUserPanel: function () {
    this.props.actions.toggleLeftPanel(!this.props.leftPanelIsOpened)
  },

  canGoToProcess: function () {
    return !_.isEmpty(this.props.params.processId)
  },

  goToProcess: function () {
    browserHistory.push('/visualization/' + this.props.params.processId)
  },

  renderTopLeftButton: function () {
    if (this.props.location.pathname.startsWith("/metrics") && this.canGoToProcess()) {
      return (
        <div className="top-left-button" onClick={this.goToProcess}>
          <span className="glyphicon glyphicon-menu-left"/>
        </div>
      )
    } else {
      return null
    }
  },

  render: function () {
    const AllDialogs = Dialogs.AllDialogs
    return (
      <div id="app-container">
        <nav id="main-menu" className="navbar navbar-default">
          <div id="git" className="hide">{JSON.stringify(GIT)}</div>
          <div className="container-fluid">
            <div className="navbar-header">
              {this.renderTopLeftButton()}
              <Link id="brand-name" className="navbar-brand" to={App.path}>
                <span id="app-logo" className="vert-middle">{App.header_a}</span>
              </Link>
            </div>

            <div className="collapse navbar-collapse">
              <ul id="menu-items" className="nav navbar-nav navbar-right nav-pills nav-stacked">
                <li><Link to={Processes.path}>{Processes.header}</Link></li>
                <li><Link to={Metrics.basePath}>{Metrics.header}</Link></li>
                <li><Link to={Search.path}>{Search.header}</Link></li>
                <li><Link to={Signals.path}>{Signals.header}</Link></li>
              </ul>
            </div>
          </div>
        </nav>
        <main>
          <DragArea>
            <AllDialogs/>
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

App.path = '/'
App.header_a = 'ESP'

