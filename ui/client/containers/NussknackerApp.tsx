import React from "react"
import {Redirect, Route, RouteComponentProps} from "react-router"
import {matchPath, withRouter} from "react-router-dom"
import _ from "lodash"
import {urlChange} from "../actions/nk"
import {MenuBar} from "../components/MenuBar"
import {UnknownRecord} from "../types/common"
import {ProcessesTabData} from "./Processes"
import {SubProcessesTabData} from "./SubProcesses"
import {ArchiveTabData} from "./Archive"
import NotFound from "./errors/NotFound"
import {TransitionRouteSwitch} from "./TransitionRouteSwitch"
import Metrics from "./Metrics"
import Signals from "./Signals"
import {NkAdminPage, AdminPage} from "./AdminPage"
import DragArea from "../components/DragArea"
import {connect} from "react-redux"
import Dialogs from "../components/modals/Dialogs"
import Visualization from "./Visualization"

import "../stylesheets/mainMenu.styl"
import "../app.styl"
import ErrorHandler from "./ErrorHandler"
import {ProcessTabs} from "./ProcessTabs"
import {getFeatureSettings} from "../reducers/selectors/settings"
import {CustomTab, CustomTabPath} from "./CustomTab"
import {WithTranslation, withTranslation} from "react-i18next"
import {compose} from "redux"
import {UnregisterCallback} from "history"
import ProcessBackButton from "../components/Process/ProcessBackButton"
import * as queryString from "query-string"

type OwnProps = UnknownRecord
type State = UnknownRecord

type MetricParam = {
  params: {
    processId: string,
  },
}

export class NussknackerApp extends React.Component<Props, State> {
  private readonly path: string = `/`
  private mountedHistory: UnregisterCallback

  componentDidMount() {
    this.mountedHistory = this.props.history.listen((location, action) => {
      if (action === "PUSH") {
        this.props.urlChange(location)
      }
    })
  }

  componentWillUnmount() {
    if (this.mountedHistory) {
      this.mountedHistory()
    }
  }

  getMetricsMatch = (): MetricParam => matchPath(this.props.location.pathname, {path: Metrics.path, exact: true, strict: false})

  canGoToProcess() {
    const match = this.getMetricsMatch()
    return match?.params?.processId != null
  }

  /**
   * In some cases (eg. docker demo) we serve Grafana and Kibana from nginx proxy, from root app url, and when service responds with error
   * then React app catches this and shows error page. To make it render only error, without app menu, we have mark iframe
   * requests with special query parameter so that we can recognize them and skip menu rendering.
   */
  renderMenu = () => {
    const isLoadAsIframe = queryString.parse(this.props.history.location.search, {parseBooleans: true})?.iframe

    if (!isLoadAsIframe) {
      return (
        <MenuBar
          {...this.props}
          appPath={this.path}
          leftElement={this.renderTopLeftButton()}
          rightElement={this.environmentAlert(this.props.featuresSettings.environmentAlert)}
        />
      )
    }

    return null
  }

  renderTopLeftButton() {
    const match = this.getMetricsMatch()
    if (this.canGoToProcess()) {
      return (<ProcessBackButton processId={match.params.processId}/>)
    } else {
      return null
    }
  }

  environmentAlert(params) {
    if (params && params.content) {
      return (
        <span className={`indicator ${params.cssClass}`} title={params.content}>{params.content}</span>
      )
    }
  }

  render() {
    const AllDialogs = Dialogs.AllDialogs
    return this.props.resolved ?
      (
        <div id="app-container">
          <div className="hide">{JSON.stringify(__GIT__)}</div>
          {this.renderMenu()}
          <main>
            <DragArea>
              <AllDialogs/>
              <ErrorHandler>
                <TransitionRouteSwitch>
                  <Route
                    path={[ProcessesTabData.path, SubProcessesTabData.path, ArchiveTabData.path]}
                    component={ProcessTabs}
                    exact
                  />
                  <Route path={Visualization.path} component={Visualization} exact/>
                  <Route path={Metrics.path} component={Metrics} exact/>
                  <Route path={Signals.path} component={Signals} exact/>
                  <Route path={AdminPage.path} component={NkAdminPage} exact/>
                  <Route path={`${CustomTabPath}/:id`} component={CustomTab} exact/>
                  <Redirect from={this.path} to={ProcessesTabData.path} exact/>
                  <Route component={NotFound}/>
                </TransitionRouteSwitch>
              </ErrorHandler>
            </DragArea>
          </main>
        </div>
      ) :
      null
  }
}

function mapState(state) {
  const loggedUser = state.settings.loggedUser
  return {
    featuresSettings: getFeatureSettings(state),
    loggedUser: loggedUser,
    resolved: !_.isEmpty(loggedUser),
  }
}

const mapDispatch = {urlChange}

type StateProps = ReturnType<typeof mapState> & typeof mapDispatch
type Props = OwnProps & StateProps & WithTranslation & RouteComponentProps

const enhance = compose(
  withRouter,
  connect(mapState, mapDispatch),
  withTranslation(),
)

export const NkApp: React.ComponentClass<OwnProps> = enhance(NussknackerApp)
