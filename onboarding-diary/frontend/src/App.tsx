import { Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import Login from './pages/auth/Login';
import Register from './pages/auth/Register';
import Dashboard from './pages/dashboard/Dashboard';
import TaskList from './pages/tasks/TaskList';
import TaskForm from './pages/tasks/TaskForm';
import TaskDetail from './pages/tasks/TaskDetail';
import IssueList from './pages/issues/IssueList';
import IssueForm from './pages/issues/IssueForm';
import IssueDetail from './pages/issues/IssueDetail';
import FeedbackList from './pages/feedback/FeedbackList';
import FeedbackForm from './pages/feedback/FeedbackForm';
import FeedbackDetail from './pages/feedback/FeedbackDetail';
import NoteList from './pages/notes/NoteList';
import NoteForm from './pages/notes/NoteForm';
import NoteDetail from './pages/notes/NoteDetail';
import Reports from './pages/reports/Reports';
import AISummary from './pages/ai-summary/AISummary';
import Search from './pages/search/Search';

const App = () => {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<Dashboard />} />
        <Route path="/tasks" element={<TaskList />} />
        <Route path="/tasks/new" element={<TaskForm />} />
        <Route path="/tasks/:id" element={<TaskDetail />} />
        <Route path="/tasks/:id/edit" element={<TaskForm />} />
        <Route path="/issues" element={<IssueList />} />
        <Route path="/issues/new" element={<IssueForm />} />
        <Route path="/issues/:id" element={<IssueDetail />} />
        <Route path="/issues/:id/edit" element={<IssueForm />} />
        <Route path="/feedback" element={<FeedbackList />} />
        <Route path="/feedback/new" element={<FeedbackForm />} />
        <Route path="/feedback/:id" element={<FeedbackDetail />} />
        <Route path="/notes" element={<NoteList />} />
        <Route path="/notes/new" element={<NoteForm />} />
        <Route path="/notes/:id" element={<NoteDetail />} />
        <Route path="/notes/:id/edit" element={<NoteForm />} />
        <Route path="/reports" element={<Reports />} />
        <Route path="/ai-summary" element={<AISummary />} />
        <Route path="/search" element={<Search />} />
      </Route>
    </Routes>
  );
};

export default App;
