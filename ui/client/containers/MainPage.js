import React from "react";
import {render} from "react-dom";
import {browserHistory, Link} from "react-router";
import _ from "lodash";
import Processes from "./Processes";
import SubProcesses from "./SubProcesses";
import "../stylesheets/mainMenu.styl";
import "../stylesheets/main.styl";

import Metrics from "./Metrics";
import Search from "./Search";
import Signals from "./Signals";
import AdminPage from "./AdminPage";
import DragArea from "../components/DragArea";
import {connect} from "react-redux";
import ActionsUtils from "../actions/ActionsUtils";
import Dialogs from "../components/modals/Dialogs";
import * as VisualizationUrl from '../common/VisualizationUrl'
import Archive from "./Archive";

class App_ extends React.Component {

  toggleUserPanel() {
    this.props.actions.toggleLeftPanel(!this.props.leftPanelIsOpened)
  }

  canGoToProcess() {
    return !_.isEmpty(this.props.params.processId)
  }

  goToProcess() {
    browserHistory.push(VisualizationUrl.visualizationUrl(this.props.params.processId))
  }

  renderTopLeftButton() {
    if (this.props.location.pathname.startsWith("/metrics") && this.canGoToProcess()) {
      return (
        <div className="top-left-button" onClick={this.goToProcess}>
          <span className="glyphicon glyphicon-menu-left"/>
        </div>
      )
    } else {
      return null
    }
  }

  environmentAlert(params) {
    if (params && params.content)
      return (<span className="navbar-brand vert-middle ">
        <span className={"indicator "+ params.cssClass}>{params.content}</span>
      </span>
    );
  }

  render() {
    const AllDialogs = Dialogs.AllDialogs
    return this.props.resolved ? (
      <div id="app-container">
        <nav id="main-menu" className="navbar navbar-default">
          <div id="git" className="hide">{JSON.stringify(GIT)}</div>
          <div className="container-fluid">
            <div className="navbar-header">
              {this.renderTopLeftButton()}
              <Link id="brand-name" className="navbar-brand" to={App.path}>
                <span id="app-logo" className="vert-middle">{App.header_a}</span>
              </Link>
              {this.environmentAlert(this.props.featuresSettings.environmentAlert)}
            </div>

            <div className="collapse navbar-collapse">
              <ul id="menu-items" className="nav navbar-nav navbar-right nav-pills nav-stacked">
                <li><Link to={Processes.path}>{Processes.header}</Link></li>
                <li><Link to={SubProcesses.path}>{SubProcesses.header}</Link></li>

                {!_.isEmpty(this.props.featuresSettings.metrics) ?
                  <li><Link to={Metrics.basePath}>{Metrics.header}</Link></li> : null}
                {!_.isEmpty(this.props.featuresSettings.search) ?
                  <li><Link to={Search.path}>{Search.header}</Link></li> : null }
                {this.props.featuresSettings.signals ?
                  <li><Link to={Signals.path}>{Signals.header}</Link></li> : null }
                <li><Link to={Archive.path}>{Archive.header}</Link></li>
                {this.props.loggedUser.isAdmin ?
                  <li><Link to={AdminPage.path}>{AdminPage.header}</Link></li> : null}
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
    ) : null
  }
}

function mapState(state) {
  const loggedUser = state.settings.loggedUser
  return {
    leftPanelIsOpened: state.ui.leftPanelIsOpened,
    featuresSettings: state.settings.featuresSettings,
    loggedUser: loggedUser,
    resolved: !_.isEmpty(loggedUser)
  };
}

export const App = connect(mapState, ActionsUtils.mapDispatchWithEspActions)(App_);

App.path = '/'
App.header_a = 'ESP'

