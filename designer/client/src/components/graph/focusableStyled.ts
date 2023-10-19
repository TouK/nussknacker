import { CSSProperties } from "react";
import { styled, css, Theme } from "@mui/material";
import { alpha } from "../containers/theme/helpers";

const baseEspGraph = (theme: Theme) => css`
    overflow-y: auto;
    overflow-x: auto;
    background-color: ${theme.custom.colors.canvasBackground};
    #svg-pan-zoom-controls {
        transform: translate(0, 0px) scale(0.75);
    }
    & svg {
        width: 100%;
        height: 100%;
    }
`;

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
    ({ theme }) =>
        css`
            width: 100% !important;
            height: 100% !important;

            #nk-graph-main {
                height: 100% !important;
                ${baseEspGraph(theme)};
            }

            #nk-graph-fragment {
                width: 100% !important;
                ${baseEspGraph(theme)}
                #svg-pan-zoom-controls {
                    transform: translate(0, 0px) scale(0.5);
                }
            }

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

            .espModal {
                max-height: fit-content;
                max-height: -moz-max-content;
                height: -moz-max-content;
                height: -webkit-max-content;
                max-width: 800px;
                width: 800px;
                position: relative;
                background-color: ${theme.custom.colors.charcoal};
                outline: none;
                border-radius: 0;
                padding: 0;
                border: 2px solid ${theme.custom.colors.nero}
                box-shadow: 0 0 8px 0 ${alpha(theme.custom.colors.borderColor, 0.2)};
            }

            .draggable-container {
                position: absolute;
                top: 0;
                bottom: 0;
                left: 0;
                right: 0;
                display: flex;
                justify-content: center;
            }

            .modalContentDark {
                ${modalContent(theme.custom.colors.error, theme.custom.colors.boulder)}
            }

            .modalFooter {
                margin-top: 5px;
                border-top: 2px solid ${theme.custom.colors.nero}; 
                height: 51px;
                background-color: ${theme.custom.colors.blackMarlin};
                .footerButtons {
                    text-align: right;
                    & button {
                        margin-right: 20px;
                        text-transform: uppercase;
                    }

                    .modalConfirmButton {
                        color: ${theme.custom.colors.ok}
                    }
                }
            }

            .esp-button-error {
                &.right-panel {
                    border-color: ${theme.custom.colors.error};
                }
                &.add-comment {
                    border-color: ${theme.custom.colors.error}
                }
                &.download-button {
                    border-color: ${theme.custom.colors.error}
                }
                &.attachment-button {
                    border-color: ${theme.custom.colors.error}
                }
                :first-child {
                    & svg {
                        & g {
                            .a {
                                fill:${theme.custom.colors.error}
                            }
                        }
                    }
                }

                :nth-of-type(2) {
                    color: ${theme.custom.colors.error}
                }
            }

            body .modalButton {
                ${buttonBase(theme)};
                width: 120px;
                height: 30px;
                font-size: 18px;
                margin-top: 10px;
                font-weight: 600;
                margin-left: 10px;
                &:disabled {
                    background:${theme.custom.colors.secondaryBackground}
                }
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

                .esp-label {
                    & rect {
                        transform: translateY(0.1em) scale(1.2, 1.4);
                        &:hover {
                            cursor: zoom-in;
                            display: table;
                            & rect {
                                transform: translateY(0.8em) scale(2.5, 3.5);
                            }

                            & text {
                                font-size: 16px;
                                display: table-cell;
                            }
                        }
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

            .code-completion {
                color: ${theme.custom.colors.dimGray};
                position: relative;
                top: -25px;
                float: right;
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
        `,
);

export const BranchParameterRowStyled = styled("div")(
    ({ theme }) => `
    margin-top: 0;
    margin-bottom: 0;
    display: flex;
    .branch-param-label {
        color:  ${theme.custom.colors.secondaryColor};
        font-weight: 400;
        font-size: 14px;
        padding: 8px 10px 8px 10px;
        width: 30%;
    }
    .branch-parameter-expr-container {
        width: 100%;
    }
`,
);
