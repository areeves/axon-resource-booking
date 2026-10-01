import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query';
import { useApi } from './useApi';
import type { NewResourceDraft, Resource } from '../types';

export function useResources() {
  const { api, isAuthenticated } = useApi();
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ['resources'],
    queryFn: async () => (await api<Resource[]>('/resources')).data ?? [],
    enabled: isAuthenticated
  });

  const create = useMutation({
    mutationFn: (input: NewResourceDraft) =>
      api<Resource>('/resources', { method: 'POST', body: input, userScoped: true }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['resources'] })
  });

  return {
    resources: query.data,
    isLoading: query.isPending,
    error: query.error,
    refresh: () => query.refetch(),
    create
  };
}
