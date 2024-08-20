import React from "react";
import { MarkdownStyled } from "../components/graph/node-modal/MarkdownStyled";

export const DescriptionView = ({ children }: { children: string }) => {
    return <MarkdownStyled sx={{ marginX: 2, marginBottom: 6 }}>{children}</MarkdownStyled>;
};
