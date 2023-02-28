import React from "react"
import ReactDOM from "react-dom"
import {createBrowserRouter, createRoutesFromElements, RouterProvider} from "react-router-dom"
import ErrorBoundary from "./components/common/ErrorBoundary"
import NussknackerInitializer from "./containers/NussknackerInitializer"
import {SettingsProvider} from "./containers/SettingsInitializer"
import "./i18n"
import {StoreProvider} from "./store/provider"
import rootRoutes from "./containers/RootRoutes"

const rootContainer = document.createElement(`div`)
rootContainer.id = "root"
document.body.appendChild(rootContainer)

const router = createBrowserRouter(rootRoutes)

const Root = () => {
  return (
    <ErrorBoundary>
      <StoreProvider>
        <SettingsProvider>
          <NussknackerInitializer>
            <RouterProvider router={router}/>
          </NussknackerInitializer>
        </SettingsProvider>
      </StoreProvider>
    </ErrorBoundary>
  )
}

ReactDOM.render(<Root/>, rootContainer)
