import { styled } from "@mui/material";

export const ValidationLabel = styled("span")<{ type?: "INFO" | "ERROR" }>(({ theme, type }) => ({
    fontSize: "12px",
    marginTop: "3px",
    color: type === "ERROR" ? theme.custom.colors.error : theme.custom.colors.success,
}));

export const LimitedValidationLabel = styled(ValidationLabel)({
    display: "-webkit-box",
    WebkitLineClamp: 3,
    WebkitBoxOrient: "vertical",
    overflow: "hidden",
});
