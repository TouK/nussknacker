import React, {PropsWithChildren, useCallback} from "react"
import {useDispatch, useSelector} from "react-redux"
import {assignUser} from "../actions/nk"
import HttpService from "../http/HttpService"
import {getAuthenticationSettings} from "../reducers/selectors/settings"
import {AuthInitializer} from "./Auth"

function NussknackerInitializer({children}: PropsWithChildren<unknown>): JSX.Element {
  const dispatch = useDispatch()

  const onAuth = useCallback(
    () => HttpService.fetchLoggedUser().then(({data}) => {
      dispatch(assignUser(data))
    }),
    [dispatch],
  )

  const authenticationSettings = useSelector(getAuthenticationSettings)

  return (
    <AuthInitializer authenticationSettings={authenticationSettings} onAuthFulfilled={onAuth}>
      {children}
    </AuthInitializer>
  )
}

export default NussknackerInitializer
