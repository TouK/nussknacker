import {css, cx} from "emotion"
import React, {PropsWithChildren} from "react"
import {useNkTheme} from "../../containers/theme"
import searchIconStyles from "../table/searchIcon.styl"
import {InputProps, ThemedInput} from "./ThemedInput"

export function InputWithIcon({children, ...props}: PropsWithChildren<InputProps>) {
  const {theme} = useNkTheme()
  const styles = css({
    width: theme.spacing.controlHeight,
    height: theme.spacing.controlHeight,
    padding: theme.spacing.controlHeight / 4,
    svg: {
      boxShadow: `0 0 ${theme.spacing.controlHeight / 4}px ${theme.spacing.controlHeight / 8}px ${theme.colors.secondaryBackground}, 0 0 ${theme.spacing.controlHeight / 2}px ${theme.spacing.controlHeight / 2}px ${theme.colors.secondaryBackground} inset`,
    },
  })

  return (
    <div className={cx(children && searchIconStyles.withAddon)}>
      <ThemedInput {...props}/>
      {children && (
        <div className={cx(searchIconStyles.addon, styles)}>{children}</div>
      )}
    </div>
  )
}

