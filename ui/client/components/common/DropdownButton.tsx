/* eslint-disable i18next/no-literal-string */
import React, {CSSProperties, PropsWithChildren, useCallback, useState} from "react"
import {createPortal} from "react-dom"

import Select from "react-select"
import styles from "../../stylesheets/select.styl"
import {ButtonProps, ButtonWithFocus} from "../withFocus"

const forceHideStyles: CSSProperties = {
  height: "0 !important",
  minHeight: "0 !important",
  maxHeight: "0 !important",
  overflow: "hidden",
  margin: 0,
  padding: 0,
}

const styleOverride = {
  container: () => forceHideStyles,
  control: () => forceHideStyles,
  menuPortal: ({width, ...base}) => ({...base, zIndex: 1000}),
  menu: ({position, ...base}) => ({...base}),
}

interface Option<T> {
  label: string,
  value: T,
}

interface Props<T = any> {
  options: Option<T>[],
  onRangeSelect: (value: T) => void,
  wrapperStyle?: CSSProperties,
}

export function DropdownButton<T = any>(props: PropsWithChildren<ButtonProps & Props<T>>): JSX.Element {
  const [isOpen, setIsOpen] = useState<boolean>()
  const {options, onRangeSelect: onSelect, children, onClick, wrapperStyle, ...buttonProps} = props

  const toggleOpen = useCallback((e) => {
    setIsOpen(state => !state)
    onClick?.(e)
  }, [onClick])

  const onSelectChange = useCallback(({value}: Option<T>) => {
    setIsOpen(false)
    onSelect(value)
  }, [toggleOpen, onSelect])

  return (
    <Dropdown
      isOpen={isOpen}
      onClose={toggleOpen}
      style={wrapperStyle}
      target={(
        <ButtonWithFocus type="button" {...buttonProps} onClick={toggleOpen}>
          {children}
        </ButtonWithFocus>
      )}
    >
      <Select
        autoFocus
        classNamePrefix={styles.nodeValueSelect}
        backspaceRemovesValue={false}
        components={{IndicatorsContainer: EmptyContainer}}
        controlShouldRenderValue={false}
        isClearable={false}
        menuPortalTarget={document.body}
        menuIsOpen
        onChange={onSelectChange}
        options={options}
        styles={styleOverride}
        tabSelectsValue={false}
      />
    </Dropdown>
  )
}

const Menu = (props: PropsWithChildren<unknown>) => (
  <div
    style={{
      position: "absolute",
      left: "1em",
      bottom: "1em",
    }}
    {...props}
  />
)

const Blanket = ({onClick}: {onClick: () => void}) => createPortal((
  <div
    style={{
      position: "fixed",
      bottom: 0,
      left: 0,
      top: 0,
      right: 0,
      zIndex: 999,
    }}
    onClick={onClick}
  />
), document.body)

const Dropdown = ({children, isOpen, target, onClose, style}) => (
  <span style={{...style, position: "relative"}}>
    <span style={{position: "relative", width: "100%", height: "100%", display: "flex"}}>
      {target}
      {isOpen ? <Menu>{children}</Menu> : null}
      {isOpen ? <Blanket onClick={onClose}/> : null}
    </span>
  </span>
)

const EmptyContainer = () => <></>
