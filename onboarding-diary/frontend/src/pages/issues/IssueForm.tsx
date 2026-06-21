import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { createIssue, getIssue, updateIssue } from '../../api/issueLogs';
import type { IssueLogRequest } from '../../types';

const IssueForm = () => {
  const { id } = useParams();
  const isEdit = Boolean(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } =
    useForm<IssueLogRequest>({
      defaultValues: { severity: 'MEDIUM', status: 'OPEN' },
    });

  const { data: existing } = useQuery({
    queryKey: ['issue', id],
    queryFn: () => getIssue(id as string),
    enabled: isEdit,
  });

  useEffect(() => {
    if (existing) {
      reset({
        title: existing.title,
        description: existing.description,
        severity: existing.severity,
        status: existing.status,
        resolution: existing.resolution,
      });
    }
  }, [existing, reset]);

  const mutation = useMutation({
    mutationFn: (values: IssueLogRequest) =>
      isEdit ? updateIssue(id as string, values) : createIssue(values),
    onSuccess: () => {
      toast.success(isEdit ? 'Issue updated' : 'Issue created');
      queryClient.invalidateQueries({ queryKey: ['issues'] });
      navigate('/issues');
    },
    onError: () => toast.error('Failed to save issue'),
  });

  const onSubmit = (values: IssueLogRequest) => mutation.mutate(values);

  return (
    <div className="max-w-2xl space-y-4">
      <h1 className="text-2xl font-bold text-slate-800">{isEdit ? 'Edit Issue' : 'New Issue'}</h1>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded-xl bg-white p-6 shadow-sm">
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Title</label>
          <input
            {...register('title', { required: 'Title is required' })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none"
          />
          {errors.title && <p className="mt-1 text-xs text-red-600">{errors.title.message}</p>}
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Description</label>
          <textarea
            rows={4}
            {...register('description')}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none"
          />
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Severity</label>
            <select {...register('severity')} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </select>
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Status</label>
            <select {...register('status')} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="OPEN">Open</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="RESOLVED">Resolved</option>
              <option value="CLOSED">Closed</option>
            </select>
          </div>
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Resolution</label>
          <textarea
            rows={3}
            {...register('resolution')}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none"
          />
        </div>
        <div className="flex gap-3">
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded-md bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-60"
          >
            {isEdit ? 'Update' : 'Create'}
          </button>
          <button
            type="button"
            onClick={() => navigate('/issues')}
            className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default IssueForm;
