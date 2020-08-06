import {defaultsDeep} from "lodash"
import React from "react"
import HealthCheck from "../components/HealthCheck"
import {ArchiveTabData} from "./Archive"
import {darkTheme} from "./darkTheme"
import {ProcessesTabData} from "./Processes"
import {SubProcessesTabData} from "./SubProcesses"
import {Tabs} from "../components/tabs/Tabs"

import {NkThemeProvider} from "./theme"

export function ProcessTabs() {
  return (
    <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
      <Tabs tabs={[ProcessesTabData, SubProcessesTabData, ArchiveTabData]}>
        <HealthCheck/>
      </Tabs>
    </NkThemeProvider>
  )
}
