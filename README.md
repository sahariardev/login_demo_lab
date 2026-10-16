# Spring Boot Session Auth Demo

A small Spring Boot demo application that shows a classic **server-side, session based login flow**.
It ships with 5 in-memory users, a login screen, specific error messages for a wrong username vs. a
wrong password, and a protected dashboard with a logout button.

> ⚠️ **This is a demo / teaching application.**
> Passwords are stored **in plain text**, are **never hashed**, and every login attempt is written to a
> log file **including the raw username and password**. Do **not** use any of this in production.

---

## Features

- Login screen (Thymeleaf + CSS)
- 5 in-memory users (no database)
- Separate messages for a wrong username and a wrong password
- Session based authentication (`HttpSession`, no Spring Security)
- Protected `/dashboard` page with a welcome message and a **Logout** button
- File logging of every login attempt, with the username **and** password in plain text

---

## Demo users

| # | Username | Password   | Full name   |
|---|----------|------------|-------------|
| 1 | `admin`  | `admin123` | Admin User  |
| 2 | `rifat`  | `rifat123` | Rifat Ahmed |
| 3 | `john`   | `john123`  | John Doe    |
| 4 | `jane`   | `jane123`  | Jane Smith  |
| 5 | `guest`  | `guest123` | Guest User  |

These are defined in
[`src/main/java/com/example/authdemo/service/UserService.java`](src/main/java/com/example/authdemo/service/UserService.java).
Usernames are case-insensitive when signing in.

---

## Requirements

- Java 21 (tested with Temurin 21)
- No Maven/Gradle install needed — the Gradle wrapper is included

---

## Running the app

**Windows**

```powershell
.\gradlew.bat bootRun
```

**macOS / Linux**

```bash
./gradlew bootRun
```

Then open <http://localhost:8080> in a browser. You will be redirected to the login page.

To build a runnable jar instead:

```bash
./gradlew build -x test
java -jar build/libs/auth-demo-1.0.0.jar
```

The app listens on port **8080** (change it with `server.port` in
`src/main/resources/application.properties`).

---

## Running with Docker

A `Dockerfile` and a full [`DOCKER.md`](DOCKER.md) guide are included. Quick start:

```bash
docker build -t auth-demo:1.0.0 .
docker run -d --name auth-demo -p 8080:8080 -e AGENT_TOKEN=dev-token-123 auth-demo:1.0.0
```

Then open <http://localhost:8080>. See [`DOCKER.md`](DOCKER.md) for the read-only/volume variants,
environment variables, log access and troubleshooting.

---

## How the login flow works

1. `GET /login` renders the login form.
2. `POST /login` checks the username first:
   - unknown username → **"Wrong username. Please check your username and try again."**
   - known username but wrong password → **"Wrong password. Please check your password and try again."**
   - both correct → the user is stored in the HTTP session and redirected to `/dashboard`.
3. `GET /dashboard` requires a valid session; otherwise you are sent back to `/login`.
4. `POST /logout` invalidates the session and returns you to the login page.

| Method | Path         | Description                          |
|--------|--------------|--------------------------------------|
| GET    | `/`          | Redirects to `/dashboard` or `/login` |
| GET    | `/login`     | Login page                           |
| POST   | `/login`     | Handles the login form               |
| GET    | `/dashboard` | Protected page (requires session)    |
| POST   | `/logout`    | Ends the session                     |

---

## Logging (plain text usernames & passwords)

Logging is configured in
[`src/main/resources/logback-spring.xml`](src/main/resources/logback-spring.xml) and writes to:

```
logs/authentication.log
```

Every attempt is recorded with the raw credentials, for example:

```
2026-10-16 10:12:03.114 | WARN  | AUTH | LOGIN FAILED (wrong username) | username='nobody' password='whatever' | ip=127.0.0.1
2026-10-16 10:12:19.882 | WARN  | AUTH | LOGIN FAILED (wrong password) | username='admin' password='badpass' | ip=127.0.0.1
2026-10-16 10:12:31.005 | INFO  | AUTH | LOGIN SUCCESS | username='admin' password='admin123' | ip=127.0.0.1
2026-10-16 10:13:02.771 | INFO  | AUTH | LOGOUT | username='admin' | ip=127.0.0.1
```

The log file is rotated daily (`authentication.yyyy-MM-dd.log`) and kept for 30 days.
The `logs/` directory is git-ignored.

You can change the log directory without editing the config by setting an environment variable:

```powershell
$env:LOG_DIR="C:\temp\authlogs"; .\gradlew.bat bootRun
```

---

## Project structure

```
auth_lib/
├── build.gradle                     # Spring Boot 3.3 / Java 21 build
├── settings.gradle
├── gradlew / gradlew.bat            # Gradle wrapper
├── Dockerfile                       # two-stage Gradle -> Alpine runtime image
├── .dockerignore
├── supervisor.sh                    # runs the agent + web app in one container
├── README.md
├── DOCKER.md                        # how to build & run the image
└── src/main/
    ├── java/com/example/authdemo/
    │   ├── AuthDemoApplication.java # Spring Boot entry point
    │   ├── controller/AuthController.java
    │   ├── model/User.java
    │   └── service/
    │       ├── UserService.java     # in-memory users + authenticate()
    │       └── LoginResult.java     # SUCCESS / USERNAME_NOT_FOUND / WRONG_PASSWORD
    └── resources/
        ├── application.properties
        ├── logback-spring.xml       # file logging config
        ├── static/css/style.css
        └── templates/
            ├── login.html
            └── dashboard.html
```

---

## Security notes (on purpose, this is a demo)

- Passwords are kept in memory in plain text and never hashed.
- The log file contains the username and password of every attempt.
- Sessions are stored in the server's memory (no external session store).
- There is no Spring Security, no CSRF protection and no rate limiting.

For a real application you would hash passwords (e.g. BCrypt), never log credentials, and use a
proper authentication framework.
