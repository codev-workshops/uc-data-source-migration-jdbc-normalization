import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { deleteNote, getNotes } from '../../api/additionalNotes';
import Badge from '../../components/Badge';

const NoteList = () => {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ['notes'], queryFn: getNotes });

  const removeMutation = useMutation({
    mutationFn: deleteNote,
    onSuccess: () => {
      toast.success('Note deleted');
      queryClient.invalidateQueries({ queryKey: ['notes'] });
    },
    onError: () => toast.error('Failed to delete note'),
  });

  const notes = [...(data ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">Notes</h1>
        <Link
          to="/notes/new"
          className="rounded-md bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700"
        >
          New Note
        </Link>
      </div>

      {isLoading ? (
        <p className="text-slate-500">Loading...</p>
      ) : notes.length === 0 ? (
        <p className="text-slate-500">No notes yet.</p>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {notes.map((note) => (
            <div key={note.id} className="flex flex-col rounded-xl bg-white p-5 shadow-sm">
              <div className="flex items-center justify-between">
                <h3 className="font-semibold text-slate-800">{note.title || 'Untitled'}</h3>
                {note.category && <Badge label={note.category} color="purple" />}
              </div>
              <p className="mt-2 line-clamp-3 flex-1 text-sm text-slate-600">{note.content}</p>
              <div className="mt-3 flex items-center gap-3 text-sm">
                <Link to={`/notes/${note.id}`} className="text-slate-600 hover:underline">
                  View
                </Link>
                <Link to={`/notes/${note.id}/edit`} className="text-blue-600 hover:underline">
                  Edit
                </Link>
                <button
                  onClick={() => removeMutation.mutate(note.id)}
                  className="text-red-600 hover:underline"
                >
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default NoteList;
