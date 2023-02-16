import {css} from "@emotion/css"
import React from "react"
import {useSelector} from "react-redux"
import {Redirect} from "react-router"
import {matchPath, useLocation} from "react-router-dom"
import {MenuBar} from "../components/MenuBar"
import ProcessBackButton from "../components/Process/ProcessBackButton"
import {VersionInfo} from "../components/versionInfo"
import {getFeatureSettings, getLoggedUser, getTabs} from "../reducers/selectors/settings"
import {UnknownRecord} from "../types/common"
import {ErrorHandler} from "./ErrorHandler"
import Metrics from "./Metrics"
import {TransitionRouteSwitch} from "./TransitionRouteSwitch"
import loadable from "@loadable/component"
import LoaderSpinner from "../components/Spinner"
import * as Paths from "./paths"
import {NotFound} from "./errors/NotFound"
import {EnvironmentTag} from "./EnvironmentTag"
import {CompatRoute} from "react-router-dom-v5-compat"
import {isEmpty} from "lodash"

type MetricParam = {
  params: {
    processId: string,
  },
}

const VisualizationWrapped = loadable(() => import("./VisualizationWrapped"), {fallback: <LoaderSpinner show={true}/>})
const ProcessesTab = loadable(() => import("./ProcessesTab"), {fallback: <LoaderSpinner show={true}/>})
const ScenariosTab = loadable(() => import("./ScenariosTab"), {fallback: <LoaderSpinner show={true}/>})
const CustomTab = loadable(() => import("./CustomTab"), {fallback: <LoaderSpinner show={true}/>})

function getMetricsMatch(location): MetricParam {
  return matchPath(location.pathname, {
    path: Paths.MetricsPath,
    exact: true,
    strict: false,
  })
}

function TopLeftButton() {
  const location = useLocation()
  const match = getMetricsMatch(location)
  if (match?.params?.processId != null) {
    return (<ProcessBackButton processId={match.params.processId}/>)
  } else {
    return null
  }
}

export function NussknackerApp() {
  const tabs = useSelector(getTabs)
  const featuresSettings = useSelector(getFeatureSettings)
  const loggedUser = useSelector(getLoggedUser)

  const rootTab = tabs.find(e => e.id === "scenarios")
  const fallbackPath = rootTab?.type === "Local" ? rootTab.url : Paths.ScenariosBasePath

  if (isEmpty(loggedUser)) {
    return null
  }

  return (
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
        leftElement={<TopLeftButton/>}
        rightElement={<EnvironmentTag/>}
      />
      <main>
        <VersionInfo/>
        <ErrorHandler>
          <TransitionRouteSwitch>
            <CompatRoute path={`${Paths.ScenariosBasePath}`} component={ScenariosTab}/>
            <CompatRoute path={`${Paths.ProcessesTabDataPath}/:rest?`} component={ProcessesTab}/>
            <CompatRoute path={Paths.VisualizationPath} component={VisualizationWrapped} exact/>
            <CompatRoute path={Paths.MetricsPath} component={Metrics} exact/>
            <CompatRoute path={`${Paths.CustomTabBasePath}/:id/:rest?`} component={CustomTab}/>
            <CompatRoute path={Paths.RootPath} render={() => <Redirect to={fallbackPath}/>} exact/>
            <CompatRoute component={NotFound}/>
          </TransitionRouteSwitch>
        </ErrorHandler>
      </main>
      {featuresSettings.usageStatisticsReports.enabled && (
        <img
          src={featuresSettings.usageStatisticsReports.url}
          alt="anonymous usage reporting"
          referrerPolicy="origin"
          hidden
        />
      )}
    </div>
  )
}

