import {css, cx} from "emotion"
import React, {PropsWithChildren} from "react"

export function ContentSize({children}: PropsWithChildren<unknown>): JSX.Element {
  return (
    <div className={cx("modalContentDark", css({padding: "1.0em 0.5em 1.5em", display: "flex", ">div": {width: 750, flex: 1}}))}>
      {children}
    </div>
  )
}
