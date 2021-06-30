import React, {PropsWithChildren, useEffect, useState} from "react"
import {useDispatch} from "react-redux"
import {assignSettings, SettingsData} from "../actions/nk"
import LoaderSpinner from "../components/Spinner"
import HttpService from "../http/HttpService"
import {AuthBackends, AuthenticationSettings} from "../reducers/settings";

export function SettingsProvider({children}: PropsWithChildren<unknown>): JSX.Element {
  const [data, setData] = useState<SettingsData>(null)
  const dispatch = useDispatch()

  useEffect(() => {
    HttpService.fetchSettings()
      .then(({data}) => {
        const {backend} = data.authentication;
        const settings = data
        return HttpService.fetchAuthenticationSettings<AuthenticationSettings>(backend)
          .then(({data}) => {
            return {
              ...settings,
              authentication: {
                ...settings.authentication,
                ...data,
              }
            }
          })
      })
      .then((settings) => {
        setData(settings)
        dispatch(assignSettings(settings))
      })
      .catch((error) => setData(() => {
        throw new Error(error)
      }))
  }, [dispatch])

  return data ? <>{children}</> : <LoaderSpinner show/>
}
