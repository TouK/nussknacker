import {css, cx} from "@emotion/css"
import React, {useCallback, useEffect, useRef, useState} from "react"
import {alpha} from "../containers/theme"
import HttpService, {AppBuildInfo} from "../http/HttpService"

function useAppInfo(): AppBuildInfo {
  const [appInfo, setAppInfo] = useState<AppBuildInfo>()

  useEffect(() => {
    HttpService.fetchAppBuildInfo().then(res => setAppInfo(res.data))
  }, [])

  return appInfo
}

function Nu({size}: { size?: string }): JSX.Element {
  return (
    <svg fill="currentColor" style={{height: size}} viewBox="48 0 50 59">
      <path
        d="M68.35,58a15.64,15.64,0,0,1-5.53-1,12,12,0,0,1-4.48-2.95,13.79,13.79,0,0,1-3-5,20.89,20.89,0,0,1-1.08-7.07V29.89l9.62-6h.46V40a9.9,9.9,0,0,0,2,6.59q2,2.36,6,2.36h.8q4,0,6-2.36a9.9,9.9,0,0,0,2-6.59V23.89h.44l9.65,6V42.07a20.89,20.89,0,0,1-1.08,7.07,13.92,13.92,0,0,1-3,5,12,12,0,0,1-4.49,2.95,15.62,15.62,0,0,1-5.52,1Z"
      />
      <path
        d="M91.25,23.25v-5a7.86,7.86,0,0,0-1.08-4.18,9,9,0,0,0-3-3,14.81,14.81,0,0,0-4.49-1.74,25.42,25.42,0,0,0-5.52-.57h-1V1H69.39V8.8h-1a25.45,25.45,0,0,0-5.53.57,14.64,14.64,0,0,0-4.48,1.74,8.89,8.89,0,0,0-3,3,7.86,7.86,0,0,0-1.08,4.18v5h1.11L65.6,16.89l6.59,10.55h1.13l6.59-10.55,10.23,6.36Z"
      />
    </svg>
  )
}

export function VersionInfo(): JSX.Element {
  const appInfo = useAppInfo()
  const variedVersions = __BUILD_VERSION__ !== appInfo?.version

  const [expanded, setExpanded] = useState(false)
  const timeout = useRef(null)
  const show = useCallback(() => {
    clearTimeout(timeout.current)
    setExpanded(true)
  }, [])
  const hide = useCallback(() => {
    const t = 2000
    clearTimeout(timeout.current)
    timeout.current = setTimeout(() => {
      setExpanded(false)
    }, t)
  }, [])

  return ((
    <div
      data-testid="version-info"
      className={css({
        "&, div, svg": {
          transition: "all .25s",
        },

        color: alpha("black", .75),
        background: alpha("white", expanded ? .25 : 0),
        backdropFilter: expanded ? "blur(5px)" : "none",

        position: "absolute",
        bottom: 0,
        right: 0,
        left: 0,
        zIndex: 10,
        overflow: "hidden",

        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "flex-start",

        lineHeight: "1.2em",
        whiteSpace: "nowrap",
        fontSize: "75%",

        pointerEvents: expanded ? "auto" : "none",
      })}
      onMouseOver={show}
      onMouseOut={hide}
    >
      <div className={css({
        pointerEvents: "auto",
        padding: ".5em .5em .2em .5em",
        transform: `translateX(${expanded ? 0 : 25}%) translateY(${expanded ? 0 : 45}%) rotate(${expanded ? 0 : -15}deg)`,
        color: expanded ? "inherit" : alpha("black", .25),
      })}
      >
        <Nu size="2em"/>
      </div>
      <div className={css({
        transform: `translateY(${expanded ? 0 : 110}%)`,
        flex: 1,
      })}
      >
        <div
          className={css({fontWeight: "bolder"})}
        >{variedVersions ? `UI ${__BUILD_VERSION__}` : __BUILD_VERSION__}</div>
        {variedVersions && <div>API {appInfo?.version}</div>}
      </div>
    </div>
  ))
}
