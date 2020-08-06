import {css, cx} from "emotion"
import React from "react"
import {useNkTheme} from "../../containers/theme"
import bootstrapStyles from "../../stylesheets/bootstrap.styl"
import {InputWithFocus} from "../withFocus"
import {ValueFieldProps} from "../valueField"

export type InputProps = ValueFieldProps<string> & {
  placeholder?: string,
  className?: string,
}

export function ThemedInput({value, onChange, placeholder, className}: InputProps) {
  const {theme} = useNkTheme()
  const styles = css({
    height: theme?.spacing?.controlHeight,
    borderRadius: theme?.borderRadius,
    color: theme?.colors?.primaryColor,
    borderColor: theme.colors.borderColor,
    backgroundColor: theme.colors.secondaryBackground,
  })

  return (
    <InputWithFocus
      type="text"
      placeholder={placeholder}
      className={cx(bootstrapStyles.formControl, styles, className)}
      value={value || ""}
      onChange={e => onChange(`${e.target.value}`)}
    />
  )
}
