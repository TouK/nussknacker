import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { WindowContent, WindowKind } from "../../windowManager";
import { css } from "@emotion/css";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { useTranslation } from "react-i18next";
import { editProperties } from "../../actions/nk";
import { useDispatch, useSelector } from "react-redux";
import { getPropertiesErrors, getReadOnly } from "../graph/node-modal/node/selectors";
import { NodeValidationError, PropertiesType } from "../../types";
import { getProcessName, getScenarioPropertiesConfig } from "../graph/node-modal/NodeDetailsContent/selectors";
import { debounce, isEqual } from "lodash";
import { getProcessUnsavedNewName, getScenario } from "../../reducers/selectors/graph";
import NodeUtils from "../graph/NodeUtils";
import { set } from "lodash/fp";
import HttpService from "../../http/HttpService";
import { NodeDocs } from "../graph/node-modal/nodeDetails/SubHeader";
import PropertiesSvg from "../../assets/img/properties.svg";
import { styled } from "@mui/material";
import { WindowHeaderIconStyled } from "../graph/node-modal/nodeDetails/NodeDetailsStyled";
import { PropertiesForm } from "../properties";
import { ContentSize } from "../graph/node-modal/node/ContentSize";
import { RootState } from "../../reducers";

export const usePropertiesState = () => {
    const scenario = useSelector(getScenario);
    const name = useSelector(getProcessUnsavedNewName);
    const currentProperties = useMemo(() => NodeUtils.getProcessProperties(scenario, name), [name, scenario]);
    const [editedProperties, setEditedProperties] = useState<PropertiesType>(currentProperties);
    const isTouched = useMemo(() => !isEqual(currentProperties, editedProperties), [currentProperties, editedProperties]);

    const handleSetEditedProperties = useCallback((label: string | number, value: string) => {
        setEditedProperties((prevState) => set<typeof editedProperties>(label, value, prevState) as unknown as typeof editedProperties);
    }, []);

    return { currentProperties, editedProperties, handleSetEditedProperties, isTouched };
};

export const NodeDetailsModalIcon = styled(WindowHeaderIconStyled.withComponent(PropertiesSvg))(({ theme }) => ({
    backgroundColor: theme.palette.custom.getWindowStyles(WindowKind.editProperties).backgroundColor,
}));

const PropertiesDialog = ({ ...props }: WindowContentProps) => {
    const isEditMode = !useSelector((s: RootState) => getReadOnly(s, false));

    const { t } = useTranslation();
    const dispatch = useDispatch();

    const globalPropertiesErrors = useSelector(getPropertiesErrors);
    const scenarioProperties = useSelector(getScenarioPropertiesConfig);
    const scenario = useSelector(getScenario);
    const scenarioName = useSelector(getProcessName);

    const [errors, setErrors] = useState<NodeValidationError[]>(isEditMode ? globalPropertiesErrors : []);
    const { editedProperties, handleSetEditedProperties } = usePropertiesState();
    const showSwitch = false;

    const debouncedValidateProperties = useMemo(() => {
        return debounce((scenarioName, additionalFields, id) => {
            HttpService.validateProperties(scenarioName, { additionalFields: additionalFields, name: id }).then((data) => {
                if (data) {
                    setErrors(data.validationErrors);
                }
            });
        }, 500);
    }, []);

    const apply = useMemo<WindowButtonProps>(() => {
        return {
            title: t("dialog.button.apply", "apply"),
            action: async () => {
                await dispatch(editProperties(scenario, editedProperties));
                props.close();
            },
        };
    }, [dispatch, editedProperties, props, scenario, t]);

    const cancel = useMemo<WindowButtonProps | false>(() => {
        return {
            title: t("dialog.button.cancel", "cancel"),
            action: () => props.close(),
            className: LoadingButtonTypes.secondaryButton,
        };
    }, [props, t]);

    useEffect(() => {
        if (!isEditMode) {
            return;
        }

        debouncedValidateProperties(scenarioName, editedProperties.additionalFields, editedProperties.name);
    }, [debouncedValidateProperties, isEditMode, editedProperties.additionalFields, editedProperties.name, scenarioName]);

    return (
        <WindowContent
            {...props}
            closeWithEsc
            buttons={[cancel, apply]}
            title={"Properties"}
            icon={<NodeDetailsModalIcon />}
            subheader={<NodeDocs href={scenarioProperties.docsUrl} />}
            classnames={{
                content: css({ minHeight: "100%", display: "flex", ">div": { flex: 1 }, position: "relative" }),
            }}
        >
            <div className={css({ height: "100%", display: "grid", gridTemplateRows: "auto 1fr" })}>
                <ContentSize>
                    <PropertiesForm
                        editedProperties={editedProperties}
                        handleSetEditedProperties={isEditMode ? handleSetEditedProperties : undefined}
                        errors={errors}
                        showSwitch={showSwitch}
                    />
                </ContentSize>
            </div>
        </WindowContent>
    );
};

export default PropertiesDialog;
