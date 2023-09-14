import { variables } from "../../stylesheets/variables";
import { styled } from "@mui/material";
import { VersionType } from "./HistoryItem";
import Badge from "../../assets/img/deployed.svg";

export const HistoryItemStyled = styled("li")<{ type: VersionType }>`
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
        border: 2px solid ${variables.defaultTextColor};
        border-width: 0px 0 0 2px;
        padding-left: 10px;
    }

    &:last-child::before {
        height: 14px;
    }

    &:first-child::before {
        top: 14px;
    }

    &::after {
        content: "";
        position: absolute;
        left: 13px;
        top: 14px;
        width: 16px;
        height: 16px;
        background: ${variables.panelBkgColor};
        border: 2px solid ${variables.defaultTextColor};
        border-radius: 50%;
        padding-left: 10px;
    }

    color: ${variables.defaultTextColor};

    ${(props) =>
        props.type === VersionType.current &&
        `
    color: ${variables.defaultTextColor};

    &:hover::after {
        background-color: ${variables.defaultTextColor};
    }

    &::after {
        background-color: ${variables.defaultTextColor};
    }
  `}

    ${(props) =>
        props.type === VersionType.past &&
        `
    color: rgba(${variables.defaultTextColor}, 0.8);
  `}

  ${(props) =>
        props.type === VersionType.future &&
        `
    color: rgba(${variables.defaultTextColor}, 0.3);
  `}

  &:hover {
        background-color: ${variables.panelBkgColor};
        box-sizing: border-box;

        &::after {
            background-color: ${variables.historyItemBackground};
        }
    }
`;

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
