import { CSSProperties } from "react";
import { styled, css } from "@mui/material";

const baseEspGraph = () => css`
    overflow-y: auto;
    overflow-x: auto;
    background-color: #b3b3b3; //TODO: Change me to MUI value
    #svg-pan-zoom-controls {
        transform: translate(0, 0px) scale(0.75);
    }
    & svg {
        width: 100%;
        height: 100%;
    }
`;

const espButtonBase = css`
    width: unit(72, px); //TODO: Change me to MUI value
    height: unit(72, px); //TODO: Change me to MUI value
    font-size: 11px; //TODO: Change me to MUI value
    margin: 10px 22px 10px 0; //TODO: change 22px to MUI value
    img {
        display: block;
        margin: auto;
    }

    svg {
        width: 40px;
        display: block;
        margin: auto;
    }

    &.dropzone {
        padding-top: 4px; /*why?*/
        display: inline-block;
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

export const StyledGraphWrapper = styled("div")(
    css`
        width: 100% !important;
        height: 100% !important;

        #nk-graph-main {
            height: 100% !important;
            ${baseEspGraph()};
        }

        #nk-graph-fragment {
            width: 100% !important;
            ${baseEspGraph()}
            #svg-pan-zoom-controls {
                transform: translate(0, 0px) scale(0.5);
            }
        }

        .node-validation-error {
            ${nodeHighlight("#f25c6e", "#fbd2d6")}//TODO: change me to MUI value
        }

        .node-focused-with-validation-error {
            ${nodeHighlight("#0058a9", "#f2dede")}//TODO: change me to MUI value
        }

        .node-grouping {
            ${nodeHighlight("#5ba935", "#caf2d6")}//TODO: change me to MUI value
        }

        .node-focused {
            ${nodeHighlight("#0058a9", "#e6ecff")}//TODO: change me to MUI value
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
            max-width: 800px; //TODO: change me to MUI value
            width: 800px; //TODO: change me to MUI value
            position: relative;
            background-color: #444444; //TODO: change me to MUI value
            outline: none;
            border-radius: 0;
            padding: 0;
            border: 2px solid #222; //TODO: change me to MUI value
            box-shadow: 0 0 8px 0 rgba(0, 0, 0, 0.2);
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
            ${modalContent("#f25c6e", "#777")}//TODO: change me to MUI variables
        }

        .modalFooter {
            margin-top: 5px;
            border-top: 2px solid #222; //TODO: change me to MUI variables
            height: 51px; //TODO: change me to MUI variables
            background-color: #3a3a3a; //TODO: change me to MUI variables
            .footerButtons {
                text-align: right;
                & button {
                    margin-right: 20px;
                    text-transform: uppercase;
                }

                .modalConfirmButton {
                    color: #8fad60; //TODO: change me to MUI variables
                }
            }
        }

        .esp-button-error {
            &.right-panel {
                border-color: #f25c6e; //TODO: change me to MUI variables
            }
            &.add-comment {
                border-color: #f25c6e; //TODO: change me to MUI variables
            }
            &.download-button {
                border-color: #f25c6e; //TODO: change me to MUI variables
            }
            &.attachment-button {
                border-color: #f25c6e; //TODO: change me to MUI variables
            }
            :first-child {
                & svg {
                    & g {
                        .a {
                            fill: #f25c6e; //TODO: change me to MUI variables
                        }
                    }
                }
            }

            :nth-of-type(2) {
                color: #f25c6e; //TODO: change me to MUI variables
            }
        }

        body .modalButton {
            ${espButtonBase};
            width: 120px;
            height: 30px;
            font-size: 18px;
            margin-top: 10px;
            font-weight: 600;
            margin-left: 10px;
            &:disabled {
                background: #333333; //TODO: change me to MUI variables
            }
        }

        .error {
            background-color: #fbb03b;
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
                    stroke: #46bfdb;
                }
                .marker-target,
                .marker-source {
                    fill: #46bfdb;
                }
                .marker-vertices circle {
                    fill: #b5b5b5;
                    r: 6px;
                }
            }
        }

        .code-completion {
            color: #686868;
            position: relative;
            top: -25px;
            float: right;
        }

        .row-ace-editor {
            color: #686868;
            padding-top: 8px;
            padding-bottom: 8px;
            padding-left: 5px;
            padding-right: 5px;
            background-color: #333;
            min-height: 35px;
            outline: 1px solid rgba(255, 255, 255, 0.075);
            &.focused {
                outline: 2px solid #0058a9 !important;
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

        .fadeout {
            position: relative;
            bottom: 4em;
            height: 4em;
            background: -webkit-linear-gradient(rgba(20, 20, 20, 0) 0%, rgba(20, 20, 20, 1) 100%);
            background-image: -moz-linear-gradient(rgba(20, 20, 20, 0) 0%, rgba(20, 20, 20, 1) 100%);
            background-image: -o-linear-gradient(rgba(20, 20, 20, 0) 0%, rgba(20, 20, 20, 1) 100%);
            background-image: linear-gradient(rgba(20, 20, 20, 0) 0%, rgba(20, 20, 20, 1) 100%);
            background-image: -ms-linear-gradient(rgba(20, 20, 20, 0) 0%, rgba(20, 20, 20, 1) 100%);
        }

        .marked {
            border: 2px solid #8fad60 !important; // TODO: change me to MUI color
        }

        .fieldsControl {
            .addRemoveButton {
                ${espButtonBase};
                width: 35px;
                height: 35px;
                font-weight: bold;
                font-size: 20px;
            }

            .fieldName {
                width: 28%;
            }
            .handle-bars {
                height: 35px;
                width: 12px;
                margin-left: 6px;
                cursor: grab;
            }

            .node-value {
                &.fieldName {
                    flex-basis: 30%;
                    max-width: 20em;
                }

                &.fieldRemove {
                    flex: 0;
                }
            }
        }

        .branch-parameter-row {
            margin-top: 0;
            margin-bottom: 0;
            display: flex;
            & .branch-param-label {
                color: #ccc;
                font-weight: 400;
                font-size: 14px;
                padding: 8px 10px 8px 10px;
                width: 30%;
            }
        }

        .branch-parameter-expr-container {
            width: 100%;
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
            background-color: #333;
            color: #ccc;
            font-weight: 400;
            font-size: 16px;
        }

        .drop-down-menu-placeholder {
            height: 100px;
        }
    `,
);
