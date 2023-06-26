import styled from "@emotion/styled";
import { css } from "@emotion/css";
import { Theme } from "@emotion/react";

export const lineLimitStyle = css({
    display: "-webkit-box",
    WebkitLineClamp: 3,
    WebkitBoxOrient: "vertical",
    overflow: "hidden",
});

export enum ValidationLabelType {
    INFO = "INFO",
    ERROR = "ERROR",
}

export const ValidationLabel = styled.span((props: { type: ValidationLabelType; theme?: Theme }) => ({
    fontSize: "12px",
    marginTop: "3px",
    color: props.type == ValidationLabelType.ERROR ? `${props.theme.colors.error} !important` : `green !important`,
}));
