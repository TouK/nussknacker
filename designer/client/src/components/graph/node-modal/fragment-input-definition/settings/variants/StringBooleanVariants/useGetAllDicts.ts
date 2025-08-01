import { useEffect, useState } from "react";

import httpService from "../../../../../../../http/HttpService";
import { getProcessingType } from "../../../../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../../../../store/storeHelpers";
import type { ReturnedType } from "../../../../../../../types";
import type { Option } from "../../../FieldsSelect";

interface Props {
    typ: ReturnedType;
}
export const useGetAllDicts = ({ typ }: Props) => {
    const [processDefinitionDicts, setProcessDefinitionDicts] = useState<Option[]>([]);
    const processingType = useAppSelector(getProcessingType);

    useEffect(() => {
        httpService.fetchAllProcessDefinitionDataDicts(processingType, typ.refClazzName).then((response) => {
            setProcessDefinitionDicts(response.data.map(({ id, label }) => ({ label, value: id })));
        });
    }, [processingType, typ]);

    return { processDefinitionDicts };
};
