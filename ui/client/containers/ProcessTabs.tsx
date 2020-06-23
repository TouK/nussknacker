import React, {ComponentType} from "react"
import {hot} from "react-hot-loader"
import {Switch} from "react-router-dom"
import {ArchiveTabData} from "./Archive"
import {ProcessesTabData} from "./Processes"
import styles from "./ProcessTabs.styl"
import {SubProcessesTabData} from "./SubProcesses"
import {TabLink} from "./TabLink"
import {TabRoute} from "./TabRoute"

const routes: {path: string, header: string, Component: ComponentType}[] = [
  ProcessesTabData,
  SubProcessesTabData,
  ArchiveTabData,
]

function Tabs() {
  return (
    <div className={styles.tabsWrap}>
      <div className={styles.tabs}>
        {routes.map(r => <TabLink key={r.path} {...r}/>)}
      </div>
      <div className={styles.contentWrap}>
        <Switch>
          {routes.map(r => <TabRoute key={r.path} {...r}/>)}
        </Switch>
      </div>
    </div>
  )
}

export const ProcessTabs = hot(module)(Tabs)
