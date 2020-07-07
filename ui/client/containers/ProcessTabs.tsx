import React, {ComponentType} from "react"
import {ArchiveTabData} from "./Archive"
import {ProcessesTabData} from "./Processes"
import styles from "./processTabs.styl"
import {SubProcessesTabData} from "./SubProcesses"
import {TabLink} from "./TabLink"
import {TabRoute} from "./TabRoute"

type TabData = {path: string, header: string, Component: ComponentType}

function Tabs({tabs}: {tabs: TabData[]}) {
  return (
    <div className={styles.tabsWrap}>
      <div className={styles.tabs}>
        {tabs.map(r => <TabLink key={r.path} {...r}/>)}
      </div>
      <div className={styles.contentWrap}>
        {tabs.map(r => <TabRoute key={r.path} {...r}/>)}
      </div>
    </div>
  )
}

export function ProcessTabs() {
  return (
    <Tabs tabs={[ProcessesTabData, SubProcessesTabData, ArchiveTabData]}/>
  )
}
