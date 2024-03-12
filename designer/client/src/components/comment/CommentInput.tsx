import React, { DetailedHTMLProps, TextareaHTMLAttributes } from "react";
import { useTranslation } from "react-i18next";
import { TextAreaNodeWithFocus } from "../withFocus";
import { lighten } from "@mui/material";

type Props = DetailedHTMLProps<TextareaHTMLAttributes<HTMLTextAreaElement>, HTMLTextAreaElement>;

export const CommentInput = ({ onChange, value, defaultValue, ...props }: Props): JSX.Element => {
    const { t } = useTranslation();
    return (
        <TextAreaNodeWithFocus
            {...props}
            sx={(theme) => ({ background: lighten(theme.palette.background.paper, 0.1) })}
            value={value || ""}
            placeholder={defaultValue?.toString() || t("commentInput.placeholder", "Write a comment...")}
            onChange={onChange}
        />
    );
};

export default CommentInput;
