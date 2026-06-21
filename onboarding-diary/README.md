# Onboarding Diary

A full-stack application for tracking new-hire onboarding. Recruits log tasks,
issues, and personal notes; managers leave feedback; and everyone gets a
dashboard, reports, AI weekly summaries, and search across their diary.

- **Backend**: Spring Boot 3.2 (Java 17), Spring Security + JWT, Spring Data JPA, PostgreSQL
- **Frontend**: React 19 + Vite + TypeScript, Tailwind CSS, React Router, TanStack Query, Recharts
- **Database**: PostgreSQL (via Docker, `ankane/pgvector` image)
- **Optional AI**: OpenAI chat + embeddings (weekly summaries and semantic search)

## Prerequisites

- Java 17
- Node.js 18+ (developed against Node 22)
- Docker & Docker Compose

## Getting Started

### 1. Start the database

```bash
cd onboarding-diary
docker-compose up -d
```

This starts PostgreSQL on `localhost:5432` with database `onboarding_diary`
(user `postgres`, password `postgres`).

### 2. Run the backend

```bash
cd onboarding-diary/backend
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. JPA `ddl-auto: update` creates the
schema automatically on first run.

### 3. Run the frontend

```bash
cd onboarding-diary/frontend
npm install
npm run dev
```

The app is served on `http://localhost:5173` and talks to the backend at
`http://localhost:8080/api`.

## Environment Variables

| Variable         | Required | Description                                                                 |
| ---------------- | -------- | --------------------------------------------------------------------------- |
| `OPENAI_API_KEY` | No       | Enables real OpenAI weekly summaries and embedding-based semantic search. Without it, the app falls back to a mock summary and text (`ILIKE`-style) search. |

Set it before running the backend:

```bash
export OPENAI_API_KEY=sk-...
mvn spring-boot:run
```

## Accounts & Roles

There are no seeded accounts — register from the UI.

- **Register** at `/register`. New users are created with the `RECRUIT` role.
- **RECRUIT**: manages own tasks, issues, and notes; sees feedback written about them.
- **MANAGER**: sees all logs, writes feedback for recruits, generates reports and AI summaries.
- **ADMIN**: full access across all data.

To create a `MANAGER`/`ADMIN`, register a user, then update its `role` column in
the `users` table (e.g. `UPDATE users SET role = 'MANAGER' WHERE username = '...';`).

## API Overview

| Area      | Endpoint                          | Notes                                           |
| --------- | --------------------------------- | ----------------------------------------------- |
| Auth      | `POST /api/auth/register`         | Register a recruit, returns JWT                 |
|           | `POST /api/auth/login`            | Login, returns JWT                              |
|           | `GET  /api/auth/me`               | Current user                                    |
| Tasks     | `GET/POST /api/task-logs`         | List/create task logs                           |
|           | `GET/PUT/DELETE /api/task-logs/{id}` | Read/update/delete                           |
| Issues    | `GET/POST /api/issue-logs`        | Same pattern as tasks                           |
| Feedback  | `GET/POST /api/feedback-notes`    | Create restricted to MANAGER/ADMIN              |
|           | `PUT/DELETE /api/feedback-notes/{id}` | MANAGER/ADMIN only                          |
| Notes     | `GET/POST /api/notes`             | Personal additional notes                       |
| Dashboard | `GET /api/dashboard/summary`      | Aggregate counts                                |
|           | `GET /api/dashboard/weekly-stats` | Weekly task/issue counts                        |
| Reports   | `GET /api/reports/pdf`            | PDF report (`recruitId`, optional date range)   |
|           | `GET /api/reports/csv`            | CSV (`recruitId`, `type`, optional date range)  |
| AI        | `GET /api/ai/weekly-summary`      | `recruitId`, `week`                             |
|           | `POST /api/ai/semantic-search`    | `{ query, sourceTypes }`                        |
| Users     | `GET /api/users/recruits`         | List recruits (MANAGER/ADMIN)                   |

All endpoints except `/api/auth/**` require an `Authorization: Bearer <token>` header.
