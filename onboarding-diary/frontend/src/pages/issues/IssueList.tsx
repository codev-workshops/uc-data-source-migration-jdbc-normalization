import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { deleteIssue, getIssues } from '../../api/issueLogs';
import Badge from '../../components/Badge';
import { issueStatusColor, severityColor } from '../../lib/badgeColors';
import type { IssueStatus } from '../../types';

const IssueList = () => {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ['issues'], queryFn: getIssues });
  const [statusFilter, setStatusFilter] = useState<IssueStatus | ''>('');

  const removeMutation = useMutation({
    mutationFn: deleteIssue,
    onSuccess: () => {
      toast.success('Issue deleted');
      queryClient.invalidateQueries({ queryKey: ['issues'] });
    },
    onError: () => toast.error('Failed to delete issue'),
  });

  const filtered = useMemo(() => {
    const issues = data ?? [];
    const scoped = statusFilter ? issues.filter((i) => i.status === statusFilter) : issues;
    return [...scoped].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }, [data, statusFilter]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">Issues</h1>
        <Link
          to="/issues/new"
          className="rounded-md bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700"
        >
          New Issue
        </Link>
      </div>

      <div className="flex gap-3">
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as IssueStatus | '')}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm"
        >
          <option value="">All statuses</option>
          <option value="OPEN">Open</option>
          <option value="IN_PROGRESS">In Progress</option>
          <option value="RESOLVED">Resolved</option>
          <option value="CLOSED">Closed</option>
        </select>
      </div>

      <div className="overflow-hidden rounded-xl bg-white shadow-sm">
        {isLoading ? (
          <p className="p-5 text-slate-500">Loading...</p>
        ) : filtered.length === 0 ? (
          <p className="p-5 text-slate-500">No issues found.</p>
        ) : (
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">Title</th>
                <th className="px-4 py-3">Severity</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filtered.map((issue) => (
                <tr key={issue.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-medium text-slate-800">
                    <Link to={`/issues/${issue.id}`} className="hover:underline">
                      {issue.title}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    <Badge label={issue.severity} color={severityColor(issue.severity)} />
                  </td>
                  <td className="px-4 py-3">
                    <Badge label={issue.status} color={issueStatusColor(issue.status)} />
                  </td>
                  <td className="px-4 py-3 text-right">
                    <Link to={`/issues/${issue.id}/edit`} className="mr-3 text-blue-600 hover:underline">
                      Edit
                    </Link>
                    <button
                      onClick={() => removeMutation.mutate(issue.id)}
                      className="text-red-600 hover:underline"
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};

export default IssueList;
