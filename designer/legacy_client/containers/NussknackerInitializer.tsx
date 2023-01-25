import React, {PropsWithChildren, useCallback} from "react"
import {useDispatch} from "react-redux"
import HttpService from "../http/HttpService"
import {AuthInitializer} from "./Auth"
import {useAuthenticationSettings} from "../reducers/selectors/settings"
import User from "../common/models/User"

function NussknackerInitializer({children}: PropsWithChildren<unknown>): JSX.Element {
  const dispatch = useDispatch()

  const onAuth = useCallback(
    () => HttpService.fetchLoggedUser().then(({data}) => {
      dispatch({
        type: "LOGGED_USER",
        user: new User(data),
      })
    }),
    [dispatch],
  )

  const authenticationSettings = useAuthenticationSettings()

  return (
    <AuthInitializer authenticationSettings={authenticationSettings} onAuthFulfilled={onAuth}>
      {children}
    </AuthInitializer>
  )
}

export default NussknackerInitializer
