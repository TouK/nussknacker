import { PropsOf } from "@emotion/react";
import Markdown from "react-markdown";
import remarkDirective from "remark-directive";
import remarkDirectiveRehype from "remark-directive-rehype";
import React from "react";
import { styled } from "@mui/material";
import { getBorderColor } from "../../../containers/theme/helpers";

const MarkdownWithPlugins = ({ remarkPlugins = [], children, ...props }: PropsOf<typeof Markdown>) => (
    <Markdown remarkPlugins={[remarkDirective, remarkDirectiveRehype, ...remarkPlugins]} {...props}>
        {children}
    </Markdown>
);

export const MarkdownStyled = styled(MarkdownWithPlugins)(({ theme }) => ({
    ...theme.typography.body2,
    marginTop: theme.spacing(1.5),
    marginBottom: theme.spacing(1.25),
    table: {
        backgroundColor: theme.palette.background.paper,
        marginTop: theme.spacing(0.5),
        marginBottom: theme.spacing(0.5),
        width: "95%",
    },
    "th, td": {
        padding: theme.spacing(1.25),
        border: `1px solid ${getBorderColor(theme)}`,
        fontSize: "12px",
    },
    a: {
        color: `${theme.palette.primary.main} !important`,
    },
}));

export type MarkdownStyledProps = PropsOf<typeof MarkdownStyled>;
