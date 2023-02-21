import {css} from "@emotion/css"
import React from "react"
import {useSelector} from "react-redux"
import {MenuBar} from "../components/MenuBar"
import ProcessBackButton from "../components/Process/ProcessBackButton"
import {VersionInfo} from "../components/versionInfo"
import {getFeatureSettings, getLoggedUser, getTabs} from "../reducers/selectors/settings"
import {ErrorHandler} from "./ErrorHandler"
import Metrics from "./Metrics"
import {TransitionRouteSwitch} from "./TransitionRouteSwitch"
import loadable from "@loadable/component"
import LoaderSpinner from "../components/Spinner"
import * as Paths from "./paths"
import {MetricsBasePath, VisualizationBasePath} from "./paths"
import {NotFound} from "./errors/NotFound"
import {EnvironmentTag} from "./EnvironmentTag"
import {Navigate, Route} from "react-router-dom"
import {isEmpty} from "lodash"

const VisualizationWrapped = loadable(() => import("./VisualizationWrapped"), {fallback: <LoaderSpinner show={true}/>})
const ProcessesTab = loadable(() => import("./ProcessesTab"), {fallback: <LoaderSpinner show={true}/>})
const ScenariosTab = loadable(() => import("./ScenariosTab"), {fallback: <LoaderSpinner show={true}/>})
const CustomTab = loadable(() => import("./CustomTab"), {fallback: <LoaderSpinner show={true}/>})

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
        leftElement={<ProcessBackButton/>}
        rightElement={<EnvironmentTag/>}
      />
      <main>
        <VersionInfo/>
        <ErrorHandler>
          <TransitionRouteSwitch>
            <Route path={`${Paths.ScenariosBasePath}/*`} element={<ScenariosTab/>}/>
            <Route path={`${Paths.ProcessesTabDataPath}/*`} element={<ProcessesTab/>}/>
            <Route path={`${VisualizationBasePath}/:processId`} element={<VisualizationWrapped/>}/>
            <Route path={`${MetricsBasePath}/:processId?`} element={<Metrics/>}/>
            <Route path={`${Paths.CustomTabBasePath}/:id/*`} element={<CustomTab/>}/>
            <Route path={Paths.RootPath} element={<Navigate to={fallbackPath} replace/>}/>
            <Route path="*" element={<NotFound/>}/>
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

