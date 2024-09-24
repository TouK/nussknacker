import { ScenarioGraph } from "../../types";
import { UIValueParameter } from "./genericAction";
import { SourceWithParametersTest } from "../../http/HttpService";

export interface TestAdhocValidationRequest {
    sourceParameters: SourceWithParametersTest;
    scenarioGraph: ScenarioGraph;
}
