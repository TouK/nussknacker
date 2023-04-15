import {css, cx} from "@emotion/css"
import React, {forwardRef, Ref} from "react"
import {useNkTheme} from "../../containers/theme"
import {bootstrapStyles} from "../../styles"
import {ValueFieldProps} from "../valueField"
import {InputWithFocus} from "../withFocus"

export type InputProps = ValueFieldProps<string> & {
  placeholder?: string,
  className?: string,
}

export const ThemedInput = forwardRef(function ThemedInput({value, onChange, placeholder, className}: InputProps, ref: Ref<HTMLInputElement>): JSX.Element {
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
      ref={ref}
      type="text"
      placeholder={placeholder}
      className={cx(bootstrapStyles.formControl, styles, className)}
      value={value || ""}
      onChange={e => onChange(`${e.target.value}`)}
    />
  )
})
