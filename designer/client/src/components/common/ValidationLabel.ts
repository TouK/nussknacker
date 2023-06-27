import styled from "@emotion/styled";

export const ValidationLabel = styled.span<{ type?: "INFO" | "ERROR" }>(({ theme, type }) => ({
    fontSize: "12px",
    marginTop: "3px",
    color: type === "ERROR" ? `${theme.colors?.error} !important` : `green !important`,
}));

export const LimitedValidationLabel = styled(ValidationLabel)({
    display: "-webkit-box",
    WebkitLineClamp: 3,
    WebkitBoxOrient: "vertical",
    overflow: "hidden",
});
