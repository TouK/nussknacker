import React, { ComponentProps, useCallback, useRef } from "react";
import { useTranslation } from "react-i18next";
import Icon from "../../../../assets/img/toolbarButtons/paste.svg";
import { useSelectionActions } from "../../../graph/SelectionContextProvider";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { ToolbarButtonProps } from "../../types";
import { Box, styled } from "@mui/material";
import { isTouchDevice } from "../../../../helpers/detectDevice";

const TransparentBox = styled(Box)({
    opacity: 0,
    overflow: "hidden",
});
type TransparentBoxProps = ComponentProps<typeof Box>;

function FakeInput(props: TransparentBoxProps) {
    const clearInput = useCallback((e) => {
        const target = e.target as HTMLElement;
        target.blur();
        target.innerHTML = "";
    }, []);
    return <TransparentBox {...props} contentEditable onInput={clearInput} />;
}

function PasteButton(props: ToolbarButtonProps): JSX.Element {
    const { t } = useTranslation();
    const { paste, canPaste } = useSelectionActions();
    const { disabled, type } = props;
    const available = !disabled && paste && canPaste;

    const ref = useRef<HTMLButtonElement & HTMLDivElement>();

    return (
        <>
            <CapabilitiesToolbarButton
                ref={ref}
                editFrontend
                name={t("panels.actions.edit-paste.button", "paste")}
                icon={<Icon />}
                disabled={!available && !isTouchDevice()}
                onClick={available ? (event) => paste(event.nativeEvent) : null}
                type={type}
            />
            {isTouchDevice() && !available && (
                <FakeInput
                    sx={{
                        position: "absolute",
                        top: ref.current?.offsetTop,
                        left: ref.current?.offsetLeft,
                        width: ref.current?.getBoundingClientRect().width,
                        height: ref.current?.getBoundingClientRect().height,
                    }}
                />
            )}
        </>
    );
}

export default PasteButton;
