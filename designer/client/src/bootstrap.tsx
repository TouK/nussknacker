import { css } from "@emotion/css";
import React from "react";
import { createRoot } from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import { ErrorBoundary } from "./components/common/error-boundary";
import { GlideGridPortal } from "./components/graph/node-modal/editors/expression/Table/glideGridPortal";
import { BASE_PATH } from "./config";
import { BuildInfoProvider } from "./containers/BuildInfoProvider";
import NussknackerInitializer from "./containers/NussknackerInitializer";
import rootRoutes from "./containers/RootRoutes";
import { SettingsProvider } from "./containers/SettingsInitializer";
import "./i18n";
import { NuThemeProvider } from "./containers/theme/nuThemeProvider";
import { StoreProvider } from "./store/provider";

const rootContainer = document.createElement(`div`);
rootContainer.id = "root";
rootContainer.className = css({
    height: "100dvh",
    display: "flex",
});
document.body.appendChild(rootContainer);

const router = createBrowserRouter(rootRoutes, { basename: BASE_PATH.replace(/\/$/, "") });

const root = createRoot(rootContainer);

const Root = () => (
    <>
        <NuThemeProvider>
            <ErrorBoundary>
                <StoreProvider>
                    <SettingsProvider>
                        <NussknackerInitializer>
                            <BuildInfoProvider>
                                <RouterProvider router={router} />
                            </BuildInfoProvider>
                        </NussknackerInitializer>
                    </SettingsProvider>
                </StoreProvider>
            </ErrorBoundary>
        </NuThemeProvider>
        <GlideGridPortal />
    </>
);

root.render(<Root />);
