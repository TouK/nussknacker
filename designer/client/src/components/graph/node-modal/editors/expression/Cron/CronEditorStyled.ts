import { css, styled } from "@mui/material";
import { alpha } from "../../../../../../containers/theme/helpers";
import { blendLighten } from "../../../../../../containers/theme/nuTheme";

export const CronEditorStyled = styled("div")(
    ({ theme }) => css`
        width: 100%;
        .cron_builder_bordering {
            border-radius: none;
            border: none;
            padding: 8px 0 0 0;
            text-align: left;
            background: ${theme.palette.background.paper};
        }
        .container-fluid {
            padding: 0;
        }
        .cron_builder {
            width: 100%;
            background-color: ${theme.palette.background.paper};
            outline: 1px solid ${alpha(theme.custom.colors.primaryColor, 0.075)};
            border: none;
            * {
                color: ${theme.custom.colors.secondaryColor};
            }
        }
        .well {
            display: flex;
            align-items: center;
            background-color: ${theme.palette.background.paper} !important;
            border: 1px solid ${blendLighten(theme.palette.background.paper, 0.25)} !important;
            margin-bottom: 8px !important;
            border-radius: 0 !important;
            padding: 12px !important;
        }
        .row {
            margin-left: auto !important;
            margin-right: auto !important;
        }
        .nav {
            padding-left: 0;
            margin-bottom: 0;
            list-style: none;
            display: flex;
            margin-top: 0;
            column-gap: 2px;
            li {
                flex: 1 !important;
            }
            > li {
                position: relative;
                display: block;
                > a {
                    position: relative;
                    display: block;
                    padding: 10px 15px;
                    &:hover,
                    &:focus {
                        text-decoration: none;
                    }
                }

                // Disabled state sets text to gray and nukes hover/tab effects
                &.disabled > a {
                    color: ${theme.palette.action.disabled};

                    &:hover,
                    &:focus {
                        color: ${theme.palette.action.disabled};
                        text-decoration: none;
                        cursor: not-allowed;
                        background-color: transparent;
                    }
                }
            }
        }
        .cron_builder_bordering input {
            border-radius: 0;
            background: ${theme.palette.background.paper};
        }
        .cron_builder_bordering input[type="radio"] {
            margin-top: 2px;
            vertical-align: text-top;
            accent-color: ${theme.palette.primary.main};
            margin-right: ${theme.spacing(1)};
        }

        .cron_builder_bordering input[type="checkbox"] {
            accent-color: ${theme.palette.primary.main};
            margin-right: ${theme.spacing(1)};
        }
        .cron_builder_bordering input[type="number"] {
            height: 26px;
            border: 1px solid ${blendLighten(theme.palette.background.paper, 0.25)};
            width: 12% !important;
        }

        .cron_builder_bordering input {
            border: 1px solid ${blendLighten(theme.palette.background.paper, 0.25)};
        }
        .span6 {
            width: 50%;
            align-self: flex-start;
            padding: 0;
        }
        input[type="radio"],
        input[type="checkbox"] {
            vertical-align: bottom;
            margin: 8px 0 0 0;
            transform: scale(1.2);
            -moz-appearance: none;
        }
        .nav-tabs > li > a {
            border-radius: 0;
            border-right: 1px solid ${blendLighten(theme.palette.background.paper, 0.25)};
            border-left: 1px solid ${blendLighten(theme.palette.background.paper, 0.25)};
            border-top: 1px solid ${blendLighten(theme.palette.background.paper, 0.25)};
            border-bottom: 0;
            color: ${theme.palette.text.secondary} !important;
        }
        .nav-tabs > li.active > a,
        .nav-tabs > li.active > a:hover,
        .nav-tabs > li.active > a:focus,
        .nav-tabs > li > a:hover,
        .cron_builder .nav-tabs > li.active > a,
        .nav-tabs > li.active > a:hover,
        .nav-tabs > li.active > a:focus {
            background: ${theme.palette.action.hover} !important;
            border-right: 1px solid ${blendLighten(theme.palette.background.paper, 0.25)};
            border-left: 1px solid ${blendLighten(theme.palette.background.paper, 0.25)};
            border-top: 1px solid ${blendLighten(theme.palette.background.paper, 0.25)};
            border-bottom: 0;
        }
        .cron_builder .nav-tabs > li.active > a {
            color: ${theme.custom.colors.secondaryColor};
            border-bottom: 1px solid ${blendLighten(theme.palette.background.paper, 0.25)};
        }
        .cron-builder-bg {
            background-color: ${theme.palette.background.paper} !important;
            margin-top: 8px;
        }
        .minutes,
        .hours {
            background: ${theme.palette.background.paper};
            border-radius: 0;
            border: 1px solid ${blendLighten(theme.palette.background.paper, 0.25)};
            margin-left: 8px;
        }
        .col-md-offset-2 {
            margin-left: 0 !important;
        }
        .col-md-6 {
            width: 100% !important;
            padding-left: 0 !important;
        }
        .nav-link.active {
            background-color: inherit !important;
            color: ${theme.palette.primary.main} !important;
            border-color: ${theme.palette.primary.main} !important;
        }
    `,
);
