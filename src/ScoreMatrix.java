package src;
public class ScoreMatrix {
    private int[][] scores; // 2D ARRAY - SCORES (TEAM A,B) = POINTS SCORED BY TEAM A AGAINST TEAM B
    private Team[] teams;
    private int size;

    public ScoreMatrix(Team[] teams) {
        this.teams = teams;
        this.size = teams.length;
        this.scores = new int[size][size];

        // INITIALIZE WITH -1 MEANING "NOT PLAYED YET"
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                scores[i][j] = -1;
            }
        }
    }

    // RECORD MATCH RESULT
    public void recordMatch(int teamAId, int teamBId, int scoreA, int scoreB) {
    if (teamAId < 0 || teamAId >= size || teamBId < 0 || teamBId >= size) {
        System.out.println("Warning: Invalid team IDs: " + teamAId + ", " + teamBId);
        return;
    }
    if (teamAId == teamBId) {
        System.out.println("Warning: A team cannot play against itself.");
        return;
    }
    scores[teamAId][teamBId] = scoreA;
    scores[teamBId][teamAId] = scoreB;
}

    // GET SCORE WHEN TEAM A PLAYED TEAM B
    public int getScore(int teamAId, int teamBId) {
        if (teamAId >= 0 && teamAId < size && teamBId >= 0 && teamBId < size) {
            return scores[teamAId][teamBId];
        }
        return -1;
    }

    // CHECK IF TWO TEAMS HAVE PLAYED
   public boolean hasPlayed(int teamAId, int teamBId) {
    if (teamAId < 0 || teamAId >= size || teamBId < 0 || teamBId >= size) return false;
    return scores[teamAId][teamBId] != -1;
}

    // GET TEAM'S TOTAL POINTS SCORED
    public int getTotalPointsScored(int teamId) {
        int total = 0;
        for (int i = 0; i < size; i++) {
            if (scores[teamId][i] != -1) {
                total += scores[teamId][i];
            }
        }
        return total;
    }

    // GET TEAM'S TOTAL POINTS ALLOWED
    public int getTotalPointsAllowed(int teamId) {
        int total = 0;
        for (int i = 0; i < size; i++) {
            if (scores[i][teamId] != -1) {
                total += scores[i][teamId];
            }
        }
        return total;
    }

    // PRINT ENTIRE SCORE MATRIX
    public void printMatrix() {
        System.out.println("\n========== SCORE MATRIX ==========");
        System.out.print("       ");
        for (int i= 0; i < size; i++) {
            System.out.printf("%-12s", teams[i].getName());
        }
        System.out.println();
        System.out.println("       " + "-".repeat(12 * size));

        for (int i = 0; i < size; i++) {
            System.out.printf("%-8s", teams[i].getName());
            for (int j = 0; j < size; j++) {
                if (i==j) {
                    System.out.printf("%-12s", "---");
                } else if (scores[i][j] == -1) {
                    System.out.printf("%-12s", "NOT PLAYED");
                } else {
                    System.out.printf("%-12d", scores[i][j]);
                }
            }
            System.out.println();
        }
        System.out.println("==================================\n");
    }

    public String getHeadToHead(int teamAId, int teamBId) {
    if (teamAId < 0 || teamAId >= size || teamBId < 0 || teamBId >= size) {
        return "Invalid team IDs provided.";
    }
    if (teamAId == teamBId) {
        return "A team cannot play against itself.";
    }
    if (!hasPlayed(teamAId, teamBId)) {
        return teams[teamAId].getName() + " and " + teams[teamBId].getName() + " have not played yet.";
    }
    int scoreA = scores[teamAId][teamBId];
    int scoreB = scores[teamBId][teamAId];
    String winner;
    if (scoreA > scoreB) winner = teams[teamAId].getName();
    else if (scoreB > scoreA) winner = teams[teamBId].getName();
    else winner = "TIE";
    return teams[teamAId].getName() + " " + scoreA + " - " + scoreB + " " + teams[teamBId].getName() + " → Winner: " + winner;
    }
}