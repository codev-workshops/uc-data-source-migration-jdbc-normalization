import { useMemo, useState } from 'react';
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { deleteTask, getTasks } from '../../api/taskLogs';
import Badge from '../../components/Badge';
import { priorityColor, taskStatusColor } from '../../lib/badgeColors';
import type { TaskStatus } from '../../types';

const TaskList = () => {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ['tasks'], queryFn: getTasks });
  const [statusFilter, setStatusFilter] = useState<TaskStatus | ''>('');

  const removeMutation = useMutation({
    mutationFn: deleteTask,
    onSuccess: () => {
      toast.success('Task deleted');
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
    },
    onError: () => toast.error('Failed to delete task'),
  });

  const filtered = useMemo(() => {
    const tasks = data ?? [];
    const scoped = statusFilter ? tasks.filter((t) => t.status === statusFilter) : tasks;
    return [...scoped].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }, [data, statusFilter]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">Tasks</h1>
        <Link
          to="/tasks/new"
          className="rounded-md bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700"
        >
          New Task
        </Link>
      </div>

      <div className="flex gap-3">
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as TaskStatus | '')}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm"
        >
          <option value="">All statuses</option>
          <option value="PENDING">Pending</option>
          <option value="IN_PROGRESS">In Progress</option>
          <option value="COMPLETED">Completed</option>
        </select>
      </div>

      <div className="overflow-hidden rounded-xl bg-white shadow-sm">
        {isLoading ? (
          <p className="p-5 text-slate-500">Loading...</p>
        ) : filtered.length === 0 ? (
          <p className="p-5 text-slate-500">No tasks found.</p>
        ) : (
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">Title</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Priority</th>
                <th className="px-4 py-3">Due Date</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filtered.map((task) => (
                <tr key={task.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-medium text-slate-800">
                    <Link to={`/tasks/${task.id}`} className="hover:underline">
                      {task.title}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    <Badge label={task.status} color={taskStatusColor(task.status)} />
                  </td>
                  <td className="px-4 py-3">
                    <Badge label={task.priority} color={priorityColor(task.priority)} />
                  </td>
                  <td className="px-4 py-3 text-slate-600">{task.dueDate ?? '-'}</td>
                  <td className="px-4 py-3 text-right">
                    <Link to={`/tasks/${task.id}/edit`} className="mr-3 text-blue-600 hover:underline">
                      Edit
                    </Link>
                    <button
                      onClick={() => removeMutation.mutate(task.id)}
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

export default TaskList;
