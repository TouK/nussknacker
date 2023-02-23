import loadable from "@loadable/component"
import LoaderSpinner from "../components/Spinner"
import React, {useMemo} from "react"
import * as Paths from "./paths"
import {MetricsBasePath, VisualizationBasePath} from "./paths"
import {Navigate, useRoutes} from "react-router-dom"
import Metrics from "./Metrics"
import Services from "./Services"
import {NotFound} from "./errors/NotFound"
import {CustomTab, RedirectStar} from "./CustomTab"
import {useTabData} from "./CustomTabPage"

const VisualizationWrapped = loadable(() => import("./VisualizationWrapped"), {fallback: <LoaderSpinner show={true}/>})
const ProcessesTab = loadable(() => import("./ProcessesTab"), {fallback: <LoaderSpinner show={true}/>})
const ScenariosTab = loadable(() => import("./ScenariosTab"), {fallback: <LoaderSpinner show={true}/>})

export function RootRoutes() {
  const rootTab = useTabData("scenarios")
  const fallbackPath = useMemo(() => rootTab?.type === "Local" ? rootTab.url : Paths.ScenariosBasePath, [rootTab])
  return useRoutes([
    {
      path: "/",
      element: <Navigate to={fallbackPath} replace/>,
    },
    {
      path: "/404",
      element: <NotFound/>,
    },
    {
      path: `${VisualizationBasePath}/:processId`,
      element: <VisualizationWrapped/>,
    },
    {
      path: Paths.ScenariosBasePath,
      element: <ScenariosTab/>,
    },
    {
      path: `/legacy_scenarios/*`,
      element: <RedirectStar to={`/`}/>,
    },
    {
      path: `/processes/*`,
      element: <ProcessesTab/>,
    },
    {
      path: "/services",
      element: <Services/>,
    },
    {
      path: MetricsBasePath,
      children: [
        {
          index: true,
          element: <Metrics/>,
        },
        {
          path: `:processId`,
          element: <Metrics/>,
        },
      ],
    },
    {
      path: "/:id/*",
      element: <CustomTab/>,
    },
    {
      path: "/*",
      element: <Navigate to="/404" replace/>,
    },
  ])
}
