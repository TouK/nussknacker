import React from "react";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import NussknackerInitializer from "./containers/NussknackerInitializer";
import { SettingsProvider } from "./containers/SettingsInitializer";
import "./i18n";
import { StoreProvider } from "./store/provider";
import rootRoutes from "./containers/RootRoutes";
import { BASE_PATH } from "./config";
import { css } from "@emotion/css";
import { NuThemeProvider } from "./containers/theme/nuThemeProvider";
import { GlideGridPortal } from "./components/graph/node-modal/editors/expression/Table/glideGridPortal";
import { createRoot } from "react-dom/client";
import { BuildInfoProvider } from "./containers/BuildInfoProvider";
import { ErrorBoundary } from "./components/common/error-boundary";

const rootContainer = document.createElement(`div`);
rootContainer.id = "root";
rootContainer.className = css({
    height: "100dvh",
    display: "flex",
});
document.body.appendChild(rootContainer);

const router = createBrowserRouter(rootRoutes, { basename: BASE_PATH.replace(/\/$/, "") });

const root = createRoot(rootContainer);

const Root = () => {
    return (
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
};

root.render(<Root />);
