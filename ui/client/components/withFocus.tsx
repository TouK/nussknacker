import cn from "classnames"
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
import styles from "./withFocus.styl"

function inputWithFocus(props: DetailedHTMLProps<InputHTMLAttributes<HTMLInputElement>, HTMLInputElement>, ref) {
  const {className, ...other} = props
  return (
    <input ref={ref} {...other} className={cn(styles.withFocus, className)}/>
  )
}

export const InputWithFocus = forwardRef<HTMLInputElement, DetailedHTMLProps<InputHTMLAttributes<HTMLInputElement>, HTMLInputElement>>(
  inputWithFocus,
)

export function TextAreaWithFocus({className, ...props}: DetailedHTMLProps<TextareaHTMLAttributes<HTMLTextAreaElement>, HTMLTextAreaElement>) {
  return (
    <textarea {...props} className={cn(styles.withFocus, className)}/>
  )
}

export function ButtonWithFocus({className, ...props}: DetailedHTMLProps<ButtonHTMLAttributes<HTMLButtonElement>, HTMLButtonElement>) {
  return (
    <button {...props} className={cn(styles.withFocus, className)}/>
  )
}

export function SelectWithFocus({className, ...props}: DetailedHTMLProps<SelectHTMLAttributes<HTMLSelectElement>, HTMLSelectElement>) {
  return (
    <select {...props} className={cn(styles.withFocus, className)}/>
  )
}

export function AWithFocus({className, ...props}: DetailedHTMLProps<AnchorHTMLAttributes<HTMLAnchorElement>, HTMLAnchorElement>) {
  return (
    <a {...props} className={cn(styles.withFocus, className)}/>
  )
}

export function FocusOutline({className, ...props}: DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement>) {
  return (
    <div {...props} className={cn(styles.withFocus, className)}/>
  )
}
