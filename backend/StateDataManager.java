import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Backend service for the app: handles both disk persistence (autosave /
 * autorestore of the in-progress tournament) and in-memory undo history,
 * plus explicit save/load bracket functionality.
 */
public class StateDataManager {

    // ── persistence paths ──────────────────────────────────────────────────
    private static final File STATE_DIR  = new File("app_data");
    private static final File STATE_FILE = new File(STATE_DIR, "autosave.dat");
    private static final File SAVE_DIR   = new File("saved_brackets");

    // ── serializable data containers ───────────────────────────────────────
    public static class AppState implements Serializable {
        private static final long serialVersionUID = 1L;

        public String bracketName;
        public String bracketType;
        public String sport;
        public String description;

        public double scrollH;
        public double scrollV;

        public List<TeamData>  teamList  = new ArrayList<>();
        public List<MatchData> matchList = new ArrayList<>();
    }

    public static class TeamData implements Serializable {
        private static final long serialVersionUID = 1L;

        public final int    id;
        public final String name;

        public TeamData(int id, String name) {
            this.id   = id;
            this.name = name;
        }
    }

    public static class MatchData implements Serializable {
        private static final long serialVersionUID = 1L;

        public final int    matchId;
        public final String winnerName;
        public final String score;

        public MatchData(int matchId, String winnerName, String score) {
            this.matchId    = matchId;
            this.winnerName = winnerName;
            this.score      = score;
        }
    }

    // ── save / load API ────────────────────────────────────────────────────
    public boolean hasSavedState() {
        return STATE_FILE.exists() && STATE_FILE.length() > 0;
    }

    public boolean saveState(AppState state) {
        try {
            if (!STATE_DIR.exists()) STATE_DIR.mkdirs();
            try (ObjectOutputStream out =
                     new ObjectOutputStream(new FileOutputStream(STATE_FILE))) {
                out.writeObject(state);
            }
            return true;
        } catch (IOException e) {
            System.err.println("StateDataManager: failed to save state – " + e.getMessage());
            return false;
        }
    }

    public AppState loadState() {
        if (!hasSavedState()) return null;
        try (ObjectInputStream in =
                 new ObjectInputStream(new FileInputStream(STATE_FILE))) {
            Object obj = in.readObject();
            return (obj instanceof AppState) ? (AppState) obj : null;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("StateDataManager: failed to load state – " + e.getMessage());
            return null;
        }
    }

    // ── BRACKET SAVE / LOAD (text format) ──────────────────────────────────

    /**
     * Saves the current bracket to a text file in the saved_brackets directory.
     * 
     * @param bracketName  The name of the bracket (used as filename)
     * @param bracketType  The bracket type
     * @param sport        The sport/game name
     * @param description  The bracket description
     * @param status       The current status text
     * @param teams        Array of teams
     * @param tournament   The tournament bracket object
     * @return true if save succeeded, false otherwise
     */
    public boolean saveBracket(String bracketName, String bracketType, String sport,
                               String description, String status,
                               Team[] teams, TournamentBracket tournament) {
        try {
            if (!SAVE_DIR.exists()) SAVE_DIR.mkdirs();
            File saveFile = new File(SAVE_DIR, bracketName.replaceAll("\\s+", "_") + ".txt");
            
            try (PrintWriter writer = new PrintWriter(saveFile)) {
                writer.println("BRACKET NAME: " + bracketName);
                writer.println("BRACKET TYPE: " + bracketType);
                writer.println("SPORT/GAME: " + sport);
                writer.println("DESCRIPTION: " + description);
                writer.println("STATUS: " + status);
                writer.println("TEAMS: " + teams.length);
                writer.println("----------------------------------------");
                for (Team t : teams)
                    writer.println("Team: " + t.getName() + " | Wins: " + t.getWins() + " | Losses: " + t.getLosses());
                writer.println("----------------------------------------");
                writer.println("MATCH RESULTS:");
                if (tournament != null) {
                    for (Match m : tournament.getAllMatches()) {
                        if (m.isCompleted()) {
                            String t1 = m.getTeam1() != null ? m.getTeam1().getName() : "TBA";
                            String t2 = m.getTeam2() != null ? m.getTeam2().getName() : "TBA";
                            writer.println("Match " + m.getMatchId() + ": " + t1 + " vs " + t2 + " -> Winner: " + m.getWinner().getName() + " (" + m.getScore() + ")");
                        }
                    }
                }
            }
            return true;
        } catch (FileNotFoundException e) {
            System.err.println("StateDataManager: failed to save bracket – " + e.getMessage());
            return false;
        }
    }

