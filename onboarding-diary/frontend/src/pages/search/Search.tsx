import { useState } from 'react';
import toast from 'react-hot-toast';
import { semanticSearch } from '../../api/ai';
import Badge from '../../components/Badge';
import type { SearchResult, SourceType } from '../../types';

const ALL_TYPES: SourceType[] = ['TASK', 'ISSUE', 'FEEDBACK', 'NOTE'];

const typeColor = (type: SourceType) => {
  switch (type) {
    case 'TASK':
      return 'blue' as const;
    case 'ISSUE':
      return 'red' as const;
    case 'FEEDBACK':
      return 'green' as const;
    default:
      return 'purple' as const;
  }
};

const Search = () => {
  const [query, setQuery] = useState('');
  const [types, setTypes] = useState<SourceType[]>([...ALL_TYPES]);
  const [results, setResults] = useState<SearchResult[]>([]);
  const [searched, setSearched] = useState(false);
  const [loading, setLoading] = useState(false);

  const toggleType = (type: SourceType) => {
    setTypes((prev) =>
      prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type],
    );
  };

  const handleSearch = async () => {
    if (!query.trim()) {
      toast.error('Enter a search query');
      return;
    }
    setLoading(true);
    try {
      const res = await semanticSearch({ query, sourceTypes: types });
      setResults(res);
      setSearched(true);
    } catch {
      toast.error('Search failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-3xl space-y-4">
      <h1 className="text-2xl font-bold text-slate-800">Search</h1>
      <div className="space-y-4 rounded-xl bg-white p-6 shadow-sm">
        <div className="flex gap-2">
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            placeholder="Search across your onboarding diary..."
            className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none"
          />
          <button
            onClick={handleSearch}
            disabled={loading}
            className="rounded-md bg-slate-800 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-60"
          >
            {loading ? 'Searching...' : 'Search'}
          </button>
        </div>
        <div className="flex flex-wrap gap-4">
          {ALL_TYPES.map((type) => (
            <label key={type} className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={types.includes(type)}
                onChange={() => toggleType(type)}
              />
              {type}
            </label>
          ))}
        </div>
      </div>

      {searched && results.length === 0 ? (
        <p className="text-slate-500">No results found.</p>
      ) : (
        <div className="space-y-3">
          {results.map((result) => (
            <div key={`${result.sourceType}-${result.sourceId}`} className="rounded-xl bg-white p-5 shadow-sm">
              <div className="flex items-center justify-between">
                <Badge label={result.sourceType} color={typeColor(result.sourceType)} />
                <span className="text-xs text-slate-400">
                  score: {result.score.toFixed(3)}
                </span>
              </div>
              <p className="mt-2 text-sm text-slate-700">{result.content}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default Search;
