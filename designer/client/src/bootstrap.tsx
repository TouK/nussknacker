import React from "react";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import ErrorBoundary from "./components/common/ErrorBoundary";
import NussknackerInitializer from "./containers/NussknackerInitializer";
import { SettingsProvider } from "./containers/SettingsInitializer";
import "./i18n";
import { StoreProvider } from "./store/provider";
import rootRoutes from "./containers/RootRoutes";
import { BASE_PATH } from "./config";
import { createRoot } from "react-dom/client";

const rootContainer = document.createElement(`div`);
rootContainer.id = "root";
const root = createRoot(rootContainer);
document.body.appendChild(rootContainer);

const router = createBrowserRouter(rootRoutes, { basename: BASE_PATH.replace(/\/$/, "") });

const Root = () => {
    return (
        <ErrorBoundary>
            <StoreProvider>
                <SettingsProvider>
                    <NussknackerInitializer>
                        <RouterProvider router={router} />
                    </NussknackerInitializer>
                </SettingsProvider>
            </StoreProvider>
        </ErrorBoundary>
    );
};

root.render(<Root />);
