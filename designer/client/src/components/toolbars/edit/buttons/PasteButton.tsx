import { Box, styled } from "@mui/material";
import type { ComponentProps } from "react";
import React, { useCallback, useRef } from "react";
import { useTranslation } from "react-i18next";

import Icon from "../../../../assets/img/toolbarButtons/paste.svg";
import { isTouchDevice } from "../../../../helpers/detectDevice";
import { useSelectionActions } from "../../../graph/SelectionContextProvider";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import type { ToolbarButtonProps } from "../../types";

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

function PasteButton(props: ToolbarButtonProps): React.JSX.Element {
    const { t } = useTranslation();
    const { paste } = useSelectionActions();
    const { disabled, type } = props;
    const available = !disabled && paste;

    const ref = useRef<HTMLButtonElement & HTMLDivElement>(null);

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
