import {css} from "@emotion/css"
import {UnregisterCallback} from "history"
import _ from "lodash"
import React from "react"
import {WithTranslation, withTranslation} from "react-i18next"
import {connect} from "react-redux"
import {Redirect, Route, RouteComponentProps} from "react-router"
import {matchPath, withRouter} from "react-router-dom"
import {compose} from "redux"
import {urlChange} from "../actions/nk"
import {MenuBar} from "../components/MenuBar"
import ProcessBackButton from "../components/Process/ProcessBackButton"
import {VersionInfo} from "../components/versionInfo"
import {getFeatureSettings, getLoggedUser} from "../reducers/selectors/settings"
import {UnknownRecord} from "../types/common"
import {AdminPage, NkAdminPage} from "./AdminPage"
import {ArchiveTabData} from "./Archive"
import ErrorHandler from "./ErrorHandler"
import NotFound from "./errors/NotFound"
import Metrics from "./Metrics"
import {ProcessesTabData} from "./Processes"
import Signals from "./Signals"
import {SubProcessesTabData} from "./SubProcesses"
import {TransitionRouteSwitch} from "./TransitionRouteSwitch"
import loadable from "@loadable/component"
import LoaderSpinner from "../components/Spinner"
import * as VisualizationUrl from "../common/VisualizationUrl"

type OwnProps = UnknownRecord
type State = UnknownRecord

type MetricParam = {
  params: {
    processId: string,
  },
}

const VisualizationWrapped = loadable(() => import("./VisualizationWrapped"), {fallback: <LoaderSpinner show={true}/>});
const ProcessTabs = loadable(() => import("./ProcessTabs"), {fallback: <LoaderSpinner show={true}/>});
const ScenariosTab = loadable(() => import("./ScenariosTab"), {fallback: <LoaderSpinner show={true}/>});
const CustomTab = loadable(() => import("./CustomTab"), {fallback: <LoaderSpinner show={true}/>});

export class NussknackerApp extends React.Component<Props, State> {
  private readonly path: string = `/`
  private mountedHistory: UnregisterCallback

  getMetricsMatch = (): MetricParam => matchPath(this.props.location.pathname, {
    path: Metrics.path,
    exact: true,
    strict: false,
  })

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

  canGoToProcess() {
    const match = this.getMetricsMatch()
    return match?.params?.processId != null
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
    return this.props.resolved ?
      (
        <div
          id="app-container"
          className={css({
            width: "100%",
            height: "100%",
            display: "grid",
            alignItems: "stretch",
            gridTemplateRows: "auto 1fr",
            main: {
              overflow: "auto",
              display: "flex",
              flexDirection: "column-reverse",
            },
          })}
        >
          <MenuBar
            appPath={this.path}
            leftElement={this.renderTopLeftButton()}
            rightElement={this.environmentAlert(this.props.featuresSettings.environmentAlert)}
          />
          <main>
            <VersionInfo/>
            <ErrorHandler>
              <TransitionRouteSwitch>
                <Route
                  path={[ProcessesTabData.path, SubProcessesTabData.path, ArchiveTabData.path]}
                  component={ProcessTabs}
                  exact
                />
                <Route path={VisualizationUrl.visualizationPath} component={VisualizationWrapped} exact/>
                <Route path={Metrics.path} component={Metrics} exact/>
                <Route path={Signals.path} component={Signals} exact/>
                <Route path={AdminPage.path} component={NkAdminPage} exact/>
                <Route path={`/customtabs/scenarios_2/:rest(.*)?`} component={ScenariosTab}/>
                <Route path={`/customtabs/:id/:rest(.*)?`} component={CustomTab}/>
                <Redirect from={this.path} to={ProcessesTabData.path} exact/>
                <Route component={NotFound}/>
              </TransitionRouteSwitch>
            </ErrorHandler>
          </main>
        </div>
      ) :
      null
  }
}

function mapState(state) {
  const loggedUser = getLoggedUser(state)
  return {
    featuresSettings: getFeatureSettings(state),
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
