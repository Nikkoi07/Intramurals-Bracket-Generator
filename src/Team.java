package src;
public class Team {
    private int id;
    private String name;
    private int wins;
    private int losses;
    private int pointsScored;
    private int pointsAllowed;
    
    public Team(int id, String name) {
        this.id = id;
        this.name = name;
        this.wins = 0;
        this.losses = 0;
        this.pointsScored = 0;
        this.pointsAllowed = 0;
    }
    
    public int getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public int getWins() {
        return wins;
    }
    
    public int getLosses() {
        return losses;
    }
    
    public int getPointsScored() {
        return pointsScored;
    }
    
    public int getPointsAllowed() {
        return pointsAllowed;
    }
    
    public int getPointDifference() {
        return pointsScored - pointsAllowed;
    }
    
    public double getWinPercentage() {
        int total = wins + losses;
        if (total == 0) return 0;
        return (wins * 100.0) / total;
    }
    
    public void addWin(int scoreFor, int scoreAgainst) {
        this.wins++;
        this.pointsScored += scoreFor;
        this.pointsAllowed += scoreAgainst;
    }
    
    public void addLoss(int scoreFor, int scoreAgainst) {
        this.losses++;
        this.pointsScored += scoreFor;
        this.pointsAllowed += scoreAgainst;
    }
    
    @Override
    public String toString() {
        return name;
    }
}