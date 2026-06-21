import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { createTask, getTask, updateTask } from '../../api/taskLogs';
import type { TaskLogRequest } from '../../types';

const TaskForm = () => {
  const { id } = useParams();
  const isEdit = Boolean(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } =
    useForm<TaskLogRequest>({
      defaultValues: { status: 'PENDING', priority: 'MEDIUM' },
    });

  const { data: existing } = useQuery({
    queryKey: ['task', id],
    queryFn: () => getTask(id as string),
    enabled: isEdit,
  });

  useEffect(() => {
    if (existing) {
      reset({
        title: existing.title,
        description: existing.description,
        status: existing.status,
        priority: existing.priority,
        dueDate: existing.dueDate,
      });
    }
  }, [existing, reset]);

  const mutation = useMutation({
    mutationFn: (values: TaskLogRequest) =>
      isEdit ? updateTask(id as string, values) : createTask(values),
    onSuccess: () => {
      toast.success(isEdit ? 'Task updated' : 'Task created');
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      navigate('/tasks');
    },
    onError: () => toast.error('Failed to save task'),
  });

  const onSubmit = (values: TaskLogRequest) => mutation.mutate(values);

  return (
    <div className="max-w-2xl space-y-4">
      <h1 className="text-2xl font-bold text-slate-800">{isEdit ? 'Edit Task' : 'New Task'}</h1>
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
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Status</label>
            <select {...register('status')} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="PENDING">Pending</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="COMPLETED">Completed</option>
            </select>
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Priority</label>
            <select {...register('priority')} className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
            </select>
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-700">Due Date</label>
            <input
              type="date"
              {...register('dueDate')}
              className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
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
            onClick={() => navigate('/tasks')}
            className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default TaskForm;
