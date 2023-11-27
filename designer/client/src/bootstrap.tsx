import React from "react";
import ReactDOM from "react-dom";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import NussknackerInitializer from "./containers/NussknackerInitializer";
import { SettingsProvider } from "./containers/SettingsInitializer";
import "./i18n";
import { StoreProvider } from "./store/provider";
import rootRoutes from "./containers/RootRoutes";
import { BASE_PATH } from "./config";
import { css } from "@emotion/css";
import RootErrorBoundary from "./components/common/RootErrorBoundary";
import { NuThemeProvider } from "./containers/theme/nuThemeProvider";
import { FixedPortal } from "./fixedPortal";

const rootContainer = document.createElement(`div`);
rootContainer.id = "root";
rootContainer.className = css({
    height: "100dvh",
    display: "flex",
});
document.body.appendChild(rootContainer);

const router = createBrowserRouter(rootRoutes, { basename: BASE_PATH.replace(/\/$/, "") });

const Root = () => {
    return (
        <>
            <NuThemeProvider>
                <RootErrorBoundary>
                    <StoreProvider>
                        <SettingsProvider>
                            <NussknackerInitializer>
                                <RouterProvider router={router} />
                            </NussknackerInitializer>
                        </SettingsProvider>
                    </StoreProvider>
                </RootErrorBoundary>
            </NuThemeProvider>
            <FixedPortal />
        </>
    );
};

ReactDOM.render(<Root />, rootContainer);
