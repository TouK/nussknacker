import {DefaultContent, DefaultContentProps} from "@touk/window-manager"
import {css, cx} from "emotion"
import React, {PropsWithChildren} from "react"
import {useNkTheme} from "../containers/theme"
import {getWindowColors} from "./getWindowColors"

export function WindowContent({classnames, ...props}: PropsWithChildren<DefaultContentProps>): JSX.Element {
  const {theme} = useNkTheme()
  return (
    <DefaultContent
      {...props}
      classnames={{
        header: cx(getWindowColors(props.data.kind)),
        headerButtons: css({fontSize: 15}),
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
