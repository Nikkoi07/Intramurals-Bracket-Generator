public class Match {
    private Match leftChild;
    private Match rightChild;
    private Team team1;
    private Team team2;
    private Team winner;
    private String score;
    private int round;
    private boolean completed;
    private int matchId;
    private static int idCounter = 1;
    private boolean isWinnersBracket;

    public static void resetIdCounter() { idCounter = 1; }

    // ── Play-In flags ──────────────────────────────────────────────────────
    private boolean isPlayIn           = false;
    private boolean isPlayInGrandFinal = false;
    private boolean isMainBracket      = false;
    private Match   mainBracketSlot    = null;

    public Match(int round) {
        this.round = round;
        this.completed = false;
        this.team1 = null;
        this.team2 = null;
        this.winner = null;
        this.leftChild = null;
        this.rightChild = null;
        this.matchId = idCounter++;
        this.isWinnersBracket = false;
    }


    // ── GETTERS ────────────────────────────────────────────────────────────
    public Match   getLeftChild()          { return leftChild; }
    public Match   getRightChild()         { return rightChild; }
    public Team    getTeam1()              { return team1; }
    public Team    getTeam2()              { return team2; }
    public Team    getWinner()             { return winner; }
    public int     getRound()              { return round; }
    public boolean isCompleted()           { return completed; }
    public int     getMatchId()            { return matchId; }
    public String  getScore()              { return score; }
    public boolean isWinnersBracket()      { return isWinnersBracket; }
    public boolean isPlayIn()              { return isPlayIn; }
    public boolean isPlayInGrandFinal()    { return isPlayInGrandFinal; }
    public boolean isMainBracket()         { return isMainBracket; }
    public Match   getMainBracketSlot()    { return mainBracketSlot; }


    // ── SETTERS ────────────────────────────────────────────────────────────
    public void setLeftChild(Match leftChild)                  { this.leftChild = leftChild; }
    public void setRightChild(Match rightChild)                { this.rightChild = rightChild; }
    public void setTeam1(Team team1)                           { this.team1 = team1; }
    public void setTeam2(Team team2)                           { this.team2 = team2; }
    public void setMatchId(int matchId)                        { this.matchId = matchId; }
    public void setRound(int round)                            { this.round = round; }
    public void setIsWinnersBracket(boolean isWinners)         { this.isWinnersBracket = isWinners; }
    public void setIsPlayIn(boolean isPlayIn)                  { this.isPlayIn = isPlayIn; }
    public void setIsPlayInGrandFinal(boolean isGrandFinal)    { this.isPlayInGrandFinal = isGrandFinal; }
    public void setIsMainBracket(boolean isMainBracket)        { this.isMainBracket = isMainBracket; }
    public void setMainBracketSlot(Match slot)                 { this.mainBracketSlot = slot; }

    public void setWinner(Team winner, int score1, int score2) {
        this.winner = winner;
        this.score = score1 + "-" + score2;
        this.completed = true;

        Team loser = (team1 == winner) ? team2 : team1;
        if (loser != null) {
            winner.addWin(score1, score2);
            loser.addLoss(score1, score2);
        }
    }

    public void setWinner(Team winner) {
        this.winner = winner;
        this.completed = true;
    }

    public void clearResult() {
    this.winner    = null;
    this.score     = null;
    this.completed = false;
}

/** Restores a previously recorded result without triggering propagation. */
public void forceSetResult(Team winner, String score) {
    this.winner    = winner;
    this.score     = score;
    this.completed = true;
}

    @Override
    public String toString() {
        String t1 = (team1 == null) ? "TBA" : team1.getName();
        String t2 = (team2 == null) ? "TBA" : team2.getName();
        String tag = isPlayInGrandFinal ? "[PI-GF]"
                   : isPlayIn          ? "[PI]"
                   : isMainBracket     ? "[MAIN]"
                   : "";
        if (completed && winner != null) {
            return tag + "R" + round + "M" + matchId + ":"
                   + t1 + "vs" + t2 + "→" + winner.getName() + "(" + score + ")";
        }
        return tag + "R" + round + "M" + matchId + ":" + t1 + "vs" + t2 + " [PENDING]";
    }
}