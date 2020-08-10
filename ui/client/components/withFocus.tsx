import {cx} from "emotion"
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

const inputWithFocus = ({className, ...props}: DetailedHTMLProps<InputHTMLAttributes<HTMLInputElement>, HTMLInputElement>, ref) => {
  const {withFocus} = useNkTheme()
  return (
    <input ref={ref} {...props} className={cx(withFocus, className)}/>
  )
}

export const InputWithFocus = forwardRef(inputWithFocus)

export function TextAreaWithFocus({className, ...props}: DetailedHTMLProps<TextareaHTMLAttributes<HTMLTextAreaElement>, HTMLTextAreaElement>) {
  const {withFocus} = useNkTheme()
  return (
    <textarea {...props} className={cx(withFocus, className)}/>
  )
}

export type ButtonProps = DetailedHTMLProps<ButtonHTMLAttributes<HTMLButtonElement>, HTMLButtonElement>
export function ButtonWithFocus({className, ...props}: ButtonProps) {
  const {withFocus} = useNkTheme()
  return (
    <button {...props} className={cx(withFocus, className)}/>
  )
}

export function SelectWithFocus({className, ...props}: DetailedHTMLProps<SelectHTMLAttributes<HTMLSelectElement>, HTMLSelectElement>) {
  const {withFocus} = useNkTheme()
  return (
    <select {...props} className={cx(withFocus, className)}/>
  )
}

export function AWithFocus({className, ...props}: DetailedHTMLProps<AnchorHTMLAttributes<HTMLAnchorElement>, HTMLAnchorElement>) {
  const {withFocus} = useNkTheme()
  return (
    <a {...props} className={cx(withFocus, className)}/>
  )
}

function focusOutline({className, ...props}: DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>, ref) {
  const {withFocus} = useNkTheme()
  return (
    <div ref={ref} {...props} className={cx(withFocus, className)}/>
  )
}

export const FocusOutline = forwardRef(focusOutline)
