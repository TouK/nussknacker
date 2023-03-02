import loadable from "@loadable/component"
import LoaderSpinner from "../components/Spinner"
import React, {useMemo} from "react"
import * as Paths from "./paths"
import {MetricsBasePath, RootPath, ScenariosBasePath, VisualizationBasePath} from "./paths"
import {createRoutesFromElements, Navigate, Route} from "react-router-dom"
import Metrics from "./Metrics"
import {NotFound} from "./errors/NotFound"
import {CustomTab, StarRedirect} from "./CustomTab"
import {useTabData} from "./CustomTabPage"
import {NussknackerApp} from "./NussknackerApp"

const Visualization = loadable(() => import("./Visualization"), {fallback: <LoaderSpinner show={true}/>})
const ProcessesTab = loadable(() => import("./ProcessesTab"), {fallback: <LoaderSpinner show={true}/>})
const ScenariosTab = loadable(() => import("./ScenariosTab"), {fallback: <LoaderSpinner show={true}/>})

function DefaultRedirect() {
  const rootTab = useTabData("scenarios")
  const defaultPath = useMemo(() => rootTab?.type === "Local" ? rootTab.url : Paths.ScenariosBasePath, [rootTab])
  return <Navigate to={defaultPath} replace/>
}

export default createRoutesFromElements(
  <Route path="/" element={<NussknackerApp/>}>
    <Route index element={<DefaultRedirect/>}/>
    <Route path="/404" element={<NotFound/>}/>

    <Route path={`${VisualizationBasePath}/:id`} element={<Visualization/>}/>

    {/* overrides scenarios custom tab */}
    <Route path={ScenariosBasePath} element={<ScenariosTab/>}/>

    {/* overrides legacy scenarios custom tab */}
    <Route path="/legacy_scenarios/*" element={<StarRedirect to={RootPath}/>}/>
    <Route path="/processes/*" element={<ProcessesTab/>}/>

    {/* overrides metrics custom tab */}
    <Route path={MetricsBasePath}>
      <Route index element={<Metrics/>}/>
      <Route path=":processId" element={<Metrics/>}/>
    </Route>

    <Route path="/:id/*" element={<CustomTab/>}/>

    <Route path="*" element={<Navigate to="/404" replace/>}/>
  </Route>
)

