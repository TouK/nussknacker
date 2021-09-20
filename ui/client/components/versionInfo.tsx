import React, {useEffect, useState} from "react"
import HttpService, {AppBuildInfo} from "../http/HttpService"
import {css} from "emotion"
import {useUserSettings} from "../common/userSettings"
import {createPortal} from "react-dom"
import {alpha} from "../containers/theme"

function useAppInfo(): AppBuildInfo {
  const [appInfo, setAppInfo] = useState<AppBuildInfo>()

  useEffect(() => {
    HttpService.fetchAppBuildInfo().then(res => setAppInfo(res.data))
  }, [])

  return appInfo
}

export function VersionInfo(): JSX.Element {
  const appInfo = useAppInfo()
  const [settings] = useUserSettings()
  const variedVersions = __BUILD_VERSION__ !== appInfo?.version
  const forceExtend = settings["show.version.extended"]
  const [eventExtended, setExtended] = useState(false)
  const extended = eventExtended || forceExtend
  return createPortal((
    <div
      className={css({
        display: window["Cypress"] ? "none" : "block",
        position: "absolute",
        bottom: 0,
        zIndex: eventExtended ? 1000 : 0,
        padding: "2em 2em .5em .5em",
        textShadow: `1px 1px 3px ${alpha("black", .25)}`,
        lineHeight: "1.4em",
        transformOrigin: "bottom left",
        color: alpha("white", extended ? .8 : .5),
        transition: "all .5s",
        transform: extended ? "scale(1)" : "scale(.75)",
      })}
      onPointerEnter={() => setExtended(true)}
      onPointerLeave={() => setExtended(false)}
    >
      <div className={css({fontWeight: "bolder"})}>
        {variedVersions ? `UI ${__BUILD_VERSION__}` : __BUILD_VERSION__}
      </div>
      <div className={css({display: variedVersions && extended ? "block" : "none"})}>
        API {appInfo?.version}
      </div>
    </div>
  ), document.body)
}
