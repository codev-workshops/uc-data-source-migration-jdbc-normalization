import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { getFeedback } from '../../api/feedbackNotes';
import Badge from '../../components/Badge';

const FeedbackDetail = () => {
  const { id } = useParams();
  const { data, isLoading } = useQuery({ queryKey: ['feedback'], queryFn: getFeedback });
  const note = data?.find((f) => f.id === id);

  if (isLoading) {
    return <p className="text-slate-500">Loading...</p>;
  }

  if (!note) {
    return <p className="text-slate-500">Feedback not found.</p>;
  }

  return (
    <div className="max-w-2xl space-y-4">
      <h1 className="text-2xl font-bold text-slate-800">Feedback Detail</h1>
      <div className="space-y-3 rounded-xl bg-white p-6 shadow-sm">
        <div className="flex items-center gap-2">
          {note.week != null && <Badge label={`Week ${note.week}`} color="blue" />}
        </div>
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div>
            <dt className="text-slate-400">Recruit</dt>
            <dd className="text-slate-700">{note.recruitName || note.recruitId}</dd>
          </div>
          <div>
            <dt className="text-slate-400">Manager</dt>
            <dd className="text-slate-700">{note.managerName || note.managerId}</dd>
          </div>
        </dl>
        <div>
          <h3 className="text-xs uppercase text-slate-400">Content</h3>
          <p className="whitespace-pre-wrap text-sm text-slate-700">{note.content}</p>
        </div>
        <p className="text-xs text-slate-400">
          Created {new Date(note.createdAt).toLocaleString()}
        </p>
      </div>
      <Link to="/feedback" className="text-sm text-slate-500 hover:underline">
        ← Back to feedback
      </Link>
    </div>
  );
};

export default FeedbackDetail;
