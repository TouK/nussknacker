import React, {ReactNode, useEffect, useState} from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {NavLink} from "react-router-dom"
import {ReactComponent as NussknackerLogo} from "../assets/img/nussknacker-logo.svg"
import {AdminPage} from "../containers/AdminPage"
import {CustomTabPath} from "../containers/CustomTab"
import {ProcessesTabData} from "../containers/Processes"
import {Signals} from "../containers/Signals"
import {getCustomTabs} from "../reducers/selectors/settings"
import {Flex} from "./common/Flex"
import {ButtonWithFocus} from "./withFocus"

type MenuItemData = {
  path: string,
  show: boolean,
  title: string,
}

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

function mapDynamicItems({name, id}: {name: string, id: string}) {
  return {show: true, path: `${CustomTabPath}/${id}`, title: name}
}

function createMenuItem({show, path, title}: MenuItemData): JSX.Element {
  return show ? <MenuItem key={title} path={path} title={title}/> : null
}

function MenuItem({title, path}: {title: string, path: string}) {
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
  const showSignals = featuresSettings.signals
  const showAdmin = loggedUser.globalPermissions.adminTab
  const customTabs = useSelector(getCustomTabs)

  const [expanded, setExpanded] = useStateWithRevertTimeout(false)
  const {t} = useTranslation()

  function buildMenu() {
    const defaultMenuItems: MenuItemData[] = [
      {show: true, path: ProcessesTabData.path, title: t("menu.processes", "Processes")},
      {show: showSignals, path: Signals.path, title: t("menu.signals", "Signals")},
      {show: showAdmin, path: AdminPage.path, title: t("menu.adminPage", "Admin")},
    ]

    const menuItems = defaultMenuItems.concat(
      customTabs.map(mapDynamicItems),
    ).map(createMenuItem)

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

