import {defaultsDeep} from "lodash"
import React, {createContext} from "react"
import HealthCheck from "../components/HealthCheck"
import {ArchiveTabData} from "./Archive"
import {darkTheme} from "./darkTheme"
import {ProcessesTabData} from "./Processes"
import {SubProcessesTabData} from "./SubProcesses"
import {Tabs} from "../components/tabs/Tabs"
import {NkThemeProvider} from "./theme"
import {Router} from "react-router-dom"
import history from "../history"

interface ProcessTabsProps {
  onScenarioAdd: () => void,
  onFragmentAdd: () => void,
  scenarioLinkGetter: (scenarioId: string) => string,
  metricsLinkGetter: (scenarioId: string) => string,
}

export const ScenariosContext = createContext<ProcessTabsProps>(null)

function ProcessTabs(props: ProcessTabsProps) {
  return (
    <Router history={history}>
      <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
        <ScenariosContext.Provider value={props}>
          <Tabs tabs={[ProcessesTabData, SubProcessesTabData, ArchiveTabData]}>
            <HealthCheck/>
          </Tabs>
        </ScenariosContext.Provider>
      </NkThemeProvider>
    </Router>
  )
}

export default ProcessTabs
