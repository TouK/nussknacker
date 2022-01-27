import {defaultsDeep} from "lodash"
import React from "react"
import HealthCheck from "../components/HealthCheck"
import {ArchiveTabData} from "./Archive"
import {darkTheme} from "./darkTheme"
import {ProcessesTabData} from "./Processes"
import {SubProcessesTabData} from "./SubProcesses"
import {Tabs} from "../components/tabs/Tabs"

import {NkThemeProvider} from "./theme"
import {css} from "@emotion/css"

export function ProcessTabs() {
  return (
    <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
      <Tabs
        className={css({paddingBottom: "1.8em"})}
        tabs={[ProcessesTabData, SubProcessesTabData, ArchiveTabData]}
      >
        <HealthCheck/>
      </Tabs>
    </NkThemeProvider>
  )
}
