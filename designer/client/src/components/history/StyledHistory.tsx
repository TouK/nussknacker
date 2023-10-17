import { css, styled } from "@mui/material";
import { VersionType } from "./HistoryItem";
import Badge from "../deployed.svg";
import color from "color";

export const HistoryItemStyled = styled("li")<{ type: VersionType }>(
    ({ theme, type }) => css`
        cursor: pointer;
        overflow: hidden;
        position: relative;
        padding: 5px 0 5px 42px;
        display: flex;
        justify-content: space-between;
        align-items: flex-start;

        .date {
            pointer-events: none;
        }

        &::before {
            content: "";
            position: absolute;
            left: 20px;
            top: 0;
            width: 20px;
            height: 999px;
            border: 2px solid ${theme.custom.colors.secondaryColor};
            border-width: 0px 0 0 2px;
            padding-left: 10px;
        }

        &:last-of-type::before {
            height: 14px;
        }

        &:first-of-type::before {
            top: 14px;
        }

        &::after {
            content: "";
            position: absolute;
            left: 13px;
            top: 14px;
            width: 16px;
            height: 16px;
            background: ${theme.custom.colors.tundora};
            border: 2px solid ${theme.custom.colors.secondaryColor};
            border-radius: 50%;
            padding-left: 10px;
        }

        color: ${theme.custom.colors.secondaryColor};

        ${type === VersionType.current &&
        css`
            color: ${theme.custom.colors.secondaryColor};
            &:hover::after {
                background-color: ${theme.custom.colors.secondaryColor} !important;
            }
            &::after {
                background-color: ${theme.custom.colors.secondaryColor};
            }
        `}

        ${type === VersionType.past &&
        css`
            color: rgba(${color.rgb(theme.custom.colors.secondaryColor).array()}, 0.8);
        `}

  ${type === VersionType.future &&
        css`
            color: rgba(${color.rgb(theme.custom.colors.secondaryColor).array()}, 0.3);
        `}

        &:hover {
            background-color: ${theme.custom.colors.charcoal};
            box-sizing: border-box;

            &::after {
                background-color: ${theme.custom.colors.mutedColor};
            }
        }
    `,
);

export const TrackVertical = styled("div")`
    width: 8px !important;
    position: absolute;
    right: 2px;
    bottom: 2px;
    top: 2px;
    border-radius: 6px !important;
    visibility: visible;
`;

export const ProcessHistoryWrapper = styled("ul")`
    font-size: 12px;
    padding: 5px 0;
    list-style: none;
    margin: 0;
`;

export const StyledBadge = styled(Badge)`
    height: 1.2em;
    margin: 0 1.2em;
`;
