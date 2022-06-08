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
import {getFeatureSettings, getLoggedUser, getTabs} from "../reducers/selectors/settings"
import {UnknownRecord} from "../types/common"
import Services from "./Services";
import ErrorHandler from "./ErrorHandler"
import NotFound from "./errors/NotFound"
import Metrics from "./Metrics"
import Signals from "./Signals"
import {TransitionRouteSwitch} from "./TransitionRouteSwitch"
import loadable from "@loadable/component"
import LoaderSpinner from "../components/Spinner"
import * as Paths from "./paths"

type OwnProps = UnknownRecord
type State = UnknownRecord

type MetricParam = {
  params: {
    processId: string,
  },
}

const VisualizationWrapped = loadable(() => import("./VisualizationWrapped"), {fallback: <LoaderSpinner show={true}/>})
const ProcessTabs = loadable(() => import("./ProcessTabs"), {fallback: <LoaderSpinner show={true}/>})
const ScenariosTab = loadable(() => import("./ScenariosTab"), {fallback: <LoaderSpinner show={true}/>})
const CustomTab = loadable(() => import("./CustomTab"), {fallback: <LoaderSpinner show={true}/>})

export class NussknackerApp extends React.Component<Props, State> {
  private mountedHistory: UnregisterCallback

  getMetricsMatch = (): MetricParam => matchPath(this.props.location.pathname, {
    path: Paths.MetricsPath,
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
    const {resolved, tabs, featuresSettings} = this.props
    const rootTab = tabs.find(e => e.id === "scenarios")
    const fallbackPath = rootTab?.type === "Local" ? rootTab.url : Paths.ScenariosBasePath
    return resolved ?
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
            appPath={Paths.RootPath}
            leftElement={this.renderTopLeftButton()}
            rightElement={this.environmentAlert(featuresSettings.environmentAlert)}
          />
          <main>
            <VersionInfo/>
            <ErrorHandler>
              <TransitionRouteSwitch>
                <Route path={`${Paths.ScenariosBasePath}/:rest(.*)?`} component={ScenariosTab}/>
                <Route path={Paths.ProcessesLegacyPaths} component={ProcessTabs} exact/>
                <Route path={Paths.VisualizationPath} component={VisualizationWrapped} exact/>
                <Route path={Paths.MetricsPath} component={Metrics} exact/>
                <Route path={Paths.SignalsPath} component={Signals} exact/>
                <Route path={Paths.ServicesPath} component={Services} exact/>
                <Route path={`${Paths.CustomTabBasePath}/:id/:rest(.*)?`} component={CustomTab}/>
                <Route path={Paths.RootPath} render={() => <Redirect to={fallbackPath}/>} exact/>
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
    tabs: getTabs(state),
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
