import { CSSProperties } from "react";
import { styled, css, Theme } from "@mui/material";
import { Button } from "../FormElements";
import { blend } from "@mui/system";
import { blendLighten } from "../../containers/theme/helpers";
import { nodeValue } from "./node-modal/NodeDetailsContent/NodeTableStyled";

export const buttonBase = (theme: Theme) => css`
    border-radius: 0;
    background-color: ${theme.palette.background.paper};
    transition: background-color 0.2s;
    user-select: none;
    &:focus {
        border: 1px solid ${theme.palette.primary.main};
    }
    &:disabled,
    &.disabled {
        opacity: 0.3;
        cursor: not-allowed !important;
    }

    &:not(:disabled):hover,
    &:not(.disabled):hover {
        background-color: ${theme.palette.action.hover};
    }
`;

export const StyledButton = styled(Button)(
    ({ theme }) => css`
        ${buttonBase(theme)};
        width: 35px;
        height: 35px;
        font-weight: bold;
        font-size: 20px;
    `,
);

const modalContent = (errorColor: CSSProperties["color"], hrColor: CSSProperties["color"]) => css`
    overflow: auto;
    clear: both;
    .warning {
        margin: 15px;
        color: ${errorColor};
        .icon {
            float: left;
            width: 30px;
            height: 30px;
        }
    }

    hr {
        border-top: 1px solid ${hrColor};
        margin-top: 10px;
        margin-bottom: 10px;
    }

    &.edge-details {
        height: 270px;
    }
`;

const nodeHighlight = (strokeColor: CSSProperties["color"], backgroundFill: CSSProperties["color"]) => css`
    .body {
        stroke: ${strokeColor};
        stroke-width: 1px;
    }
    .background {
        fill: ${backgroundFill};
    }
    .joint-port-body {
        stroke-width: 1px;
        fill: ${backgroundFill};
        stroke: ${strokeColor};
    }
`;

export const nodeValidationError = "node-validation-error";
export const nodeFound = "node-found";
export const nodeFoundHover = "node-found-hover";
export const nodeFocused = "node-focused";

const nodeStyles = (theme: Theme) => {
    return css`
        .joint-type-esp-model {
            &,
            .body,
            .background,
            .joint-port-body {
                stroke-width: 0.5;
                transition: filter 0.5s, fill 0.25s, stroke 0.25s;
            }
        }

        .${nodeValidationError} {
            ${nodeHighlight(
                theme.palette.error.main,
                blend(blendLighten(theme.palette.background.paper, 0.04), theme.palette.error.main, 0.3),
            )}
        }

        .${nodeFocused} {
            ${nodeHighlight(
                theme.palette.secondary.light,
                blend(blendLighten(theme.palette.background.paper, 0.04), theme.palette.secondary.light, 0.15),
            )}
        }

        .${nodeFocused}.${nodeValidationError} {
            ${nodeHighlight(
                theme.palette.secondary.light,
                blend(blendLighten(theme.palette.background.paper, 0.04), theme.palette.error.main, 0.3),
            )}
        }
        .${nodeFound} {
            ${nodeHighlight(
                theme.palette.success.main,
                blend(blendLighten(theme.palette.background.paper, 0.04), theme.palette.success.main, 0.3),
            )}
        }
        .${nodeFocused}.${nodeFound} {
            ${nodeHighlight(
                theme.palette.secondary.light,
                blend(blendLighten(theme.palette.background.paper, 0.04), theme.palette.success.main, 0.3),
            )}
        }
        .${nodeFoundHover}.${nodeFound} {
            ${nodeHighlight(
                theme.palette.success.dark,
                blend(blendLighten(theme.palette.background.paper, 0.04), theme.palette.success.dark, 0.2),
            )}
        }
        .${nodeFocused}.${nodeFoundHover}.${nodeFound} {
            ${nodeHighlight(
                theme.palette.secondary.light,
                blend(blendLighten(theme.palette.background.paper, 0.04), theme.palette.success.dark, 0.2),
            )}
        }
    `;
};

export const FocusableStyled = styled("div")(
    ({ theme, id }) =>
        css`
            width: 100% !important;
            height: 100% !important;

            ${nodeStyles(theme)}

            .modalContentDark {
                ${modalContent(theme.palette.error.main, blendLighten(theme.palette.background.paper, 0.25))}
            }
            .element {
                cursor: pointer;
                &:active {
                    cursor: -moz-grabbing;
                    cursor: -webkit-grabbing;
                    cursor: grabbing;
                }
            }

            .testResultsSummary {
                font-size: 13px;
                font-weight: bold;
            }

            .nodeIcon {
                opacity: 0.75;

                &:hover {
                    opacity: 1;
                }

                .joint-type-esp-group & .joint-type-basic-rect & {
                    display: none;
                }

                .forced-hover & .joint-type-esp-group:hover & {
                    display: block;
                }
            }

            .branch-parameter-expr {
                display: inline-flex;
                width: 100%;

                .${nodeValue} {
                    width: 100% !important;
                }
            }

            .branch-parameter-expr-value {
                width: 100%;
                display: inline-block;
            }

            .branch-param-select {
                width: 100%;
                padding: 0 10px;
                border: none;
                background-color: ${theme.palette.background.paper};
                color: ${theme.palette.text.secondary};
                font-weight: 400;
                font-size: 16px;
            }

            .drop-down-menu-placeholder {
                height: 100px;
            }

            .joint-paper-background {
                overflow-y: auto;
                overflow-x: auto;
                background-color: ${theme.palette.background.default};
            }

            & svg {
                width: 100%;
                height: 100%;
            }

            ${id === "nk-graph-main" &&
            `height: 100% !important;
                `}
            ${id === "nk-graph-fragment" &&
            `width: 100% !important;
                #svg-pan-zoom-controls {
                    transform: translate(0, 0px) scale(0.5);
                }`}
        `,
);
