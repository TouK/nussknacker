import {cx} from "@emotion/css"
import React, {
  AnchorHTMLAttributes,
  ButtonHTMLAttributes,
  DetailedHTMLProps,
  forwardRef,
  HTMLAttributes,
  InputHTMLAttributes,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from "react"
import {useNkTheme} from "../containers/theme"

export const InputWithFocus = forwardRef(function InputWithFocus(
  {className, ...props}: DetailedHTMLProps<InputHTMLAttributes<HTMLInputElement>, HTMLInputElement>,
  ref: React.Ref<HTMLInputElement>,
): JSX.Element {
  const {withFocus} = useNkTheme()
  return (
    <input ref={ref} {...props} className={cx(withFocus, className)}/>
  )
})

export function TextAreaWithFocus({
  className,
  ...props
}: DetailedHTMLProps<TextareaHTMLAttributes<HTMLTextAreaElement>, HTMLTextAreaElement>): JSX.Element {
  const {withFocus} = useNkTheme()
  return (
    <textarea {...props} className={cx(withFocus, className)}/>
  )
}

export type ButtonProps = DetailedHTMLProps<ButtonHTMLAttributes<HTMLButtonElement>, HTMLButtonElement>

export function ButtonWithFocus({className, onClick, ...props}: ButtonProps): JSX.Element {
  const {withFocus} = useNkTheme()
  return (
    <button
      {...props}
      className={cx(withFocus, className)}
      onClick={event => {
        const {currentTarget} = event
        onClick(event)
        setTimeout(() => currentTarget.scrollIntoView({behavior: "smooth", block: "nearest"}))
      }}
    />
  )
}

export function SelectWithFocus({
  className,
  ...props
}: DetailedHTMLProps<SelectHTMLAttributes<HTMLSelectElement>, HTMLSelectElement>): JSX.Element {
  const {withFocus} = useNkTheme()
  return (
    <select {...props} className={cx(withFocus, className)}/>
  )
}

export function AWithFocus({
  className,
  ...props
}: DetailedHTMLProps<AnchorHTMLAttributes<HTMLAnchorElement>, HTMLAnchorElement>): JSX.Element {
  const {withFocus} = useNkTheme()
  return (
    <a {...props} className={cx(withFocus, className)}/>
  )
}

export const FocusOutline = forwardRef(function FocusOutline({
  className,
  ...props
}: DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>, ref: React.Ref<HTMLDivElement>): JSX.Element {
  const {withFocus} = useNkTheme()
  return (
    <div ref={ref} {...props} className={cx(withFocus, className)}/>
  )
})
