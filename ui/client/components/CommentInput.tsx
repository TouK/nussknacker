import React, {ChangeEventHandler, ReactNode} from "react"
import {useTranslation} from "react-i18next"

type Props = {
  onChange: ChangeEventHandler<HTMLTextAreaElement>;
  value: string | string[] | number;
}

const CommentInput = (props: Props): ReactNode => {
  const {t} = useTranslation()
  return (
      <textarea
          value={props.value || ""}
          placeholder={t("commentInput.placeholder", "Write a comment...")}
          onChange={props.onChange}
      />
  )
}

export default CommentInput
