import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';

export function useResources() {
  const { api, isAuthenticated } = useApi();
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ['resources'],
    queryFn: async () => (await api<Resource[]>('/resources')).data ?? [],
    enabled: isAuthenticated
  });

  const create = useMutation({
    mutationFn: (input: NewResource) =>
      api<Resource>('/resources', { method: 'POST', body: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['resources'] })
  });

  return { resources: query.data, isLoading: query.isPending, error: query.error, create };
}
