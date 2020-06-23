import _ from "lodash"
import React, {ReactNode, useEffect, useState} from "react"
import {useTranslation} from "react-i18next"
import {NavLink} from "react-router-dom"
import {AdminPage} from "../containers/AdminPage"
import {ArchiveTabData} from "../containers/Archive"
import {EspApp} from "../containers/EspApp"
import {Metrics} from "../containers/Metrics"
import {ProcessesTabData} from "../containers/Processes"
import {Search} from "../containers/Search"
import {Signals} from "../containers/Signals"
import {SubProcessesTabData} from "../containers/SubProcesses"
import {Flex} from "./common/Flex"

function useStateWithRevertTimeout<T>(startValue: T, time = 10000): [T, React.Dispatch<React.SetStateAction<T>>] {
  const [defaultValue] = useState<T>(startValue)
  const [value, setValue] = useState<T>(defaultValue)
  useEffect(() => {
    let t: NodeJS.Timeout
    if (value) {
      t = setTimeout(() => {
        setValue(defaultValue)
      }, time)
    }
    return () => clearTimeout(t)
  }, [value, time])
  return [value, setValue]
}

type Props = {
  app: typeof EspApp,
  rightElement?: ReactNode,
  leftElement?: ReactNode,
  loggedUser: $TodoType,
  featuresSettings: $TodoType,
}

const Spacer = () => <Flex flex={1}/>

export function MenuBar({rightElement = null, leftElement = null, ...props}: Props) {
  const {app: {path, header}, loggedUser, featuresSettings} = props
  const showMetrics = !_.isEmpty(featuresSettings.metrics)
  const showSearch = !_.isEmpty(featuresSettings.search)
  const showSignals = featuresSettings.signals
  const showAdmin = loggedUser.globalPermissions.adminTab

  const [expanded, setExpanded] = useStateWithRevertTimeout(false)
  const {t} = useTranslation()

  return (
    <header>
      <nav id="main-menu" className={`navbar navbar-default ${expanded ? "expanded" : "collapsed"}`}>
        <Flex>
          {leftElement}
          <NavLink id="brand-name" className="navbar-brand" to={path}>
            <span id="app-logo" className="vert-middle">{header}</span>
          </NavLink>
          {rightElement}
          <Spacer/>
          <button className="expand-button" onClick={() => setExpanded(!expanded)}>
            <span className={`glyphicon glyphicon-menu-${expanded ? "up" : "down"}`}/>
          </button>
          <ul id="menu-items" onClick={() => setExpanded(false)}>
            <MenuItem path={ProcessesTabData.path} title={t("menu.processes", "Processes")}/>
            <MenuItem path={SubProcessesTabData.path} title={t("menu.subProcesses", "Subprocesses")}/>
            {showMetrics && <MenuItem path={Metrics.basePath} title={t("menu.metrics", "Metrics")}/>}
            {showSearch && <MenuItem path={Search.path} title={t("menu.search", "Search")}/>}
            {showSignals && <MenuItem path={Signals.path} title={t("menu.signals", "Signals")}/>}
            <MenuItem path={ArchiveTabData.path} title={t("menu.archive", "Archive")}/>
            {showAdmin && <MenuItem path={AdminPage.path} title={t("menu.adminPage", "Admin")}/>}
          </ul>
        </Flex>
      </nav>
    </header>
  )
}

function MenuItem({title, path}: { title: string, path: string }) {
  return <li key={title}><NavLink to={path}>{title}</NavLink></li>
}

