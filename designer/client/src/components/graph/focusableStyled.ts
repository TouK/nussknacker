import { CSSProperties } from "react";
import { styled, css, Theme } from "@mui/material";
import { alpha } from "../../containers/theme/helpers";
import { ButtonWithFocus } from "../withFocus";

export const buttonBase = (theme: Theme) => css`
    border: 1px solid ${theme.custom.colors.doveGray};
    border-radius: 0;
    background-color: ${theme.custom.colors.primaryBackground};
    color: ${theme.custom.colors.secondaryColor};
    transition: background-color 0.2s;
    user-select: none;
    &:disabled,
    &.disabled {
        opacity: 0.3;
        cursor: not-allowed !important;
    }

    &:not(:disabled):hover,
    &:not(.disabled):hover {
        background-color: ${theme.custom.colors.charcoal};
    }
`;

export const StyledButtonWithFocus = styled(ButtonWithFocus)(
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
        stroke-width: 2px;
    }
    .background {
        fill: ${backgroundFill};
    }
    .joint-port-body {
        stroke-width: 3px;
        fill: ${backgroundFill};
        stroke: ${strokeColor};
    }
`;

export const FocusableStyled = styled("div")(
    ({ theme, id }) =>
        css`
            width: 100% !important;
            height: 100% !important;
          
            .node-validation-error {
                ${nodeHighlight(theme.custom.colors.error, theme.custom.colors.cinderella)}
            }

            .node-focused-with-validation-error {
                ${nodeHighlight(theme.custom.colors.cobalt, theme.custom.colors.bizarre)}
            }

            .node-grouping {
                ${nodeHighlight(theme.custom.colors.apple, theme.custom.colors.blueRomance)}
            }

            .node-focused {
                ${nodeHighlight(theme.custom.colors.cobalt, theme.custom.colors.zumthor)}
            }

            .joint-type-basic-rect.node-focused {
                & rect {
                    stroke-width: 2;
                    opacity: 0.2;
                }
            }

            .modalContentDark {
                ${modalContent(theme.custom.colors.error, theme.custom.colors.boulder)}
            }

            .error {
                background-color: ${theme.custom.colors.yellowOrange};
            }

            .element {
                cursor: pointer;
                &:active {
                    cursor: -moz-grabbing;
                    cursor: -webkit-grabbing;
                    cursor: grabbing;
                }
            }

            .link {
                .connection-wrap {
                    &:hover {
                        stroke: transparent;
                        stroke-width: 10px;
                        stroke-linecap: initial;
                    }
                }

                &:hover {
                    .connection {
                        stroke: ${theme.custom.colors.scooter};
                    }
                    .marker-target,
                    .marker-source {
                        fill: ${theme.custom.colors.scooter}
                    }
                    .marker-vertices circle {
                        fill: ${theme.custom.colors.nobel}
                        r: 6px;
                    }
                }
            }

            .row-ace-editor {
                color: ${theme.custom.colors.dimGray};
                padding-top: 8px;
                padding-bottom: 8px;
                padding-left: 5px;
                padding-right: 5px;
                background-color: ${theme.custom.colors.secondaryBackground};
                min-height: 35px;
                outline: 1px solid ${alpha(theme.custom.colors.primaryColor, 0.075)};
                &.focused {
                    outline: 2px solid ${theme.custom.colors.cobalt} !important;
                    outline-offset: -1px !important;
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
                .node-value {
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
                background-color: ${theme.custom.colors.secondaryBackground};
                color: ${theme.custom.colors.secondaryColor};
                font-weight: 400;
                font-size: 16px;
            }

            .drop-down-menu-placeholder {
                height: 100px;
            }

          .joint-paper-background {
            overflow-y: auto;
            overflow-x: auto;
            background-color: ${theme.custom.colors.canvasBackground};
          }
          #svg-pan-zoom-controls {
            transform: translate(0, 0px) scale(0.75);
          }
          & svg {
            width: 100%;
            height: 100%;
          }
          
          ${
              id === "nk-graph-main" &&
              `height: 100% !important;
                `
          }
          ${
              id === "nk-graph-fragment" &&
              `width: 100% !important;
                #svg-pan-zoom-controls {
                    transform: translate(0, 0px) scale(0.5);
                }`
          }
        `,
);
