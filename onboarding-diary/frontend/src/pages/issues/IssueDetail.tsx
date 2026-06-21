import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { getIssue } from '../../api/issueLogs';
import Badge from '../../components/Badge';
import { issueStatusColor, severityColor } from '../../lib/badgeColors';

const IssueDetail = () => {
  const { id } = useParams();
  const { data: issue, isLoading } = useQuery({
    queryKey: ['issue', id],
    queryFn: () => getIssue(id as string),
    enabled: Boolean(id),
  });

  if (isLoading) {
    return <p className="text-slate-500">Loading...</p>;
  }

  if (!issue) {
    return <p className="text-slate-500">Issue not found.</p>;
  }

  return (
    <div className="max-w-2xl space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">{issue.title}</h1>
        <Link to={`/issues/${issue.id}/edit`} className="text-sm font-medium text-blue-600 hover:underline">
          Edit
        </Link>
      </div>
      <div className="space-y-3 rounded-xl bg-white p-6 shadow-sm">
        <div className="flex gap-2">
          <Badge label={issue.severity} color={severityColor(issue.severity)} />
          <Badge label={issue.status} color={issueStatusColor(issue.status)} />
        </div>
        <div>
          <h3 className="text-xs uppercase text-slate-400">Description</h3>
          <p className="whitespace-pre-wrap text-sm text-slate-700">
            {issue.description || 'No description provided.'}
          </p>
        </div>
        <div>
          <h3 className="text-xs uppercase text-slate-400">Resolution</h3>
          <p className="whitespace-pre-wrap text-sm text-slate-700">
            {issue.resolution || 'Not resolved yet.'}
          </p>
        </div>
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div>
            <dt className="text-slate-400">Created</dt>
            <dd className="text-slate-700">{new Date(issue.createdAt).toLocaleString()}</dd>
          </div>
          <div>
            <dt className="text-slate-400">Updated</dt>
            <dd className="text-slate-700">{new Date(issue.updatedAt).toLocaleString()}</dd>
          </div>
        </dl>
      </div>
      <Link to="/issues" className="text-sm text-slate-500 hover:underline">
        ← Back to issues
      </Link>
    </div>
  );
};

export default IssueDetail;
