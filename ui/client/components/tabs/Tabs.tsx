//TODO: move all colors to theme
import {css, cx} from "@emotion/css"
import React, {ComponentType, PropsWithChildren} from "react"
import {useNkTheme} from "../../containers/theme"
import styles from "./processTabs.styl"
import {TabLink} from "./TabLink"
import {TabRoute} from "./TabRoute"

type TabData = { path: string, header: string, Component: ComponentType }

type Props = { tabs: TabData[], className?: string }

export function Tabs({tabs, children, className}: PropsWithChildren<Props>) {
  const {theme} = useNkTheme()
  return (
    <div className={cx(styles.tabsRoot, theme.themeClass, css({backgroundColor: theme.colors.canvasBackground}), className)}>
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
          {tabs.map(data => <TabLink key={data.path} {...data}/>)}
        </div>
        <div className={styles.contentWrap}>
          {tabs.map(data => <TabRoute key={data.path} {...data}/>)}
        </div>
      </div>
    </div>
  )
}
