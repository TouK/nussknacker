import React, {PropsWithChildren, useEffect, useState} from "react"
import {useDispatch} from "react-redux"
import {SettingsData} from "../actions/nk"
import LoaderSpinner from "../components/Spinner"
import HttpService from "../http/HttpService"

export function SettingsProvider({children}: PropsWithChildren<unknown>): JSX.Element {
  const [data, setData] = useState<SettingsData>(null)
  const dispatch = useDispatch()

  useEffect(() => {
    HttpService.fetchSettingsWithAuth()
      .then((settings) => {
        setData(settings)
        dispatch({
          type: "UI_SETTINGS",
          settings: settings,
        })
      })
      .catch((error) => setData(() => {
        throw error
      }))
  }, [dispatch])

  return data ? <>{children}</> : <LoaderSpinner show/>
}
