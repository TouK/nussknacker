import { alpha, css, Theme } from "@mui/material";
import { CSSProperties } from "react";
import { CSSObjectWithLabel } from "react-select";
import { blendDarken, blendLighten, getBorderColor } from "../containers/theme/helpers";
import { getLuminance } from "@mui/system/colorManipulator";

export const selectStyled = (theme: Theme) => {
    const commonNodeInput = (padding: CSSProperties["padding"]) => css`
        width: 100%;
        padding: 0 ${padding};
        border: none;
        background-color: ${theme.palette.background.paper};
        color: ${theme.palette.text.secondary};
        font-weight: 400;
        font-size: 14px;
        outline: 1px solid
            ${getLuminance(theme.palette.background.paper) > 0.5
                ? blendLighten(theme.palette.background.paper, 0.05)
                : blendDarken(theme.palette.background.paper, 0.05)};
    `;

    const control = (base: CSSObjectWithLabel, isFocused: boolean, isDisabled: boolean, isError: boolean) => css`
        ${base};
        background-color: ${isDisabled ? theme.palette.action.disabledBackground : theme.palette.background.paper};
        color: ${theme.palette.action.disabled} !important;
        max-height: 35px;
        min-height: 35px;
        border: none;
        border-radius: 0;
        box-shadow: 0;
        outline: 1px solid ${isError ? theme.palette.error.light : isFocused ? theme.palette.primary.main : getBorderColor(theme)} !important;
    `;

    const menuOption = (base: CSSObjectWithLabel, isSelected: boolean, isDisabled: boolean) => css`
        ${base}
        ${commonNodeInput("10px")};
        height: 25px;
        line-height: 25px;
        border: 1px;
        border-radius: 0;
        background-color: ${isSelected
            ? getLuminance(theme.palette.background.paper) > 0.5
                ? blendDarken(theme.palette.background.paper, 0.15)
                : blendLighten(theme.palette.background.paper, 0.15)
            : isDisabled
            ? "none"
            : theme.palette.background.paper};
        color: ${isDisabled && theme.palette.action.disabled};
        &:hover {
            color: ${!isDisabled && "inherit"};
            background-color: ${!isDisabled && theme.palette.action.hover};
        }
    `;

    const labelsInput = (base: CSSObjectWithLabel, isDisabled: boolean) => css`
        ${base};
        ${commonNodeInput("0px")};
        display: flex;
        margin-top: 0px;
        margin-bottom: 0px;
        border-radius: 6px;
        outline: none;
        &:hover {
            color: ${!isDisabled && "inherit"};
            border-color: ${!isDisabled && theme.palette.action.hover};
        }
    `;

    const input = (base: CSSObjectWithLabel) => css`
        ${base};
        ${commonNodeInput("10px")}; //TODO input hides partially due to padding...
        outline: none;
    `;

    const singleValue = (base: CSSObjectWithLabel, isDisabled: boolean) => css`
        ${base};
        ${commonNodeInput("0")}; //TODO input hides partially due to padding...
        background-color: ${isDisabled ? "inherit" : theme.palette.background.paper};
        position: absolute;
        outline: none;
    `;

    const menuList = (base: CSSObjectWithLabel) => css`
        ${base};
        padding-top: 0;
        padding-bottom: 0;

        ::-webkit-scrollbar {
            width: 5px;
            height: 0;
        }

        ::-webkit-scrollbar-track {
            background: ${blendLighten(theme.palette.background.paper, 0.5)};
        }

        ::-webkit-scrollbar-thumb {
            background: ${alpha(theme.palette.background.paper, 0.85)};
        }

        ::-webkit-scrollbar-thumb:hover {
            background: ${theme.palette.action.hover};
        }
    `;

    const menuPortal = (base: CSSObjectWithLabel) => css`
        ${base};
        z-index: 2000;
    `;

    const menu = (base: CSSObjectWithLabel) => css`
        ${base};
        z-index: 2;
        border-radius: 0;
        background-color: ${theme.palette.background.paper};
        box-shadow: none;
        outline: 1px solid ${getBorderColor(theme)};
    `;

    const valueContainer = (base: CSSObjectWithLabel) => css`
        ${base};
    `;

    const dropdownIndicator = (base: CSSObjectWithLabel) => css`
        ${base};
        color: ${theme.palette.text.secondary};
        :hover {
            color: ${theme.palette.text.secondary}; // It overwrites default react-select hover color
        }
    `;

    const indicatorSeparator = (base: CSSObjectWithLabel) => css`
        ${base};
        background-color: ${theme.palette.text.secondary};
    `;

    return {
        control,
        menu,
        menuList,
        menuOption,
        menuPortal,
        labelsInput,
        input,
        singleValue,
        valueContainer,
        dropdownIndicator,
        indicatorSeparator,
    };
};
