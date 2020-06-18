import {AxiosResponse} from "axios"
import {useCallback, useState} from "react"

export function useFetch<T>(fetchAction: () => Promise<AxiosResponse<T>>, initValue?: T): [T, () => Promise<void>, boolean] {
  const [isLoading, setIsLoading] = useState<boolean>(false)
  const [data, setData] = useState<T>(initValue)

  const getData = useCallback(
    async () => {
      setIsLoading(true)
      const {data} = await fetchAction()
      setData(data)
      setIsLoading(false)
    },
    [fetchAction],
  )
  return [data, getData, isLoading]
}
