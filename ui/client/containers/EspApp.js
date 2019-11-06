import React from "react"
import {matchPath, NavLink, Route, Switch, withRouter} from 'react-router-dom'
import {hot} from 'react-hot-loader'
import _ from "lodash"
import Processes from "./Processes"
import SubProcesses from "./SubProcesses"
import NotFound from "./errors/NotFound"
import {nkPath} from "../config";
import {CSSTransition, TransitionGroup} from "react-transition-group"
import Metrics from "./Metrics"
import Search from "./Search"
import Signals from "./Signals"
import AdminPage from "./AdminPage"
import DragArea from "../components/DragArea"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import Dialogs from "../components/modals/Dialogs"
import * as VisualizationUrl from '../common/VisualizationUrl'
import Archive from "./Archive"
import Visualization from "./Visualization"

import 'bootstrap/dist/css/bootstrap.css'
import '../stylesheets/mainMenu.styl'
import '../assets/fonts/fonts.less'
import '../stylesheets/main.styl'
import '../app.styl'
import ErrorHandler from "./ErrorHandler"


class EspApp extends React.Component {

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
    return _.get(match, 'params.processId') != null
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
        <span className="navbar-brand vert-middle ">
          <span className={"indicator " + params.cssClass}>{params.content}</span>
        </span>
      )
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
              <NavLink id="brand-name" className="navbar-brand" to={EspApp.path}>
                <span id="app-logo" className="vert-middle">{EspApp.header}</span>
              </NavLink>
              {this.environmentAlert(this.props.featuresSettings.environmentAlert)}
            </div>

            <div className="collapse navbar-collapse">
              <ul id="menu-items" className="nav navbar-nav navbar-right nav-pills nav-stacked">
                <li><NavLink to={Processes.path}>{Processes.header}</NavLink></li>
                <li><NavLink to={SubProcesses.path}>{SubProcesses.header}</NavLink></li>
                {
                  !_.isEmpty(this.props.featuresSettings.metrics) ?
                    <li><NavLink to={Metrics.basePath}>{Metrics.header}</NavLink></li> : null
                }
                {
                  !_.isEmpty(this.props.featuresSettings.search) ?
                    <li><NavLink to={Search.path}>{Search.header}</NavLink></li> : null
                }
                {
                  this.props.featuresSettings.signals ?
                    <li><NavLink to={Signals.path}>{Signals.header}</NavLink></li> : null
                }
                <li><NavLink to={Archive.path}>{Archive.header}</NavLink></li>
                {
                  this.props.loggedUser.globalPermissions.adminTab ?
                    <li><NavLink to={AdminPage.path}>{AdminPage.header}</NavLink></li> : null
                }
              </ul>
            </div>
          </div>
        </nav>
        <main>
          <DragArea>
            <AllDialogs/>
            <div id="working-area" className={this.props.leftPanelIsOpened ? 'is-opened' : null}>
              <ErrorHandler>
                <Route path={EspApp.path} render={({location}) => (
                  <TransitionGroup>
                    <CSSTransition key={location.pathname} classNames="fade" timeout={{enter: 300, exit: 300}}>
                      <Switch location={location}>
                        <Route path={SubProcesses.path} component={SubProcesses} exact/>
                        <Route path={Archive.path} component={Archive} exact/>
                        <Route path={Processes.path} component={Processes} exact/>
                        <Route path={Visualization.path} component={Visualization} exact/>
                        <Route path={Metrics.path} component={Metrics} exact/>
                        <Route path={Search.path} component={Search} exact/>
                        <Route path={Signals.path} component={Signals} exact/>
                        <Route path={AdminPage.path} component={AdminPage} exact/>
                        <Route path={EspApp.path} component={Processes} exact/>
                        <Route component={NotFound}/>
                      </Switch>
                    </CSSTransition>
                  </TransitionGroup>
                )}/>
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
    resolved: !_.isEmpty(loggedUser)
  }
}

EspApp.path = `${nkPath}/`
EspApp.header = 'ESP'

export default hot(module)(withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(EspApp)))