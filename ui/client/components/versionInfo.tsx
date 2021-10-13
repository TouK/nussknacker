import {css} from "@emotion/css"
import React, {useEffect, useState} from "react"
import {alpha} from "../containers/theme"
import HttpService, {AppBuildInfo} from "../http/HttpService"

function useAppInfo(): AppBuildInfo {
  const [appInfo, setAppInfo] = useState<AppBuildInfo>()

  useEffect(() => {
    HttpService.fetchAppBuildInfo().then(res => setAppInfo(res.data))
  }, [])

  return appInfo
}

export function VersionInfo(): JSX.Element {
  const appInfo = useAppInfo()
  const variedVersions = __BUILD_VERSION__ !== appInfo?.version
  return ((
    <div
      className={css({
        position: "absolute",
        bottom: 0,
        zIndex: 1,
        padding: ".5em",
        lineHeight: "1.4em",
        transformOrigin: "bottom left",
        color: alpha("black", .25),
        whiteSpace: "nowrap",
        transform: "scale(.75)",
      })}
    >
      <div className={css({fontWeight: "bolder"})}>{variedVersions ? `UI ${__BUILD_VERSION__}` : __BUILD_VERSION__}</div>
      {variedVersions && <div>API {appInfo?.version}</div>}
    </div>
  ))
}