    /**
     * Loads a bracket from a text file.
     * 
     * @param file  The file to load from
     * @return A LoadResult containing all parsed data, or null if load failed
     */
    public LoadResult loadBracket(File file) {
        if (file == null || !file.exists()) return null;
        
        try (Scanner sc = new Scanner(file)) {
            String bracketName = "", bracketType = "Single Elimination", sport = "";
            StringBuilder desc = new StringBuilder();
            List<String> teamNames = new ArrayList<>();
            Map<Integer, String[]> matchResults = new HashMap<>(); // matchId -> {winnerName, score}
            
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.startsWith("BRACKET NAME: ")) {
                    bracketName = line.substring(14);
                } else if (line.startsWith("BRACKET TYPE: ")) {
                    bracketType = line.substring(14);
                } else if (line.startsWith("SPORT/GAME: ")) {
                    sport = line.substring(12);
                } else if (line.startsWith("DESCRIPTION: ")) {
                    desc.append(line.substring(13));
                } else if (line.startsWith("Team: ")) {
                    String tName = line.substring(6);
                    int idx = tName.indexOf(" |");
                    if (idx > 0) tName = tName.substring(0, idx);
                    teamNames.add(tName);
                } else if (line.startsWith("Match ")) {
                    try {
                        int colonIdx = line.indexOf(':');
                        int winnerIdx = line.indexOf("Winner: ");
                        int scoreOpenIdx = line.lastIndexOf('(');
                        int scoreCloseIdx = line.lastIndexOf(')');
                        if (colonIdx > 6 && winnerIdx >= 0 && scoreOpenIdx > winnerIdx && scoreCloseIdx > scoreOpenIdx) {
                            int matchId = Integer.parseInt(line.substring(6, colonIdx).trim());
                            String winnerName = line.substring(winnerIdx + 8, scoreOpenIdx).trim();
                            String score = line.substring(scoreOpenIdx + 1, scoreCloseIdx).trim();
                            matchResults.put(matchId, new String[]{winnerName, score});
                        }
                    } catch (NumberFormatException ignored) { /* skip malformed line */ }
                }
            }
            
            return new LoadResult(bracketName, bracketType, sport, desc.toString(), teamNames, matchResults);
        } catch (FileNotFoundException e) {
            System.err.println("StateDataManager: failed to load bracket – " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets the saved brackets directory.
     * @return The directory containing saved brackets
     */
    public File getSavedBracketsDir() {
        return SAVE_DIR;
    }

    /**
     * Checks if there are any saved brackets.
     * @return true if at least one saved bracket file exists
     */
    public boolean hasSavedBrackets() {
        return SAVE_DIR.exists() && SAVE_DIR.listFiles() != null && SAVE_DIR.listFiles().length > 0;
    }

    /**
     * Result container for loadBracket operation.
     */
    public static class LoadResult {
        public final String bracketName;
        public final String bracketType;
        public final String sport;
        public final String description;
        public final List<String> teamNames;
        public final Map<Integer, String[]> matchResults; // matchId -> {winnerName, score}

        public LoadResult(String bracketName, String bracketType, String sport,
                          String description, List<String> teamNames,
                          Map<Integer, String[]> matchResults) {
            this.bracketName = bracketName;
            this.bracketType = bracketType;
            this.sport = sport;
            this.description = description;
            this.teamNames = teamNames;
            this.matchResults = matchResults;
        }
    }

    // =========================================================================
    // UNDO HISTORY (in-memory only — not persisted to disk)
    // =========================================================================

    private static class MatchSnapshot {
        final int    matchId;
        final String winnerId;
        final String score;

        MatchSnapshot(int matchId, String winnerId, String score) {
            this.matchId  = matchId;
            this.winnerId = winnerId;
            this.score    = score;
        }
    }

    private final Deque<List<MatchSnapshot>> undoStack = new ArrayDeque<>();

    public boolean isUndoEmpty() {
        return undoStack.isEmpty();
    }

    public void clearUndoHistory() {
        undoStack.clear();
    }

    /** Captures the current match results so they can be restored later. */
    public void pushUndo(TournamentBracket tournament) {
        if (tournament == null) return;

        List<MatchSnapshot> frame = new ArrayList<>();
        for (Match m : tournament.getAllMatches()) {
            String winnerName = (m.getWinner() != null) ? m.getWinner().getName() : null;
            frame.add(new MatchSnapshot(m.getMatchId(), winnerName, m.getScore()));
        }
        undoStack.push(frame);
    }

    /**
     * Pops the most recent frame from the undo stack and restores it into
     * {@code tournament}.
     *
     * @return {@code true} on success, {@code false} when the stack is empty.
     */
    public boolean undo(TournamentBracket tournament) {
        if (undoStack.isEmpty()) return false;

        List<MatchSnapshot> frame = undoStack.pop();

        // Build a lookup so we can find each match by id in O(1)
        Map<Integer, MatchSnapshot> snapMap = new HashMap<>();
        for (MatchSnapshot s : frame) snapMap.put(s.matchId, s);

        // Clear all current results first
        for (Match m : tournament.getAllMatches()) {
            m.clearResult();
        }
        tournament.clearScoreMatrix();

        // Re-apply in round order so earlier winners propagate before later
        // rounds look up their advancement slots.
        List<Match> sorted = new ArrayList<>(tournament.getAllMatches());
        sorted.sort(Comparator.comparingInt(Match::getRound));

        for (Match m : sorted) {
            MatchSnapshot s = snapMap.get(m.getMatchId());
            if (s == null) continue;
            tournament.revertMatch(m, s.winnerId, s.score);
        }

        tournament.recalculateAllTeamStats();
        return true;
    }
}