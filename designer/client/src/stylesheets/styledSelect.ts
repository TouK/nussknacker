import { css, darken, Theme } from "@mui/material";
import { CSSProperties } from "react";
import { CSSObjectWithLabel } from "react-select";

export const styledSelect = (theme: Theme) => {
    const commonNodeInput = (padding: CSSProperties["padding"]) => css`
        width: 100%;
        padding: 0 ${padding};
        border: none;
        background-color: #333333;
        color: #cccccc;
        font-weight: 400;
        font-size: 14px;
        outline: 1px solid rgba(255, 255, 255, 0.075);
    `;

    const control = (base: CSSObjectWithLabel, isFocused: boolean, isDisabled: boolean) => css`
        ${base};
        background-color: #333333; //TODO: change me
        max-height: 35px;
        min-height: 35px;
        border: 0;
        border-radius: 0;
        color: #cccccc; //TODO: change me
        box-shadow: 0;

        ${isFocused &&
        css`
            outline: 2px solid #0058a9 !important; //TODO: change me
            outline-offset: -1px !important;
        `}
        ${isDisabled &&
        css`
            background-color: #444444 !important;
        `}
    `;

    const menuOption = (base: CSSObjectWithLabel, isSelected: boolean, isFocused: boolean) => css`
        ${base}
        ${commonNodeInput("10px")};
        height: 25px;
        line-height: 25px;
        border: 1px;
        border-radius: 0;

        ${isSelected &&
        css`
            background-color: #0058a9;
        `}

        ${isFocused &&
        css`
            background-color: ${darken("#0058a9", 0.5)};
        `}
    &:hover {
            background-color: ${darken("#0058a9", 0.5)};
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
        position: absolute;
        outline: none;
        ${isDisabled &&
        css`
            background-color: #444444 !important;
        `}
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
            background: #666666; //TODO: change me;
        }

        ::-webkit-scrollbar-thumb {
            background: #888888;
        }

        ::-webkit-scrollbar-thumb:hover {
            background: #555555;
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
    `;

    const valueContainer = (base: CSSObjectWithLabel, hasValue: boolean) => css`
        ${base};
        ${hasValue &&
        css`
            background-color: #333333; //TODO: change me;
            color: #cccccc; //TODO: change me;
        `}
    `;

    return {
        control,
        menu,
        menuList,
        menuOption,
        menuPortal,
        input,
        singleValue,
        valueContainer,
    };
};
