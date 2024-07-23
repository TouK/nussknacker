import { useSelector } from "react-redux";
import { getProcessingType } from "../../../../../../../reducers/selectors/graph";
import { useEffect, useState } from "react";
import httpService from "../../../../../../../http/HttpService";
import { Option } from "../../../FieldsSelect";
import { ReturnedType } from "../../../../../../../types";

interface Props {
    typ: ReturnedType;
}
export const useGetAllDicts = ({ typ }: Props) => {
    const [processDefinitionDicts, setProcessDefinitionDicts] = useState<Option[]>([]);
    const processingType = useSelector(getProcessingType);

    useEffect(() => {
        httpService.fetchAllProcessDefinitionDataDicts(processingType, typ.refClazzName).then((response) => {
            setProcessDefinitionDicts(response.data.map(({ id, label }) => ({ label, value: id })));
        });
    }, [processingType, typ]);

    return { processDefinitionDicts };
};
