import { css, styled } from "@mui/material";
import { FocusableStyled } from "./focusableStyled";

export const dragHovered = "dragHovered";
export const GraphStyled = styled(FocusableStyled)(
    ({ theme }) => css`
        min-height: 300px;
        min-width: 300px;
        color: ${theme.custom.colors.primaryColor};
        .arrow-marker path {
            fill: ${theme.custom.colors.primaryColor};
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
                fill: ${theme.custom.colors.eucalyptus}
                stroke: ${theme.custom.colors.seaGarden}
                transition: all 0.25s ease-in-out;
                &:hover {
                    fill: ${theme.custom.colors.lawnGreen}
                    stroke: ${theme.custom.colors.seaGarden}
                }
            }

            .marker-arrowhead-group-source {
                display: none;
            }

            .connection-wrap {
                cursor: pointer;
                stroke: ${theme.custom.colors.eucalyptus}
            }
            &:hover .connection-wrap {
                opacity: 0.5;
                stroke-opacity: 0.5;
            }

            &.${dragHovered} {
                .connection {
                    opacity: 0.5;
                }
                .connection-wrap {
                    opacity: 0.5;
                    stroke-opacity: 0.5;
                    stroke-width: 24;
                    stroke-linecap: square;
                    stroke: ${theme.custom.colors.red};
                }
            }

            .connection {
                stroke: ${theme.custom.colors.primaryColor};
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
