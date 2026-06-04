# 🏆 Intramural Bracket Generator

A JavaFX desktop app for generating and managing intramural sports tournament brackets.

---

## Features
- **7 tournament formats:** Single Elimination, Double Elimination, Round Robin, Swiss, Free For All, Play-In SE, and Play-In DE
- Auto-handles byes, seeding, and bracket propagation
- Click any match to enter scores — winners advance automatically
- Full undo stack for reverting results
- Drag-and-drop participant reordering
- Save and load bracket state to file
- Live progress bar and completion status

---

## Setup

**Requirements:** Java 11+, JavaFX SDK

**Compile:**
```bash
javac --module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.fxml *.java
```

**Run:**
```bash
java --module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.fxml app
```

---

## How to Use
1. Add team names in the left panel and drag to reorder seeds
2. Select teams to include, then choose a tournament format
3. Click **Generate Bracket**
4. Click a match card to enter scores — the bracket updates automatically
5. Use **Undo** to revert any result
6. Save your bracket at any time and reload it later

---

## Project Structure
| File | Role |
|---|---|
| `app.java` | UI layout, event handling, and bracket rendering |
| `TournamentBracket.java` | Core bracket logic and winner propagation |
| `Match.java` | Match node storing teams, score, round, and Play-In flags |
| `Team.java` | Team stats tracking wins, losses, and points |
| `ScoreMatrix.java` | 2D matrix of head-to-head scores for Round Robin/Swiss formats |
| `TournamentType.java` | Enum of all 7 supported tournament formats |

---
## Data Model Overview
```
Match (tree node)
├── leftChild / rightChild  → previous-round matches feeding into this one
├── team1, team2            → competing teams (null = TBD/bye)
├── winner, score           → set when the match is played
├── round, matchId          → position in the bracket
└── flags: isPlayIn, isPlayInGrandFinal, isMainBracket, isWinnersBracket
```

```
Team
├── id, name
├── wins, losses
└── pointsScored, pointsAllowed → updated automatically when a match is completed
```

```
ScoreMatrix  (used in Round Robin / Swiss)
└── int[n][n] grid → scores[A][B] = points A scored against B (-1 if not yet played)
```
