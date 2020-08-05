import cn from "classnames"
import React, {ComponentType, PropsWithChildren} from "react"
import HealthCheck from "../components/HealthCheck"
import darkStyles from "../stylesheets/darkColors.styl"
import {ArchiveTabData} from "./Archive"
import {ProcessesTabData} from "./Processes"
import styles from "./processTabs.styl"
import {SubProcessesTabData} from "./SubProcesses"
import {TabLink} from "./TabLink"
import {TabRoute} from "./TabRoute"

type TabData = {path: string, header: string, Component: ComponentType}

function Tabs({tabs, children}: PropsWithChildren<{tabs: TabData[]}>) {
  return (
    <div className={cn(darkStyles.canvas)}>
      <div className={styles.tabsWrap}>
        {children}
        <div
          className={cn([
            styles.tabs,
            styles.withBottomLine,
            styles.withDrop,
            styles.rounded,
          ])}
        >
          {tabs.map(r => <TabLink key={r.path} {...r}/>)}
        </div>
        <div className={styles.contentWrap}>
          {tabs.map(r => <TabRoute key={r.path} {...r}/>)}
        </div>
      </div>
    </div>
  )
}

export function ProcessTabs() {
  return (
    <Tabs tabs={[ProcessesTabData, SubProcessesTabData, ArchiveTabData]}>
      <HealthCheck/>
    </Tabs>
  )
}
