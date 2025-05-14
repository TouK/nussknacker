import InfoIcon from "@mui/icons-material/Info";
import { styled } from "@mui/material";

import { MarkdownStyled } from "../../MarkdownStyled";

export const StyledInfo = styled(InfoIcon)(() => ({
    cursor: "pointer",
    width: "1rem",
    height: "1rem",
}));

export const StyledInfoChildrenWrapper = styled("span")(() => ({
    display: "inline-flex",
    height: "fit-content",
}));

StyledInfoChildrenWrapper.defaultProps = {
    // disable svg <title> behavior
    title: "",
};

export const StyledInfoMarkdown = styled(MarkdownStyled)(({ theme }) => ({
    fontSize: "0.75rem",
    marginTop: theme.spacing(1),
    marginBottom: theme.spacing(1),
    "> p": { marginTop: theme.spacing(1), marginBottom: theme.spacing(1) },
}));
