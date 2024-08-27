import loadable from "@loadable/component";
import LoaderSpinner from "../components/spinner/Spinner";
import React, { useMemo } from "react";
import * as Paths from "./paths";
import { MetricsBasePath, RootPath, ScenariosBasePath, VisualizationBasePath } from "./paths";
import { createRoutesFromElements, Navigate, Route } from "react-router-dom";
import Metrics from "./Metrics";
import { NotFound } from "../components/common/error-boundary/NotFound";
import { CustomTab, StarRedirect } from "./CustomTab";
import { useTabData } from "./CustomTabPage";
import { NussknackerApp } from "./NussknackerApp";
import { FullPageErrorBoundaryFallbackComponent } from "../components/common/error-boundary";

const Visualization = loadable(() => import("./Visualization"), { fallback: <LoaderSpinner show={true} /> });
const ScenariosTab = loadable(() => import("./ScenariosTab"), { fallback: <LoaderSpinner show={true} /> });

function DefaultRedirect() {
    const rootTab = useTabData("scenarios");
    const defaultPath = useMemo(() => (rootTab?.type === "Local" ? rootTab.url : Paths.ScenariosBasePath), [rootTab]);
    return <Navigate to={defaultPath} replace />;
}

export default createRoutesFromElements(
    <Route path="/" element={<NussknackerApp />} errorElement={<FullPageErrorBoundaryFallbackComponent />}>
        <Route index element={<DefaultRedirect />} />
        <Route path="/404" element={<NotFound />} />
        <Route errorElement={<FullPageErrorBoundaryFallbackComponent />}>
            <Route path={`${VisualizationBasePath}/:processName`} element={<Visualization />} />

            {/* overrides scenarios custom tab */}
            <Route path={ScenariosBasePath} element={<ScenariosTab />} />

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
