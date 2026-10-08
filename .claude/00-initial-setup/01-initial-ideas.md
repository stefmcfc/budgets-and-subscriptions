# Personal Finance App: Project Brief

## Purpose
A personal finance tracker to practise Spring Boot properly. The goal is as much about learning (hand-coding the core layers) as it is about the app itself.

## Working Agreement (for Claude Code)
This project is a deliberate split between AI and human work.

**Claude writes:**
- Specs, high-level overviews and architecture notes
- Project scaffolding and boilerplate (pom/build files, config, package structure, Java records, empty class skeletons)

**I hand-code (Claude must NOT write these for me):**
- Database interactions (entities, repositories, queries, migrations)
- Security (authentication, password storage, validation)
- API layer and API documentation
- Client/service code
- Java classes (not records)
- Tests

**Claude's role during hand-coded work: reviewer.**
- Stop at each hand-coded step and wait for me to write it
- Review what I wrote: correctness, security issues, style, missed edge cases
- Give hints and explanations when I'm stuck, not full solutions, unless I explicitly ask
- Flag it if I ask for something that would break this agreement

## Core Ideas

### 1. Accounts and transactions
- Track money in and out across all bank accounts
- Break transactions down into categories
- Reminders for when to switch bank accounts (switching offers)

### 2. Spending insights
- See where money actually goes, including the quiet drains
- Subscriptions (monthly and annual) and game microtransactions
- Monthly total spent on subscriptions and games, plus a breakdown per subscription / per game (both views wanted)
- Monthly income vs expenditure
- Easy view of big one-off purchases

### 3. Brother's Amazon purchases (running balance)
- My brother buys things through my Amazon account and pays me back later
- Record these purchases and repayments
- Running balance: each Amazon purchase increases what he owes, each repayment reduces it
- These transactions stay visible in spending history, marked as owed/reimbursed
- They are excluded from my personal spending totals
- Show: current amount owed, plus purchases in the last 30 days

## Tech
- Java
- Spring Boot (hands-on practice with the framework is a main goal)
- Database: not yet chosen
- Authentication/security: Spring Security is the likely route, not yet decided
- Front end: not yet discussed

## Build Order
1. **Authentication first.** Simplest secure version, then add complexity once understood.
    - Learning goals: how sensitive data is stored in the DB, how a front end interacts with the system, validation, SQL injection protection, how the system protects database interactions
    - Aim to follow a request through the whole stack, from login form to database storage
2. Accounts and transactions
3. Categories and spending summaries
4. Subscriptions and game microtransactions views
5. Brother's Amazon balance tracking
6. Reminders (bank switching)

## Outstanding Questions
- **Data entry:** manual entry, CSV import, or bank integration (e.g. Open Banking)? This shapes the whole design.
- **Database:** which one, and how to manage schema changes (Flyway/Liquibase or none)?
- **Auth approach:** session-based or token-based (JWT)? Single user or multi-user?
- **Front end:** what will the client be (server-rendered, SPA, or API-only to start)?
- **Categories:** fixed list or user-defined? Can a transaction have more than one?
- **Subscription and game detection:** tag manually, or detect recurring/merchant patterns automatically?
- **Big purchase threshold:** fixed amount, or relative to typical spending?
- **Brother's balance:** tag transactions manually as "brother's purchase"? How are repayments recorded and matched?
- **Reminders:** in-app only, email, or push? How are bank-switch offers sourced?
- **Money handling:** currency (GBP only?) and use of BigDecimal for amounts
- **Hosting/deployment:** local only for now, or deployed somewhere?
- **Testing approach:** what levels of testing, and what tooling?

## First Steps for Claude Code
1. Read this brief and ask me any clarifying questions
2. Draft a spec for the authentication feature
3. Generate the project scaffold only
4. Hand over to me for the first hand-coded piece, then review it