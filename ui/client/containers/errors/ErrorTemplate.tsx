import React, {PropsWithChildren} from "react"

export interface ErrorTemplateProps {
  description?: string,
  message?: string,
}

export function ErrorTemplate({
  description = "",
  message = "",
  children,
}: PropsWithChildren<ErrorTemplateProps>): JSX.Element {
  return (
    <div className="error-template center-block">
      <h1>{`Oops!`}</h1>
      <h2>{message}</h2>
      <div className="error-details">
        <p>{description}</p>
        {children}
      </div>
    </div>
  )
}
