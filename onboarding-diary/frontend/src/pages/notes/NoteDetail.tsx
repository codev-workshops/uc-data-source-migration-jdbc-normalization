import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { getNote } from '../../api/additionalNotes';
import Badge from '../../components/Badge';

const NoteDetail = () => {
  const { id } = useParams();
  const { data: note, isLoading } = useQuery({
    queryKey: ['note', id],
    queryFn: () => getNote(id as string),
    enabled: Boolean(id),
  });

  if (isLoading) {
    return <p className="text-slate-500">Loading...</p>;
  }

  if (!note) {
    return <p className="text-slate-500">Note not found.</p>;
  }

  return (
    <div className="max-w-2xl space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">{note.title || 'Untitled'}</h1>
        <Link to={`/notes/${note.id}/edit`} className="text-sm font-medium text-blue-600 hover:underline">
          Edit
        </Link>
      </div>
      <div className="space-y-3 rounded-xl bg-white p-6 shadow-sm">
        {note.category && <Badge label={note.category} color="purple" />}
        <p className="whitespace-pre-wrap text-sm text-slate-700">
          {note.content || 'No content.'}
        </p>
        <p className="text-xs text-slate-400">
          Created {new Date(note.createdAt).toLocaleString()}
        </p>
      </div>
      <Link to="/notes" className="text-sm text-slate-500 hover:underline">
        ← Back to notes
      </Link>
    </div>
  );
};

export default NoteDetail;
