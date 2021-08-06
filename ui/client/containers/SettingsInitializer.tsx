import React, {PropsWithChildren, useEffect, useState} from "react"
import {useDispatch} from "react-redux"
import {assignSettings, SettingsData} from "../actions/nk"
import LoaderSpinner from "../components/Spinner"
import HttpService from "../http/HttpService"

export function SettingsProvider({children}: PropsWithChildren<unknown>): JSX.Element {
  const [data, setData] = useState<SettingsData>(null)
  const dispatch = useDispatch()

  useEffect(() => {
    HttpService.fetchSettingsWithAuth()
      .then((settings) => {
        setData(settings)
        dispatch(assignSettings(settings))
      })
      .catch((error) => setData(() => {
        throw error
      }))
  }, [dispatch])

  return data ? <>{children}</> : <LoaderSpinner show/>
}
