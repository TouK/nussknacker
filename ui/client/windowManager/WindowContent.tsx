import {DefaultContent, DefaultContentProps} from "@touk/window-manager"
import {css} from "emotion"
import React, {PropsWithChildren} from "react"
import {useNkTheme} from "../containers/theme"
import {getWindowColors} from "./getWindowColors"

export function WindowContent({classnames, ...props}: PropsWithChildren<DefaultContentProps>): JSX.Element {
  const {theme} = useNkTheme()
  return (
    <DefaultContent
      {...props}
      classnames={{
        header: getWindowColors(props.data.kind),
        footer: css({
          justifyContent: "flex-end",
          background: theme.colors.secondaryBackground,
          borderTop: `${theme.spacing.baseUnit / 3}px solid ${theme.colors.borderColor}`,
        }),
        ...classnames,
      }}
    />
  )
}
