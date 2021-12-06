import React, {PropsWithChildren} from "react"
import {isString} from "lodash"

export function startsWithEmoji(bodyContent: string): boolean {
  return /^(\p{Extended_Pictographic}\p{Emoji}*)/u.test(bodyContent)
}

export function BigEmoji({children}: PropsWithChildren<unknown>): JSX.Element {
  if (isString(children) && startsWithEmoji(children)) {
    return (
      <span style={{fontSize: "3em"}}>{children}</span>
    )
  }

  return (
    <>
      {children}
    </>
  )
}
