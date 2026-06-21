import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { createFeedback } from '../../api/feedbackNotes';
import { getRecruits } from '../../api/users';
import type { FeedbackNoteRequest } from '../../types';

const FeedbackForm = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const recruitsQuery = useQuery({ queryKey: ['recruits'], queryFn: getRecruits });

  const { register, handleSubmit, formState: { errors, isSubmitting } } =
    useForm<FeedbackNoteRequest>();

  const mutation = useMutation({
    mutationFn: createFeedback,
    onSuccess: () => {
      toast.success('Feedback submitted');
      queryClient.invalidateQueries({ queryKey: ['feedback'] });
      navigate('/feedback');
    },
    onError: () => toast.error('Failed to submit feedback'),
  });

  const onSubmit = (values: FeedbackNoteRequest) =>
    mutation.mutate({ ...values, week: values.week ? Number(values.week) : undefined });

  return (
    <div className="max-w-2xl space-y-4">
      <h1 className="text-2xl font-bold text-slate-800">New Feedback</h1>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded-xl bg-white p-6 shadow-sm">
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Recruit</label>
          <select
            {...register('recruitId', { required: 'Select a recruit' })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">Select recruit...</option>
            {recruitsQuery.data?.map((r) => (
              <option key={r.id} value={r.id}>
                {r.fullName || r.username}
              </option>
            ))}
          </select>
          {errors.recruitId && (
            <p className="mt-1 text-xs text-red-600">{errors.recruitId.message}</p>
          )}
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Week</label>
          <input
            type="number"
            min={1}
            {...register('week')}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Content</label>
          <textarea
            rows={5}
            {...register('content', { required: 'Content is required' })}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none"
          />
          {errors.content && <p className="mt-1 text-xs text-red-600">{errors.content.message}</p>}
        </div>
        <div className="flex gap-3">
          <button
            type="submit"
            disabled={isSubmitting}
            className="rounded-md bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-60"
          >
            Submit
          </button>
          <button
            type="button"
            onClick={() => navigate('/feedback')}
            className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default FeedbackForm;
