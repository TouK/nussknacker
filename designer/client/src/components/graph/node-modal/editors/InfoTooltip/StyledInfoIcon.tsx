import InfoIcon from "@mui/icons-material/Info";
import { styled } from "@mui/material";

export const StyledInfoIcon = styled(InfoIcon)(({ theme }) => ({
    cursor: "pointer",
    width: "1rem",
    height: "1rem",
    backgroundColor: theme.palette.background.paper,
}));
