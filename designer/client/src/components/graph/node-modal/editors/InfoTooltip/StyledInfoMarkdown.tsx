import { styled } from "@mui/material";

import { MarkdownStyled } from "../../MarkdownStyled";

const StyledInfoMarkdown = styled(MarkdownStyled)(({ theme }) => ({
    fontSize: "0.75rem",
    marginTop: theme.spacing(1),
    marginBottom: theme.spacing(1),
    "> p": { marginTop: theme.spacing(1), marginBottom: theme.spacing(1) },
}));

export default StyledInfoMarkdown;
