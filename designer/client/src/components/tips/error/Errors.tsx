import React, { SyntheticEvent } from "react";
import { v4 as uuid4 } from "uuid";
import DangerousIcon from "@mui/icons-material/Dangerous";
import NodeErrorsLinkSection from "./NodeErrorsLinkSection";
import i18next from "i18next";
import { concat, difference, isEmpty } from "lodash";
import { NodeType, Process, ValidationErrors } from "../../../types";
import { variables } from "../../../stylesheets/variables";
import { styledIcon } from "../Styled";

interface Props {
    errors: ValidationErrors;
    showDetails: (event: SyntheticEvent, details: NodeType) => void;
    currentProcess: Process;
}
export default class Errors extends React.Component<Props> {
    render() {
        const {
            errors = {
                globalErrors: [],
                invalidNodes: {},
                processPropertiesErrors: [],
            },
        } = this.props;
        return (
            <div key={uuid4()} style={{ display: "flex", alignItems: "center" }}>
                {this.headerIcon(errors)}
                {this.errorTips(errors)}
            </div>
        );
    }

    headerIcon = (errors) => {
        const StyledDangerousIcon = styledIcon(DangerousIcon);
        return isEmpty(errors.globalErrors) && isEmpty(errors.invalidNodes) && isEmpty(errors.processPropertiesErrors) ? null : (
            <StyledDangerousIcon sx={{ color: variables.alert.error, alignSelf: "center" }} />
        );
    };

    errorTips = (errors) => {
        const globalErrors = errors.globalErrors;
        const nodeErrors = errors.invalidNodes;
        const propertiesErrors = errors.processPropertiesErrors;

        return isEmpty(nodeErrors) && isEmpty(propertiesErrors) && isEmpty(globalErrors) ? null : (
            <div>
                {this.globalErrorsTips(globalErrors)}
                {this.nodeErrorsTips(propertiesErrors, nodeErrors)}
            </div>
        );
    };

    globalErrorsTips = (globalErrors) => <div>{globalErrors.map((error) => this.globalError(error, null))}</div>;

    globalError = (error, suffix) => (
        <span key={uuid4()} title={error.description}>
            {(suffix ? `${suffix}: ` : "") + error.message + (error.fieldName ? `(${error.fieldName})` : "")}
        </span>
    );

    nodeErrorsTips = (propertiesErrors, nodeErrors) => {
        const { showDetails, currentProcess } = this.props;
        const nodeIds = Object.keys(nodeErrors);

        const looseNodeIds = nodeIds.filter((nodeId) => nodeErrors[nodeId].some((error) => error.typ === "LooseNode"));
        const invalidEndNodeIds = nodeIds.filter((nodeId) => nodeErrors[nodeId].some((error) => error.typ === "InvalidTailOfBranch"));
        const otherNodeErrorIds = difference(nodeIds, concat(looseNodeIds, invalidEndNodeIds));
        const errorsOnTop = this.errorsOnTopPresent(otherNodeErrorIds, propertiesErrors);

        return (
            <>
                <NodeErrorsLinkSection
                    nodeIds={concat(otherNodeErrorIds, isEmpty(propertiesErrors) ? [] : "properties")}
                    message={i18next.t("errors.errorsIn", "Errors in: ")}
                    showDetails={showDetails}
                    currentProcess={currentProcess}
                />
                <NodeErrorsLinkSection
                    nodeIds={looseNodeIds}
                    message={i18next.t("errors.looseNodes", "Loose nodes: ")}
                    showDetails={showDetails}
                    currentProcess={currentProcess}
                    errorsOnTop={errorsOnTop}
                />
                <NodeErrorsLinkSection
                    nodeIds={invalidEndNodeIds}
                    message={i18next.t("errors.invalidScenarioEnd", "Scenario must end with a sink, processor or fragment: ")}
                    showDetails={showDetails}
                    currentProcess={currentProcess}
                    errorsOnTop={errorsOnTop}
                />
            </>
        );
    };

    errorsOnTopPresent(otherNodeErrorIds, propertiesErrors) {
        return !isEmpty(otherNodeErrorIds) || !isEmpty(propertiesErrors);
    }
}
