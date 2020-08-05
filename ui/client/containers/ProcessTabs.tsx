import {css, cx} from "emotion"
import {defaultsDeep} from "lodash"
import React, {ComponentType, PropsWithChildren} from "react"
import HealthCheck from "../components/HealthCheck"
import {ArchiveTabData} from "./Archive"
import {darkTheme} from "./darkTheme"
import {ProcessesTabData} from "./Processes"
import styles from "./processTabs.styl"
import {SubProcessesTabData} from "./SubProcesses"
import {TabLink} from "./TabLink"
import {TabRoute} from "./TabRoute"
import {NkThemeProvider, useNkTheme} from "./theme"

type TabData = {path: string, header: string, Component: ComponentType}

//TODO: move all colors to theme
function Tabs({tabs, children}: PropsWithChildren<{tabs: TabData[]}>) {
  const {theme} = useNkTheme()
  return (
    <div className={cx(theme.themeClass, css({backgroundColor: theme.colors.canvasBackground}))}>
      <div className={cx(styles.tabsWrap)}>
        {children}
        <div
          className={cx([
            styles.tabs,
            styles.withBottomLine,
            styles.withDrop,
            theme.borderRadius && styles.rounded,
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
    <NkThemeProvider theme={outerTheme => defaultsDeep(darkTheme, outerTheme)}>
      <Tabs tabs={[ProcessesTabData, SubProcessesTabData, ArchiveTabData]}>
        <HealthCheck/>
      </Tabs>
    </NkThemeProvider>
  )
}
