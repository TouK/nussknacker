import Dropzone, {DropEvent} from "react-dropzone"
import React, {ReactEventHandler} from "react"
import cn from "classnames"
import {PanelButtonIcon} from "./PanelButtonIcon"

interface Props {
  name: string,
  icon: string,
  className?: string,
  disabled?: boolean,
  title?: string,
  onDrop?: <T extends File>(acceptedFiles: T[], rejectedFiles: T[], event: DropEvent) => void,
  onMouseOver?: ReactEventHandler,
  onMouseOut?: ReactEventHandler,
  onClick: ReactEventHandler,
}

export function ButtonWithIcon({onDrop, title, className, disabled, name, icon, ...props}: Props) {
  const classNames = cn("espButton", "right-panel", className)
  const buttonProps = {
    ...props,
    title: title || name,
    children: (
      <>
        <PanelButtonIcon icon={icon} title={title}/>
        <div>{name}</div>
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
                  "dropZone",
                  classNames,
                  disabled && "disabled",
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
