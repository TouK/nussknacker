import React, {Suspense} from "react"
import ReactDOM from "react-dom"
import ErrorBoundary from "./components/common/ErrorBoundary"
import LoaderSpinner from "./components/Spinner"
import NussknackerInitializer from "./containers/NussknackerInitializer"
import {SettingsProvider} from "./containers/SettingsInitializer"
import {NkThemeProvider} from "./containers/theme"
import "./i18n"
import {StoreProvider} from "./store/provider"
import ProcessTabs from "./containers/ProcessTabs"

const rootContainer = document.createElement(`div`)
rootContainer.id = "root"
document.body.appendChild(rootContainer)

const Root = () => (
  <Suspense fallback={<LoaderSpinner show/>}>
    <ErrorBoundary>
      <StoreProvider>
        <SettingsProvider>
          <NussknackerInitializer>
            <NkThemeProvider>
              <ProcessTabs
                onFragmentAdd={() => console.log("fragment")}
                onScenarioAdd={() => console.log("scenario")}
                metricsLinkGetter={id => `/metrics/${encodeURIComponent(id)}`}
                scenarioLinkGetter={id => `/visualization/${encodeURIComponent(id)}`}
              />
            </NkThemeProvider>
          </NussknackerInitializer>
        </SettingsProvider>
      </StoreProvider>
    </ErrorBoundary>
  </Suspense>
)

ReactDOM.render(<Root/>, rootContainer)
