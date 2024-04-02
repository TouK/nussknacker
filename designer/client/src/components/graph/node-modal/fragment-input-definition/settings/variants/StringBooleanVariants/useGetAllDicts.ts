import { useSelector } from "react-redux";
import { getProcessingType } from "../../../../../../../reducers/selectors/graph";
import { useEffect, useState } from "react";
import httpService from "../../../../../../../http/HttpService";
import { Option } from "../../../FieldsSelect";

export const useGetAllDicts = () => {
    const [processDefinitionDicts, setProcessDefinitionDicts] = useState<Option[]>([]);
    const processingType = useSelector(getProcessingType);

    useEffect(() => {
        httpService.fetchAllProcessDefinitionDataDicts(processingType).then((data) => {
            setProcessDefinitionDicts(data.map(({ id, label }) => ({ label, value: id })));
        });
    }, [processingType]);

    return { processDefinitionDicts };
};
