import React, {PropsWithChildren} from "react"
import "../stylesheets/spinner.styl"
import LoaderSpinner from "./Spinner"

type Props = {
  isReady: boolean,
}

function SpinnerWrapper({isReady, children}: PropsWithChildren<Props>) {
  return isReady ? <>{children}</> : <LoaderSpinner show={true}/>
}

export default SpinnerWrapper

