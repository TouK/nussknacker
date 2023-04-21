import {RootState} from "../index"
import {ProcessStateType} from "../../components/Process/types";

export const getProcessState =  (state: RootState): ProcessStateType|null => state.scenarioState
