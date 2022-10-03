import React, {ChangeEventHandler, DetailedHTMLProps, ReactNode, TextareaHTMLAttributes} from "react"
import {useTranslation} from "react-i18next"
import {TextAreaWithFocus} from "./withFocus"

type Props = DetailedHTMLProps<TextareaHTMLAttributes<HTMLTextAreaElement>, HTMLTextAreaElement>

export const CommentInput = ({onChange, value, defaultValue, ...props}: Props): JSX.Element => {
  const {t} = useTranslation()
  return (
    <TextAreaWithFocus
      {...props}
      value={value || ""}
      placeholder={defaultValue?.toString() || t("commentInput.placeholder", "Write a comment...")}
      onChange={onChange}
    />
  )
}

export default CommentInput
