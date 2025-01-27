import { css, styled } from "@mui/material";

export const TimeRangeStyled = styled("div")(
    ({ theme }) => css`
        min-width: 60%;
        flex: 1;
        display: inline-block;
        .time-range-component {
            display: inline-flex;
            margin-right: 1em;
            line-height: inherit;
        }

        .time-range-component:after {
            clear: both;
        }

        .time-range-input {
            width: 4em;
            border: none !important;
            background-color: ${theme.palette.background.paper};
            text-align: center;
            padding-left: 0;
            padding-right: 0;
            height: 35px;
        }

        .time-range-components {
            display: flex;
            flex-wrap: wrap;
        }

        .time-range-component-label {
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 0.2em 0.6em 0.3em;
            font-weight: 700;
            line-height: 1;
            text-align: center;
            vertical-align: baseline;
            border-radius: 0.25em;
        }
    `,
);
