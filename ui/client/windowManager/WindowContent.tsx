import {DefaultContent, DefaultContentProps} from "@touk/window-manager"
import {css, cx} from "emotion"
import React, {PropsWithChildren, useMemo} from "react"
import {useNkTheme} from "../containers/theme"
import {getWindowColors} from "./getWindowColors"
import {LaddaButton} from "./LaddaButton"

export function WindowContent(props: PropsWithChildren<DefaultContentProps>): JSX.Element {
  const {theme} = useNkTheme()
  const classnames = useMemo(()=>({
    header: cx(getWindowColors(props.data.kind)),
    headerButtons: css({fontSize: 15}),
    footer: css({
      justifyContent: "flex-end",
      background: theme.colors.secondaryBackground,
      borderTop: `${theme.spacing.baseUnit / 3}px solid ${theme.colors.borderColor}`,
    }),
    ...props.classnames,
  }), [props.classnames, props.data.kind, theme.colors.borderColor, theme.colors.secondaryBackground, theme.spacing.baseUnit])

  const components = useMemo(() => ({
    FooterButton: LaddaButton,
    ...props.components,
  }), [props.components])

  return (
    <DefaultContent
      {...props}
      components={components}
      classnames={classnames}
    />
  )
}
