import {css, cx} from "emotion"
import React, {PropsWithChildren, useCallback, useRef} from "react"
import {useNkTheme} from "../../containers/theme"
import {AddonIcon} from "../table/SearchFilter"
import {InputProps, ThemedInput} from "./ThemedInput"

export const ClearIcon = () => {
  const {theme} = useNkTheme()
  const iconStyles = css({
    path: {
      fill: theme.colors.mutedColor,
    },
  })
  return (
    <AddonIcon className={iconStyles} svg="toolbarButtons/delete.svg"/>
  )
}

type Props = PropsWithChildren<InputProps> & {
  onClear?: () => void,
  onAddonClick?: () => void,
}

export function InputWithIcon({children, onAddonClick, onClear, ...props}: Props): JSX.Element {
  const {theme} = useNkTheme()
  const size = theme.spacing.controlHeight

  const wrapperWithAddonStyles = css({
    position: "relative",
  })

  const addonWrapperStyles = css({
    position: "absolute",
    top: 0,
    right: 0,
    height: size,
    display: "flex",
    padding: size / 4,
  })

  const addonStyles = css({
    display: "flex",
    width: size / 2,
    height: size / 2,
    marginLeft: size / 4,
    svg: {
      boxShadow: `0 0 ${size / 4}px ${size / 8}px ${theme.colors.secondaryBackground}, 0 0 ${size / 2}px ${size / 2}px ${theme.colors.secondaryBackground} inset`,
    },
  })

  const ref = useRef<HTMLInputElement>()
  const focus = useCallback(() => ref.current.focus(), [ref])

  return (
    <div className={cx(children && wrapperWithAddonStyles)}>
      <ThemedInput ref={ref} {...props}/>
      <div className={addonWrapperStyles}>
        {!!props.value && onClear && (
          <div className={addonStyles} onClick={onClear}><ClearIcon/></div>
        )}
        {children && (
          <div className={addonStyles} onClick={onAddonClick??focus}>{children}</div>
        )}
      </div>
    </div>
  )
}

