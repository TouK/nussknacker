import React from "react"
import {CustomTabPage} from "./CustomTabPage"
import {Navigate, useLocation, useParams} from "react-router-dom"

// redirect passing all params
export function StarRedirect({to, push}: { to: string, push?: boolean }) {
  const {"*": star = ""} = useParams<{ "*": string }>()
  const {hash, search} = useLocation()
  const rest = `${star}${hash}${search}`
  return <Navigate to={rest.length ? `${to}/${rest}` : to} replace={!push}/>
}

export function CustomTab(): JSX.Element {
  const {id} = useParams<{ id: string }>()
  return <CustomTabPage id={id}/>
}
