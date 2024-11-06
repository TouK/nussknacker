import { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import { WindowContent, WindowKind } from "../../windowManager";
import { css } from "@emotion/css";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { LoadingButtonTypes } from "../../windowManager/LoadingButton";
import { useTranslation } from "react-i18next";
import { editProperties } from "../../actions/nk";
import { useDispatch, useSelector } from "react-redux";
import { getPropertiesErrors } from "../graph/node-modal/node/selectors";
import { NodeValidationError, PropertiesType } from "../../types";
import { getProcessName, getScenarioPropertiesConfig } from "../graph/node-modal/NodeDetailsContent/selectors";
import { debounce, isEmpty, isEqual, sortBy } from "lodash";
import { getProcessUnsavedNewName, getScenario } from "../../reducers/selectors/graph";
import NodeUtils from "../graph/NodeUtils";
import { set } from "lodash/fp";
import HttpService from "../../http/HttpService";
import { ContentSize } from "../graph/node-modal/node/ContentSize";
import { NodeTable } from "../graph/node-modal/NodeDetailsContent/NodeTable";
import Field, { FieldType } from "../graph/node-modal/editors/field/Field";
import { nodeInput, nodeInputWithError } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { FieldLabel } from "../graph/node-modal/FieldLabel";
import ScenarioProperty from "../graph/node-modal/ScenarioProperty";
import { DescriptionField } from "../graph/node-modal/DescriptionField";
import { NodeField } from "../graph/node-modal/NodeField";
import { getValidationErrorsForField } from "../graph/node-modal/editors/Validators";
import { NodeDocs } from "../graph/node-modal/nodeDetails/SubHeader";
import PropertiesSvg from "../../assets/img/properties.svg";
import { styled } from "@mui/material";
import { WindowHeaderIconStyled } from "../graph/node-modal/nodeDetails/NodeDetailsStyled";
import NodeAdditionalInfoBox from "../graph/node-modal/NodeAdditionalInfoBox";

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

const EditPropertiesDialog = ({ ...props }: WindowContentProps) => {
    const isEditMode = true;
    const { t } = useTranslation();
    const dispatch = useDispatch();

    const globalPropertiesErrors = useSelector(getPropertiesErrors);
    const [errors, setErrors] = useState<NodeValidationError[]>(isEditMode ? globalPropertiesErrors : []);
    const scenarioProperties = useSelector(getScenarioPropertiesConfig);
    const scenarioPropertiesConfig = useMemo(() => scenarioProperties?.propertiesConfig ?? {}, [scenarioProperties?.propertiesConfig]);

    //fixme move this configuration to some better place?
    //we sort by name, to have predictable order of properties (should be replaced by defining order in configuration)
    const scenarioPropertiesSorted = useMemo(
        () => sortBy(Object.entries(scenarioPropertiesConfig), ([name]) => name),
        [scenarioPropertiesConfig],
    );

    const scenario = useSelector(getScenario);
    const scenarioName = useSelector(getProcessName);
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

    useEffect(() => {
        if (!isEditMode) {
            return;
        }

        debouncedValidateProperties(scenarioName, editedProperties.additionalFields, editedProperties.name);
    }, [debouncedValidateProperties, isEditMode, editedProperties.additionalFields, editedProperties.name, scenarioName]);

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
                    <NodeTable>
                        <Field
                            type={FieldType.input}
                            isMarked={false}
                            showValidation
                            onChange={(newValue) => handleSetEditedProperties("name", newValue.toString())}
                            readOnly={!isEditMode}
                            className={isEmpty(errors) ? nodeInput : `${nodeInput} ${nodeInputWithError}`}
                            fieldErrors={getValidationErrorsForField(errors, "name")}
                            value={editedProperties.name}
                            autoFocus
                        >
                            <FieldLabel title={"Name"} label={"Name"} />
                        </Field>
                        {scenarioPropertiesSorted.map(([propName, propConfig]) => (
                            <ScenarioProperty
                                key={propName}
                                showSwitch={showSwitch}
                                showValidation
                                propertyName={propName}
                                propertyConfig={propConfig}
                                errors={errors}
                                onChange={handleSetEditedProperties}
                                renderFieldLabel={() => (
                                    <FieldLabel title={propConfig.label} label={propConfig.label} hintText={propConfig.hintText} />
                                )}
                                editedNode={editedProperties}
                                readOnly={!isEditMode}
                            />
                        ))}
                        <DescriptionField
                            isEditMode={isEditMode}
                            showValidation
                            node={editedProperties}
                            renderFieldLabel={(paramName) => <FieldLabel title={paramName} label={paramName} />}
                            setProperty={handleSetEditedProperties}
                            errors={errors}
                        />
                        <NodeField
                            isEditMode={isEditMode}
                            showValidation
                            node={editedProperties}
                            renderFieldLabel={(paramName) => <FieldLabel title={paramName} label={paramName} />}
                            setProperty={handleSetEditedProperties}
                            errors={errors}
                            fieldType={FieldType.checkbox}
                            fieldName={"additionalFields.showDescription"}
                            description={"Show description each time scenario is opened"}
                        />
                        <NodeAdditionalInfoBox node={editedProperties} handleGetAdditionalInfo={HttpService.getPropertiesAdditionalInfo} />
                    </NodeTable>
                </ContentSize>
            </div>
        </WindowContent>
    );
};

export default EditPropertiesDialog;
