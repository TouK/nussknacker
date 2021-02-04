import React, {PropsWithChildren, useCallback} from "react"
import {useDispatch} from "react-redux"
import {assignUser} from "../actions/nk"
import HttpService from "../http/HttpService"
import {AuthInitializer} from "./Auth"

function NussknackerInitializer({children}: PropsWithChildren<unknown>): JSX.Element {
  const dispatch = useDispatch()

  const onAuth = useCallback(
    () => HttpService.fetchLoggedUser().then(({data}) => {
      dispatch(assignUser(data))
    }),
    [dispatch],
  )

  return (
    <AuthInitializer onAuthFulfilled={onAuth}>
      {children}
    </AuthInitializer>
  )
}

export default NussknackerInitializer
