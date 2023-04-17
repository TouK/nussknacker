import React, {PropsWithChildren} from "react"
import {useSelector} from "react-redux"
import {NotFound} from "./errors/NotFound"
import {ServerError} from "./errors/ServerError"
import {RootState} from "../reducers"

export function ErrorHandler({children}: PropsWithChildren<unknown>): JSX.Element {
  const error = useSelector((state: RootState) => state.httpErrorHandler.error)

  if (!error) {
    return <>{children}</>
  }

  if (!error.response) {
    throw error
  }

  switch (error.response.status) {
    case 404:
      return <NotFound message={error.response.data.toString()}/>
    default:
      return <ServerError/>
  }
}
