import React from "react"
import LoaderSpinner from "./Spinner"

export const SuspenseSpinner: React.FC = ({children}) => (
  <React.Suspense fallback={<LoaderSpinner show={true}/>}>
    {children}
  </React.Suspense>
)
