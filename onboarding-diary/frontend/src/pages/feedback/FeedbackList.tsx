import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { deleteFeedback, getFeedback } from '../../api/feedbackNotes';
import Badge from '../../components/Badge';
import { useAuth } from '../../context/AuthContext';

const FeedbackList = () => {
  const { role } = useAuth();
  const canManage = role === 'MANAGER' || role === 'ADMIN';
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ['feedback'], queryFn: getFeedback });

  const removeMutation = useMutation({
    mutationFn: deleteFeedback,
    onSuccess: () => {
      toast.success('Feedback deleted');
      queryClient.invalidateQueries({ queryKey: ['feedback'] });
    },
    onError: () => toast.error('Failed to delete feedback'),
  });

  const feedback = [...(data ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">Feedback</h1>
        {canManage && (
          <Link
            to="/feedback/new"
            className="rounded-md bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700"
          >
            New Feedback
          </Link>
        )}
      </div>

      {isLoading ? (
        <p className="text-slate-500">Loading...</p>
      ) : feedback.length === 0 ? (
        <p className="text-slate-500">No feedback yet.</p>
      ) : (
        <div className="space-y-3">
          {feedback.map((note) => (
            <div key={note.id} className="rounded-xl bg-white p-5 shadow-sm">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-2">
                  {note.week != null && <Badge label={`Week ${note.week}`} color="blue" />}
                  <span className="text-sm font-semibold text-slate-700">
                    {note.recruitName || 'Recruit'}
                  </span>
                </div>
                <div className="text-xs text-slate-400">
                  {new Date(note.createdAt).toLocaleDateString()}
                </div>
              </div>
              <p className="mt-2 line-clamp-3 text-sm text-slate-700">{note.content}</p>
              <div className="mt-3 flex items-center gap-3 text-sm">
                <Link to={`/feedback/${note.id}`} className="text-slate-600 hover:underline">
                  View
                </Link>
                {canManage && (
                  <button
                    onClick={() => removeMutation.mutate(note.id)}
                    className="text-red-600 hover:underline"
                  >
                    Delete
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default FeedbackList;
