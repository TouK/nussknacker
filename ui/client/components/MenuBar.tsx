import _ from "lodash"
import React, {ReactNode, useEffect, useState} from "react"
import {useTranslation} from "react-i18next"
import {NavLink} from "react-router-dom"
import {AdminPage} from "../containers/AdminPage"
import {ReactComponent as NussknackerLogo} from "../assets/img/nussknacker-logo.svg"
import {Metrics} from "../containers/Metrics"
import {ProcessesTabData} from "../containers/Processes"
import {Signals} from "../containers/Signals"
import {Flex} from "./common/Flex"
import {CustomTabs} from "../containers/CustomTabs"
import {ButtonWithFocus} from "./withFocus"

function useStateWithRevertTimeout<T>(startValue: T, time = 10000): [T, React.Dispatch<React.SetStateAction<T>>] {
  const [defaultValue] = useState<T>(startValue)
  const [value, setValue] = useState<T>(defaultValue)
  useEffect(() => {
    let t
    if (value) {
      t = setTimeout(() => {
        setValue(defaultValue)
      }, time)
    }
    return () => clearTimeout(t)
  }, [value, time])
  return [value, setValue]
}

function mapDynamicItems(title: string, id: string) {
  return {show: true, path: `${CustomTabs.path}/${id}`, title: title}
}

function createMenuItem(show: boolean, path: string, title: string) {
  return show && <MenuItem key={title} path={path} title={title}/>
}

function MenuItem({title, path}: { title: string, path: string }) {
  return <li key={title}><NavLink to={path}>{title}</NavLink></li>
}

type Props = {
  appPath: string,
  rightElement?: ReactNode,
  leftElement?: ReactNode,
  loggedUser: $TodoType,
  featuresSettings: $TodoType,
}

const Spacer = () => <Flex flex={1}/>

export function MenuBar({appPath, rightElement = null, leftElement = null, ...props}: Props) {
  const {loggedUser, featuresSettings} = props
  const showMetrics = !_.isEmpty(featuresSettings.metrics)
  const showSignals = featuresSettings.signals
  const showAdmin = loggedUser.globalPermissions.adminTab
  const customTabs = featuresSettings.customTabs ? [...featuresSettings.customTabs] : []

  const [expanded, setExpanded] = useStateWithRevertTimeout(false)
  const {t} = useTranslation()

  function buildMenu() {
    const defaultMenuItems = [
      {show: true, path: ProcessesTabData.path, title: t("menu.processes", "Processes")},
      {show: showMetrics, path: Metrics.basePath, title: t("menu.metrics", "Metrics")},
      {show: showSignals, path: Signals.path, title: t("menu.signals", "Signals")},
      {show: showAdmin, path: AdminPage.path, title: t("menu.adminPage", "Admin")},
    ]

    const dynamicMenuItems = customTabs
      .map((element) => mapDynamicItems(element.name, element.id))

    const menuItems = defaultMenuItems
      .concat(dynamicMenuItems)
      .map(o => createMenuItem(o.show, o.path, o.title))

    return (
      <ul id="menu-items" onClick={() => setExpanded(false)}>
        {menuItems}
      </ul>
    )
  }

  return (
    <header>
      <nav id="main-menu" className={`navbar navbar-default ${expanded ? "expanded" : "collapsed"}`}>
        <Flex>
          {leftElement}
          <NavLink className="navbar-brand" to={appPath} title={t("menu.goToMainPage", "Go to main page")}>
            <NussknackerLogo className={"navbar-brand-logo"}/>
          </NavLink>
          {rightElement}
          <Spacer/>
          <ButtonWithFocus className="expand-button" onClick={() => setExpanded(!expanded)}>
            <span className={`glyphicon glyphicon-menu-${expanded ? "up" : "down"}`}/>
          </ButtonWithFocus>
          {buildMenu()}
        </Flex>
      </nav>
    </header>
  )
}

