import React from "react"
import {Route, Redirect} from "react-router"
import {matchPath, withRouter} from "react-router-dom"
import _ from "lodash"
import {MenuBar} from "../components/MenuBar"
import {ProcessesTabData} from "./Processes"
import {SubProcessesTabData} from "./SubProcesses"
import {ArchiveTabData} from "./Archive"
import NotFound from "./errors/NotFound"
import {nkPath} from "../config"
import {TransitionRouteSwitch} from "./TransitionRouteSwitch"
import Metrics from "./Metrics"
import Search from "./Search"
import Signals from "./Signals"
import AdminPage from "./AdminPage"
import DragArea from "../components/DragArea"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import Dialogs from "../components/modals/Dialogs"
import Visualization from "./Visualization"

import "../stylesheets/mainMenu.styl"
import "../app.styl"
import ErrorHandler from "./ErrorHandler"
import {ProcessTabs} from "./ProcessTabs"
import {goToProcess} from "../actions/nk/showProcess"
import {getFeatureSettings} from "../reducers/selectors/settings"

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
    goToProcess(match.params.processId)
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
                <TransitionRouteSwitch>
                  <Route
                    path={[ProcessesTabData.path, SubProcessesTabData.path, ArchiveTabData.path]}
                    component={ProcessTabs}
                    exact
                  />
                  <Route path={Visualization.path} component={Visualization} exact/>
                  <Route path={Metrics.path} component={Metrics} exact/>
                  <Route path={Search.path} component={Search} exact/>
                  <Route path={Signals.path} component={Signals} exact/>
                  <Route path={AdminPage.path} component={AdminPage} exact/>
                  <Redirect from={EspApp.path} to={ProcessesTabData.path} exact/>
                  <Route component={NotFound}/>
                </TransitionRouteSwitch>
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
    featuresSettings: getFeatureSettings(state),
    loggedUser: loggedUser,
    resolved: !_.isEmpty(loggedUser),
  }
}

EspApp.path = `${nkPath}/`
EspApp.header = "ESP"

export default withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(EspApp))

