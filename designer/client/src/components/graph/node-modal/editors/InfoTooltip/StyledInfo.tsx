import InfoIcon from "@mui/icons-material/Info";
import { styled } from "@mui/material";

export const StyledInfo = styled(InfoIcon)(() => ({
    cursor: "pointer",
    width: "1rem",
    height: "1rem",
}));

export const StyledInfoChildrenWrapper = styled("span")(() => ({
    display: "inherit",
    height: "fit-content",
}));

StyledInfoChildrenWrapper.defaultProps = {
    // disable svg <title> behavior
    title: "",
};
