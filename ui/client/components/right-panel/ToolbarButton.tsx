import Dropzone, {DropEvent} from "react-dropzone"
import React, {ReactEventHandler} from "react"
import cn from "classnames"
import {PanelButtonIcon} from "./PanelButtonIcon"
import styles from "./ToolbarButton.styl"

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
}

export function ToolbarButton({onDrop, title, className, iconClassName, labelClassName, disabled, name, icon, ...props}: Props) {
  const classNames = cn(styles.button, className)
  const buttonProps = {
    ...props,
    title: title || name,
    children: (
      <>
        <PanelButtonIcon className={cn(iconClassName)} icon={icon} title={title}/>
        <div className={cn(labelClassName)}>{name}</div>
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
