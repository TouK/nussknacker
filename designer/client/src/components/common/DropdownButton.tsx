/* eslint-disable i18next/no-literal-string */
import React, { CSSProperties, MouseEventHandler, PropsWithChildren, ReactNode, useCallback, useState } from "react";
import { createPortal } from "react-dom";

import Select from "react-select";
import { ButtonProps, Button } from "../FormElements";
import { selectStyled } from "../../stylesheets/SelectStyled";
import { useTheme } from "@mui/material";

interface Option<T> {
    label: string;
    value: T;
}

export interface DropdownButtonProps<T> {
    options: Option<T>[];
    onRangeSelect: (value: T) => void;
    wrapperStyle?: CSSProperties;
}

export function DropdownButton<T>(props: PropsWithChildren<ButtonProps & DropdownButtonProps<T>>): JSX.Element {
    const [isOpen, setIsOpen] = useState<boolean>();
    const { options, onRangeSelect: onSelect, children, onClick, wrapperStyle, ...buttonProps } = props;
    const theme = useTheme();

    const { control, input, valueContainer, singleValue, menuPortal, menu, menuList, menuOption, dropdownIndicator, indicatorSeparator } =
        selectStyled(theme);

    const toggleOpen = useCallback(
        (e) => {
            setIsOpen((state) => !state);
            onClick?.(e);
        },
        [onClick],
    );

    const onSelectChange = useCallback(
        ({ value }: Option<T>) => {
            setIsOpen(false);
            onSelect(value);
        },
        [onSelect],
    );

    return (
        <Dropdown
            isOpen={isOpen}
            onClose={toggleOpen}
            style={wrapperStyle}
            target={
                <Button type="button" {...buttonProps} onClick={toggleOpen}>
                    {children}
                </Button>
            }
        >
            <Select
                autoFocus
                backspaceRemovesValue={false}
                components={{ IndicatorsContainer: EmptyContainer }}
                classNamePrefix={"nodeValueSelect"}
                controlShouldRenderValue={false}
                isClearable={false}
                menuPortalTarget={document.body}
                menuIsOpen
                onChange={onSelectChange}
                options={options}
                styles={{
                    container: () => ({
                        height: "0 !important",
                        minHeight: "0 !important",
                        maxHeight: "0 !important",
                        overflow: "hidden",
                        margin: 0,
                        padding: 0,
                    }),
                    control: (base, props) => ({
                        ...control(base, props.isFocused, props.isDisabled, false),
                        height: "0 !important",
                        minHeight: "0 !important",
                        maxHeight: "0 !important",
                        overflow: "hidden",
                        margin: 0,
                        padding: 0,
                    }),
                    dropdownIndicator: (base) => ({
                        ...dropdownIndicator(base),
                    }),
                    indicatorSeparator: (base) => ({
                        ...indicatorSeparator(base),
                    }),
                    menuPortal: ({ width, ...base }) => ({ ...menuPortal({ ...base, minWidth: width }) }),
                    menu: ({ position, ...base }) => ({ ...menu(base) }),
                    input: (base) => ({ ...input(base) }),
                    valueContainer: (base, props) => ({
                        ...valueContainer(base),
                    }),
                    singleValue: (base) => ({ ...singleValue(base, props.disabled) }),
                    menuList: (base) => ({
                        ...menuList(base),
                    }),
                    option: (base, props) => ({
                        ...menuOption(base, props.isSelected, props.isDisabled),
                    }),
                }}
                tabSelectsValue={false}
            />
        </Dropdown>
    );
}

const Menu = (props: PropsWithChildren<unknown>) => (
    <div
        style={{
            position: "absolute",
            left: "10px",
            right: "10px",
            top: "1em",
            bottom: "1em",
        }}
        {...props}
    />
);

const Blanket = ({ onClick }: { onClick: MouseEventHandler }) =>
    createPortal(
        <div
            style={{
                position: "fixed",
                bottom: 0,
                left: 0,
                top: 0,
                right: 0,
                zIndex: 999,
            }}
            onClick={onClick}
        />,
        document.body,
    );

interface DropdownProps {
    children: ReactNode;
    isOpen: boolean;
    target: ReactNode;
    onClose: MouseEventHandler;
    style: CSSProperties;
}

const Dropdown = ({ children, isOpen, target, onClose, style }: DropdownProps) => (
    <span style={{ ...style, position: "relative" }}>
        <span style={{ position: "relative", width: "100%", height: "100%", display: "flex" }}>
            {target}
            {isOpen ? <Menu>{children}</Menu> : null}
            {isOpen ? <Blanket onClick={onClose} /> : null}
        </span>
    </span>
);

const EmptyContainer = () => <></>;
