import {RootState} from "../index"
import {GenericActionState} from "../genericActionState";

export const getGenericActionValidation =  (state: RootState): GenericActionState => state.genericAction