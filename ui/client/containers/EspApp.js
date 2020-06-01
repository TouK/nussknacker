import React from "react"
import {Redirect} from "react-router"
import {matchPath, Route, Switch, withRouter} from "react-router-dom"
import _ from "lodash"
import {MenuBar} from "../components/MenuBar"
import * as Processes from "./Processes"
import * as SubProcesses from "./SubProcesses"
import * as Archive from "./Archive"
import NotFound from "./errors/NotFound"
import {nkPath} from "../config"
import Metrics from "./Metrics"
import Search from "./Search"
import Signals from "./Signals"
import AdminPage from "./AdminPage"
import DragArea from "../components/DragArea"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import Dialogs from "../components/modals/Dialogs"
import * as VisualizationUrl from "../common/VisualizationUrl"
import Visualization from "./Visualization"

import "../stylesheets/mainMenu.styl"
import "../stylesheets/main.styl"
import "../app.styl"
import ErrorHandler from "./ErrorHandler"
import {ProcessTabs} from "./ProcessTabs"
import {FadeRoute} from "./FadeRoute"

export class EspApp extends React.Component {

  componentDidMount() {
    this.mountedHistory = this.props.history.listen((location, action) => {
      if (action === "PUSH") {
        this.props.actions.urlChange(location)
      }
    })
  }

  componentWillUnmount() {
    if (this.mountedHistory) {
      this.mountedHistory()
    }
  }

  getMetricsMatch() {
    return matchPath(this.props.location.pathname, {path: Metrics.path, exact: true, strict: false})
  }

  canGoToProcess() {
    const match = this.getMetricsMatch()
    return _.get(match, "params.processId") != null
  }

  goToProcess = () => {
    const match = this.getMetricsMatch()
    this.props.history.push(VisualizationUrl.visualizationUrl(match.params.processId))
  }

  renderTopLeftButton() {
    if (this.canGoToProcess()) {
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
      return (
        <span className={`indicator ${params.cssClass}`} title={params.content}>{params.content}</span>
      )
  }

  render() {
    const AllDialogs = Dialogs.AllDialogs
    return this.props.resolved ? (
      <div id="app-container">
        <div className="hide">{JSON.stringify(__GIT__)}</div>
        <MenuBar
          {...this.props}
          app={EspApp}
          leftElement={this.renderTopLeftButton()}
          rightElement={this.environmentAlert(this.props.featuresSettings.environmentAlert)}
        />
        <main>
          <DragArea>
            <AllDialogs/>
            <div id="working-area" className={this.props.leftPanelIsOpened ? "is-opened" : null}>
              <ErrorHandler>
                <Route
                  path={EspApp.path}
                  render={({location}) => (
                    <Switch location={location}>
                      <FadeRoute path={[Processes.path, SubProcesses.path, Archive.path]} component={ProcessTabs} exact/>
                      <FadeRoute path={Visualization.path} component={Visualization} exact/>
                      <FadeRoute path={Metrics.path} component={Metrics} exact/>
                      <FadeRoute path={Search.path} component={Search} exact/>
                      <FadeRoute path={Signals.path} component={Signals} exact/>
                      <FadeRoute path={AdminPage.path} component={AdminPage} exact/>
                      <Redirect from={EspApp.path} to={Processes.path} exact/>
                      <FadeRoute component={NotFound}/>
                    </Switch>
                  )}
                />
              </ErrorHandler>
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
    resolved: !_.isEmpty(loggedUser),
  }
}

EspApp.path = `${nkPath}/`
EspApp.header = "ESP"

export default withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(EspApp))

