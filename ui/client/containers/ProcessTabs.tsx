import {Switch} from "react-router-dom"
import * as Processes from "./Processes"
import * as SubProcesses from "./SubProcesses"
import * as Archive from "./Archive"
import React from "react"
import {TabRoute} from "./TabRoute"
import {TabLink} from "./TabLink"
import styles from "./ProcessTabs.styl"
import {hot} from "react-hot-loader"

const routes = [Processes, SubProcesses, Archive]

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
