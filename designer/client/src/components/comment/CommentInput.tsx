import { lighten } from "@mui/material";
import type { DetailedHTMLProps, TextareaHTMLAttributes } from "react";
import React from "react";
import { useTranslation } from "react-i18next";

import { TextAreaNode } from "../FormElements";

type Props = DetailedHTMLProps<TextareaHTMLAttributes<HTMLTextAreaElement>, HTMLTextAreaElement>;

export const CommentInput = ({ onChange, value, defaultValue, ...props }: Props): React.JSX.Element => {
    const { t } = useTranslation();
    return (
        <TextAreaNode
            {...props}
            sx={(theme) => ({ background: lighten(theme.palette.background.paper, 0.1), outline: "none" })}
            value={value || ""}
            placeholder={defaultValue?.toString() || t("commentInput.placeholder", "Write a comment...")}
            onChange={onChange}
        />
    );
};

export default CommentInput;
