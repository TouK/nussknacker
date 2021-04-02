import React, {ChangeEventHandler, ReactNode} from "react"
import {useTranslation} from "react-i18next"
import {TextAreaWithFocus} from "./withFocus"

type Props = {
  onChange: ChangeEventHandler<HTMLTextAreaElement>,
  value: string | string[] | number,
}

const CommentInput = (props: Props): JSX.Element => {
  const {t} = useTranslation()
  return (
    <TextAreaWithFocus
      value={props.value || ""}
      placeholder={t("commentInput.placeholder", "Write a comment...")}
      onChange={props.onChange}
    />
  )
}

export default CommentInput
