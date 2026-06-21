import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { createNote, getNote, updateNote } from '../../api/additionalNotes';
import type { AdditionalNoteRequest } from '../../types';

const NoteForm = () => {
  const { id } = useParams();
  const isEdit = Boolean(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } =
    useForm<AdditionalNoteRequest>();

  const { data: existing } = useQuery({
    queryKey: ['note', id],
    queryFn: () => getNote(id as string),
    enabled: isEdit,
  });

  useEffect(() => {
    if (existing) {
      reset({
        title: existing.title,
        content: existing.content,
        category: existing.category,
      });
    }
  }, [existing, reset]);

  const mutation = useMutation({
    mutationFn: (values: AdditionalNoteRequest) =>
      isEdit ? updateNote(id as string, values) : createNote(values),
    onSuccess: () => {
      toast.success(isEdit ? 'Note updated' : 'Note created');
      queryClient.invalidateQueries({ queryKey: ['notes'] });
      navigate('/notes');
    },
    onError: () => toast.error('Failed to save note'),
  });

  const onSubmit = (values: AdditionalNoteRequest) => mutation.mutate(values);

  return (
    <div className="max-w-2xl space-y-4">
      <h1 className="text-2xl font-bold text-slate-800">{isEdit ? 'Edit Note' : 'New Note'}</h1>
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
          <label className="mb-1 block text-sm font-medium text-slate-700">Category</label>
          <input
            {...register('category')}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm font-medium text-slate-700">Content</label>
          <textarea
            rows={6}
            {...register('content')}
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
            onClick={() => navigate('/notes')}
            className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default NoteForm;
