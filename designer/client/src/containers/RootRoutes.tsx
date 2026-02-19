import loadable from "@loadable/component";
import React, { useMemo } from "react";
import { createRoutesFromElements, Navigate, Route } from "react-router-dom";

import { RouteErrorFallbackComponent } from "../components/common/error-boundary/fallbackComponent/RouteErrorFallbackComponent";
import { NotFound } from "../components/common/error-boundary/NotFound";
import LoaderSpinner from "../components/spinner/Spinner";
import { CustomTab, StarRedirect } from "./CustomTab";
import { useTabData } from "./CustomTabPage";
import Metrics from "./Metrics";
import { NussknackerApp } from "./NussknackerApp";
import { MetricsBasePath, RootPath, ScenariosBasePath, TopicsBasePath, VisualizationBasePath } from "./paths";

const Visualization = loadable(() => import("./Visualization"), { fallback: <LoaderSpinner show={true} /> });
const ScenariosTab = loadable(() => import("./ScenariosTab"), { fallback: <LoaderSpinner show={true} /> });
const TopicsTab = loadable(() => import("./TopicsTab"), { fallback: <LoaderSpinner show={true} /> });
const SettingsView = loadable(() => import("./settings/SettingsView"), { fallback: <LoaderSpinner show={true} /> });

function DefaultRedirect() {
    const rootTab = useTabData("scenarios");
    const defaultPath = useMemo(() => (rootTab?.type === "Local" ? rootTab.url : ScenariosBasePath), [rootTab]);
    return <Navigate to={defaultPath} replace />;
}

export default createRoutesFromElements(
    <Route path="/" element={<NussknackerApp />} errorElement={<RouteErrorFallbackComponent />}>
        <Route index element={<DefaultRedirect />} />
        <Route path="/404" element={<NotFound />} />
        <Route path="/__settings" element={<SettingsView />} />
        <Route path="/__debug">
            <Route path="ai" Component={loadable(() => import("../components/aiAssistant/debug/DebugView"))} />
        </Route>
        <Route errorElement={<RouteErrorFallbackComponent />}>
            <Route path={`${VisualizationBasePath}/:processName`} element={<Visualization />} />
            <Route path={`${VisualizationBasePath}/:processName/:version`} element={<Visualization />} />

            {/* overrides scenarios custom tab */}
            <Route path={ScenariosBasePath} element={<ScenariosTab />} />
            <Route path={TopicsBasePath}>
                <Route index element={<TopicsTab />} />
                <Route path="*" element={<TopicsTab />} />
            </Route>

            {/* for the backward compatibility we redirect old urls */}
            <Route path="/legacy_scenarios/*" element={<StarRedirect to={RootPath} />} />
            <Route path="/processes/*" element={<StarRedirect to={RootPath} />} />

            {/* overrides metrics custom tab */}
            <Route path={MetricsBasePath}>
                <Route index element={<Metrics />} />
                <Route path=":processName" element={<Metrics />} />
            </Route>

            <Route path="/:id/*" element={<CustomTab />} />
        </Route>
        <Route path="*" element={<Navigate to="/404" replace />} />
    </Route>,
);
