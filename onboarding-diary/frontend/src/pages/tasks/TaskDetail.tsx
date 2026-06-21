import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { getTask } from '../../api/taskLogs';
import Badge from '../../components/Badge';
import { priorityColor, taskStatusColor } from '../../lib/badgeColors';

const TaskDetail = () => {
  const { id } = useParams();
  const { data: task, isLoading } = useQuery({
    queryKey: ['task', id],
    queryFn: () => getTask(id as string),
    enabled: Boolean(id),
  });

  if (isLoading) {
    return <p className="text-slate-500">Loading...</p>;
  }

  if (!task) {
    return <p className="text-slate-500">Task not found.</p>;
  }

  return (
    <div className="max-w-2xl space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">{task.title}</h1>
        <Link to={`/tasks/${task.id}/edit`} className="text-sm font-medium text-blue-600 hover:underline">
          Edit
        </Link>
      </div>
      <div className="space-y-3 rounded-xl bg-white p-6 shadow-sm">
        <div className="flex gap-2">
          <Badge label={task.status} color={taskStatusColor(task.status)} />
          <Badge label={task.priority} color={priorityColor(task.priority)} />
        </div>
        <p className="whitespace-pre-wrap text-sm text-slate-700">
          {task.description || 'No description provided.'}
        </p>
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div>
            <dt className="text-slate-400">Due date</dt>
            <dd className="text-slate-700">{task.dueDate ?? '-'}</dd>
          </div>
          <div>
            <dt className="text-slate-400">Completed at</dt>
            <dd className="text-slate-700">{task.completedAt ?? '-'}</dd>
          </div>
          <div>
            <dt className="text-slate-400">Created</dt>
            <dd className="text-slate-700">{new Date(task.createdAt).toLocaleString()}</dd>
          </div>
          <div>
            <dt className="text-slate-400">Updated</dt>
            <dd className="text-slate-700">{new Date(task.updatedAt).toLocaleString()}</dd>
          </div>
        </dl>
      </div>
      <Link to="/tasks" className="text-sm text-slate-500 hover:underline">
        ← Back to tasks
      </Link>
    </div>
  );
};

export default TaskDetail;
