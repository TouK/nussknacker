import React, {ReactNode, useEffect, useState} from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {NavLink} from "react-router-dom"
import {ReactComponent as NussknackerLogo} from "../assets/img/nussknacker-logo.svg"
import {CustomTabPath} from "../containers/CustomTab"
import {getLoggedUser, getTabs} from "../reducers/selectors/settings"
import {Flex} from "./common/Flex"
import {ButtonWithFocus} from "./withFocus"
import {useSearchQuery} from "../containers/hooks/useSearchQuery"

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

function Menu({onClick}: { onClick: () => void }): JSX.Element {
  const tabs = useSelector(getTabs)
  const loggedUser = useSelector(getLoggedUser)
  const dynamicTabData = tabs.filter(({requiredPermission}) => !requiredPermission || loggedUser.hasGlobalPermission(requiredPermission))
  return (
    <ul id="menu-items" onClick={onClick}>
      {dynamicTabData.map(({title, id, type, url}) => (
        <li key={id}>
          <NavLink to={type === "Local" ? url : `${CustomTabPath}/${id}`}>{title}</NavLink>
        </li>
      ))}
    </ul>
  )
}

type Props = {
  appPath: string,
  rightElement?: ReactNode,
  leftElement?: ReactNode,
}

const Spacer = () => <Flex flex={1}/>

export function MenuBar({appPath, rightElement = null, leftElement = null}: Props): JSX.Element {
  const [expanded, setExpanded] = useStateWithRevertTimeout(false)
  const {t} = useTranslation()

  /**
   * In some cases (eg. docker demo) we serve Grafana and Kibana from nginx proxy, from root app url, and when service responds with error
   * then React app catches this and shows error page. To make it render only error, without app menu, we have mark iframe
   * requests with special query parameter so that we can recognize them and skip menu rendering.
   */
  const [{iframe: isLoadAsIframe}] = useSearchQuery<{ iframe: boolean }>()
  if (isLoadAsIframe) {
    return null
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
          <ButtonWithFocus className="expand-button" onClick={() => setExpanded(v => !v)}>
            <span className={`glyphicon glyphicon-menu-${expanded ? "up" : "down"}`}/>
          </ButtonWithFocus>
          <Menu onClick={() => setExpanded(false)}/>
        </Flex>
      </nav>
    </header>
  )
}

