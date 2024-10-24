import { styled } from "@mui/material";
import UrlIcon from "../../../UrlIcon";

export const StyledActionIcon = styled(UrlIcon)(({ theme }) => ({
    width: "1.25rem",
    height: "1.25rem",
    marginLeft: "auto",
    cursor: "pointer",
    color: theme.palette.text.secondary,
}));
