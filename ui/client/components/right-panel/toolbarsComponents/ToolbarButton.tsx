import Dropzone, {DropEvent} from "react-dropzone"
import React, {ReactEventHandler, useContext} from "react"
import cn from "classnames"
import ToolbarButtonIcon from "./ToolbarButtonIcon"
import styles from "./ToolbarButton.styl"
import {ToolbarButtonsContext} from "./ToolbarButtons"

interface Props {
  name: string,
  icon: string,
  className?: string,
  iconClassName?: string,
  labelClassName?: string,
  disabled?: boolean,
  title?: string,
  onDrop?: <T extends File>(acceptedFiles: T[], rejectedFiles: T[], event: DropEvent) => void,
  onMouseOver?: ReactEventHandler,
  onMouseOut?: ReactEventHandler,
  onClick: ReactEventHandler,
  hasError?: boolean,
  isActive?: boolean,
}

function ToolbarButton({onDrop, title, className, iconClassName, labelClassName, disabled, name, icon, hasError, isActive, ...props}: Props) {
  const {small} = useContext(ToolbarButtonsContext)
  const classNames = cn(
    styles.button,
    hasError && styles.hasError,
    isActive && styles.isActive,
    disabled && styles.disabled,
    small && styles.small,
    className,
  )
  const buttonProps = {
    ...props,
    title: title || name,
    children: (
      <>
        <ToolbarButtonIcon className={cn(styles.icon, iconClassName)} icon={icon} title={title}/>
        <div className={cn(styles.label, labelClassName)}>{name}</div>
      </>
    ),
  }

  if (onDrop) {
    return (
      <Dropzone onDrop={onDrop}>
        {({getRootProps, getInputProps}) => (
          <>
            <div
              {...getRootProps({
                ...buttonProps,
                className: cn([
                  classNames,
                  disabled && styles.disabled,
                ]),
              })}
            />
            <input {...getInputProps()}/>
          </>
        )}
      </Dropzone>
    )
  }

  return (
    <button
      type="button"
      {...buttonProps}
      className={classNames}
      disabled={disabled}
    />
  )
}

export default ToolbarButton
