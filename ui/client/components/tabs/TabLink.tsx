import {NavLink} from "react-router-dom"
import React from "react"
import {Tab} from "./Tab"
import styles from "./processTabs.styl"

export function TabLink({path, header}: {path: string, header: string}) {
  return (
    <NavLink
      to={path}
      activeClassName={styles.active}
      className={styles.link}
      exact
    >
      <Tab title={header}/>
    </NavLink>
  )
}
