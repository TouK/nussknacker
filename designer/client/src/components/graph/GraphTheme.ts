import { styled, css } from "@mui/material";

export const GraphTheme = styled("div")(
    ({ theme }) => css`
        min-height: 300px;
        min-width: 300px;
        color: white; //TODO: Move me to  MUI Theme
        .arrow-marker path {
            fill: white; //TODO: Move me to  MUI Theme
        }

        @media (hover: none) {
            .joint-theme-default .joint-link .link-tool &:hover .connection-wrap {
                display: none;
                .marker-arrowhead {
                    opacity: 0;
                    transform: scale(0.3);
                }
            }
        }

        .joint-theme-default.joint-link {
            .marker-arrowhead {
                /*small element with big stroke allows arrow to overflow link path*/
                transform: scale(0.1);
                stroke-width: 140;
                fill: #33a369; //TODO: Move me to  MUI Theme
                stroke: #2d8e54; //TODO: Move me to  MUI Theme
                transition: all 0.25s ease-in-out;
                &:hover {
                    fill: #7edb0d; //TODO: Move me to  MUI Theme
                    stroke: #2d8e54; //TODO: Move me to  MUI Theme
                }
            }

            .marker-arrowhead-group-source {
                display: none;
            }

            .connection-wrap {
                cursor: pointer;
                stroke: #33a369; //TODO: Move me to  MUI Theme
            }
            &:hover .connection-wrap {
                opacity: 0.5;
                stroke-opacity: 0.5;
            }

            &.dragHovered {
                .connection {
                    opacity: 0.5;
                }
                .connection-wrap {
                    opacity: 0.5;
                    stroke-opacity: 0.5;
                    stroke-width: 24;
                    stroke-linecap: square;
                    stroke: red;
                }
            }

            .connection {
                stroke: white; //TODO: Move me to  MUI Theme
                stroke-width: 2;
            }

            /*simple method to get dragging state*/
            &[style*="pointer"] {
                .connection {
                    stroke-width: 3;
                    stroke-dasharray: 3 0 3;
                }
            }

            .link-tool {
                /*TODO: fix this without css*/
                &[visibility="hidden"] {
                    /*remove hidden tools from size calculations*/
                    display: none;
                }
                .tool-remove {
                    cursor: default;
                    filter: saturate(0.75);
                    transition: all 0.25s ease-in-out;
                    &:hover {
                        filter: saturate(2);
                    }
                }
            }
        }

        .joint-type-esp-model {
            .body .joint-port-body .background {
                transition: all 0.25s ease-in-out;
            }
        }
    `,
);
