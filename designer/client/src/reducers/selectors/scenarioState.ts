import { RootState } from "../index";
import { ProcessStateType } from "../../components/Process/types";

export const getScenarioState = (state: RootState): ProcessStateType | null => state.scenarioState;
