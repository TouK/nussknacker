import { css } from "@emotion/css";
import { Edit } from "@mui/icons-material";
import { DefaultComponents, WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { NodeType } from "../../../../types";
import { WindowContent, WindowKind } from "../../../../windowManager";
import { LoadingButtonTypes } from "../../../../windowManager/LoadingButton";
import { Scenario } from "../../../Process/types";
import { DescriptionOnlyContent } from "../DescriptionOnlyContent";
import { getReadOnly } from "./selectors";
import { StyledHeader } from "./StyledHeader";
import { editProperties } from "../../../../actions/nk";
import { getScenario } from "../../../../reducers/selectors/graph";
import { usePropertiesState } from "../../../modals/PropertiesDialog";

interface DescriptionDialogProps extends WindowContentProps<WindowKind, { node: NodeType; scenario: Scenario }> {
    editMode?: boolean;
}

function DescriptionDialog(props: DescriptionDialogProps): JSX.Element {
    const { t } = useTranslation();
    const { editMode, close } = props;
    const readOnly = useSelector(getReadOnly);
    const dispatch = useDispatch();
    const scenario = useSelector(getScenario);
    const { currentProperties, editedProperties, handleSetEditedProperties, isTouched } = usePropertiesState();

    const [previewMode, setPreviewMode] = useState(!editMode || readOnly);

    const fieldPath = "additionalFields.description";

    const apply = useMemo<WindowButtonProps | false>(() => {
        if (readOnly) return false;
        if (previewMode && !isTouched) return false;
        return {
            title: t("dialog.button.apply", "apply"),
            action: async () => {
                await dispatch(editProperties(scenario, editedProperties));
                close();
            },
            disabled: !editedProperties.name?.length,
        };
    }, [readOnly, previewMode, isTouched, t, editedProperties, dispatch, scenario, close]);

    const cancel = useMemo<WindowButtonProps | false>(() => {
        if (previewMode && !isTouched) return false;
        return {
            title: t("dialog.button.cancel", "cancel"),
            className: LoadingButtonTypes.secondaryButton,
            action: () => {
                handleSetEditedProperties(fieldPath, currentProperties.additionalFields.description);
                setPreviewMode(true);
            },
        };
    }, [previewMode, isTouched, currentProperties, t, handleSetEditedProperties]);

    const preview = useMemo<WindowButtonProps | false>(() => {
        if (!isTouched) return false;
        return {
            title: previewMode ? t("dialog.button.edit", "edit") : t("dialog.button.preview", "preview"),
            action: () => setPreviewMode((v) => !v),
            className: LoadingButtonTypes.tertiaryButton,
        };
    }, [previewMode, t, isTouched]);

    const componentsOverride = useMemo<Partial<typeof DefaultComponents>>(() => {
        const HeaderTitle = () => <div />;

        if (isTouched || !previewMode) {
            return { HeaderTitle };
        }

        const Header = (props) => (
            <StyledHeader
                {...props}
                sx={{
                    fontSize: ".75em",
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
                onChange={!readOnly && handleSetEditedProperties}
                preview={previewMode}
            />
        </WindowContent>
    );
}

export default DescriptionDialog;
