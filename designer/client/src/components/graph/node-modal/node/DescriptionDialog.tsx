import { css } from "@emotion/css";
import { Edit } from "@mui/icons-material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { DefaultComponents } from "@touk/window-manager";
import React, { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { getUserSettings } from "../../../../reducers/selectors/userSettings";
import { useAppSelector } from "../../../../store/storeHelpers";
import type { NodeType } from "../../../../types/node";
import { LoadingButtonTypes } from "../../../../windowManager/LoadingButton";
import { WindowContent } from "../../../../windowManager/WindowContent";
import type { WindowKind } from "../../../../windowManager/WindowKind";
import { usePropertiesState } from "../../../modals/usePropertiesState";
import type { Scenario } from "../../../Process/types";
import { DescriptionOnlyContent } from "../DescriptionOnlyContent";
import { getReadOnly } from "./selectors";
import { StyledHeader } from "./StyledHeader";

interface DescriptionDialogProps extends WindowContentProps<WindowKind, { node: NodeType; scenario: Scenario }> {
    editMode?: boolean;
}

function DescriptionDialog(props: DescriptionDialogProps): React.JSX.Element {
    const { t } = useTranslation();
    const { editMode, close } = props;
    const readOnly = useAppSelector(getReadOnly);
    const { currentProperties, editedProperties, handleSetEditedProperties, isTouched, manualApply } = usePropertiesState();
    const settings = useAppSelector(getUserSettings);
    const autoApply = settings["node.autoApply"];

    const [previewMode, setPreviewMode] = useState(!editMode || readOnly);

    const fieldPath = "additionalFields.description";

    const apply = useMemo<WindowButtonProps | false>(() => {
        if (readOnly) return false;
        if (autoApply) return false;
        if (previewMode && !isTouched) return false;
        return {
            title: t("dialog.button.apply", "apply"),
            action: async () => {
                await manualApply();
                close();
            },
            disabled: !editedProperties.name?.length,
        };
    }, [autoApply, close, editedProperties.name?.length, isTouched, manualApply, previewMode, readOnly, t]);

    const cancel = useMemo<WindowButtonProps | false>(() => {
        if (previewMode && !isTouched) return false;
        return {
            title: autoApply ? t("dialog.button.close", "close") : t("dialog.button.cancel", "cancel"),
            className: LoadingButtonTypes.secondaryButton,
            action: () => {
                handleSetEditedProperties(fieldPath, currentProperties.additionalFields.description);
                setPreviewMode(true);
            },
        };
    }, [previewMode, isTouched, autoApply, t, handleSetEditedProperties, currentProperties.additionalFields.description]);

    const preview = useMemo<WindowButtonProps | false>(() => {
        if (autoApply) return false;
        if (!isTouched) return false;
        return {
            title: previewMode ? t("dialog.button.edit", "edit") : t("dialog.button.preview", "preview"),
            action: () => setPreviewMode((v) => !v),
            className: LoadingButtonTypes.tertiaryButton,
        };
    }, [autoApply, isTouched, previewMode, t]);

    const componentsOverride = useMemo<Partial<typeof DefaultComponents>>(() => {
        const HeaderTitle = () => <div />;

        if (isTouched || !previewMode) {
            return { HeaderTitle };
        }

        const Header = (props) => (
            <StyledHeader
                {...props}
                sx={{
                    "@media (any-pointer: fine)": {
                        fontSize: ".75em",
                    },
                    backgroundColor: "transparent",
                    "&:hover, &:active": { backgroundColor: "var(--backgroundColor)" },
                }}
            />
        );
        const HeaderButtonZoom = (props) => (
            <>
                {readOnly ? null : (
                    <DefaultComponents.HeaderButton action={() => setPreviewMode(false)} name="edit">
                        <Edit
                            sx={{
                                fontSize: "inherit",
                                width: "unset",
                                height: "unset",
                                padding: ".25em",
                            }}
                        />
                    </DefaultComponents.HeaderButton>
                )}
                <DefaultComponents.HeaderButtonZoom {...props} />
            </>
        );

        return { Header, HeaderTitle, HeaderButtonZoom };
    }, [isTouched, previewMode, readOnly]);

    return (
        <WindowContent
            {...props}
            closeWithEsc
            buttons={[preview, cancel, apply]}
            classnames={{
                content: css({ minHeight: "100%", display: "flex", ">div": { flex: 1 }, position: "relative" }),
            }}
            components={componentsOverride}
        >
            <DescriptionOnlyContent
                fieldPath={fieldPath}
                properties={editedProperties}
                onChange={readOnly ? undefined : handleSetEditedProperties}
                preview={previewMode}
            />
        </WindowContent>
    );
}

export default DescriptionDialog;
