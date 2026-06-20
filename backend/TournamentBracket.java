import java.util.*;

public class TournamentBracket {
    private Match root;
    private Team[] teams;
    private int totalRounds;
    private List<Match> allMatches;
    private TournamentType tournamentType;
    private List<Match> losersBracketMatches;
    private Match grandFinals;
    private Map<Match, Match> winnerToLoserMatch;
    private Map<Match, Match> lbWinnerAdvanceMap; 
    private ScoreMatrix scoreMatrix;

    public TournamentBracket(Team[] teams) {
        this(teams, TournamentType.SINGLE_ELIMINATION);
    }

    public TournamentBracket(Team[] teams, TournamentType type) {
        Match.resetIdCounter();   
        this.teams                = teams;
        this.tournamentType       = type;
        this.allMatches           = new ArrayList<>();
        this.winnerToLoserMatch   = new HashMap<>();
        this.lbWinnerAdvanceMap   = new HashMap<>();
        this.losersBracketMatches = new ArrayList<>();
        this.scoreMatrix          = new ScoreMatrix(teams);

            if  (type == TournamentType.SINGLE_ELIMINATION) {
            if (teams.length == 12) buildPlayInSE(teams, 16);
            else if (teams.length == 24) buildPlayInSE(teams, 32);
            else                         buildSingleElimination(teams);
        }
        else if (type == TournamentType.ROUND_ROBIN)                buildRoundRobin(teams);
        else if (type == TournamentType.DOUBLE_ELIMINATION) {
            if      (teams.length == 12) buildPlayInDoubleElimination(teams, 16);
            else if (teams.length == 24) buildPlayInDoubleElimination(teams, 32);
            else                         buildDoubleElimination(teams);
        }
        else if (type == TournamentType.SWISS)                      buildSwissSystem(teams);
        else if (type == TournamentType.FREE_FOR_ALL)               buildFreeForAll(teams);
        else                                                         buildSingleElimination(teams);
    }

    public TournamentBracket(Team[] teams, String type) {
        this(teams, convertToTournamentType(type));
    }

    private static TournamentType convertToTournamentType(String type) {
        if (type == null) return TournamentType.SINGLE_ELIMINATION;
        switch (type) {
            case "Single Elimination":          return TournamentType.SINGLE_ELIMINATION;
            case "Double Elimination":          return TournamentType.DOUBLE_ELIMINATION;
            case "Round Robin":                 return TournamentType.ROUND_ROBIN;
            case "Swiss System":                return TournamentType.SWISS;
            case "Free For All":                return TournamentType.FREE_FOR_ALL;
            default:                            return TournamentType.SINGLE_ELIMINATION;
        }
    }

    // =========================================================================
    // SINGLE ELIMINATION
    // =========================================================================

    private void buildSingleElimination(Team[] teams) {
        int n = teams.length;
        allMatches.clear();
        losersBracketMatches = new ArrayList<>();
        winnerToLoserMatch.clear();

        int numRounds = (int) Math.ceil(Math.log(n) / Math.log(2));
        int fullSize  = (int) Math.pow(2, numRounds);

        Team[] slots = seededSlots(teams, fullSize);

        List<Match> currentRound = new ArrayList<>();
        int matchId = 1;

        for (int i = 0; i < fullSize; i += 2) {
            Team t1 = slots[i];
            Team t2 = slots[i + 1];
            if (t1 == null && t2 == null) continue;
            Match m = new Match(1);
            m.setMatchId(matchId++);
            if (t1 != null) m.setTeam1(t1);
            if (t2 != null) m.setTeam2(t2);
            if (!isPowerOfTwo(n)) autoCompleteBye(m); 
            if (m.getTeam1() != null || m.getTeam2() != null) {
                currentRound.add(m);
                allMatches.add(m);
            }
        }

        int round = 2;
        while (currentRound.size() > 1) {
            List<Match> next = new ArrayList<>();
            for (int i = 0; i + 1 < currentRound.size(); i += 2) {
                Match parent = new Match(round);
                parent.setMatchId(matchId++);
                parent.setLeftChild(currentRound.get(i));
                parent.setRightChild(currentRound.get(i + 1));
                if (currentRound.get(i).isCompleted())
                    parent.setTeam1(currentRound.get(i).getWinner());
                if (currentRound.get(i + 1).isCompleted())
                    parent.setTeam2(currentRound.get(i + 1).getWinner());
                next.add(parent);
                allMatches.add(parent);
            }
            currentRound = next;
            round++;
        }

        this.totalRounds = round - 1;
        this.root = currentRound.isEmpty() ? null : currentRound.get(0);
    }

    private boolean isPowerOfTwo(int n) { return n > 0 && (n & (n - 1)) == 0; }


    // =========================================================================
    // SINGLE ELIMINATION — PLAY-IN (used for 12 and 24 team brackets)
    //
    // bracketSize is the next power-of-two slot grid (16 for 12 teams,
    // 32 for 24 teams). Top seeds (whatever doesn't fit in the contested
    // slots) get byes via seededSlots()+autoCompleteBye(); everyone else
    // plays a real Round 1 match. Rounds 2..N are standard halving.
    // =========================================================================

    private void buildPlayInSE(Team[] teams, int bracketSize) {
        allMatches.clear();
        losersBracketMatches = new ArrayList<>();
        winnerToLoserMatch.clear();

        int matchId = 1;
        Team[] slots = seededSlots(teams, bracketSize);

        List<Match> r1Matches = new ArrayList<>();
        for (int i = 0; i < bracketSize; i += 2) {
            Team t1 = slots[i];
            Team t2 = slots[i + 1];
            if (t1 == null && t2 == null) continue;
            Match m = new Match(1);
            m.setMatchId(matchId++);
            if (t1 != null) m.setTeam1(t1);
            if (t2 != null) m.setTeam2(t2);
            autoCompleteBye(m);
            r1Matches.add(m);
            allMatches.add(m);
        }

        // Rounds 2-N: standard halving
        List<Match> currentRound = r1Matches;
        int round = 2;
        while (currentRound.size() > 1) {
            List<Match> next = new ArrayList<>();
            for (int i = 0; i + 1 < currentRound.size(); i += 2) {
                Match parent = new Match(round);
                parent.setMatchId(matchId++);
                parent.setLeftChild(currentRound.get(i));
                parent.setRightChild(currentRound.get(i + 1));
                if (currentRound.get(i).isCompleted())
                    parent.setTeam1(currentRound.get(i).getWinner());
                if (currentRound.get(i + 1).isCompleted())
                    parent.setTeam2(currentRound.get(i + 1).getWinner());
                next.add(parent);
                allMatches.add(parent);
            }
            currentRound = next;
            round++;
        }

        this.totalRounds = round - 1;
        this.root = currentRound.isEmpty() ? null : currentRound.get(0);

        System.out.println("Play-in SE built: bracketSize=" + bracketSize
            + " totalRounds=" + totalRounds + " total=" + allMatches.size());
    }


    // =========================================================================
    // DOUBLE ELIMINATION — PLAY-IN (used for 12 and 24 team brackets)
    //
    // bracketSize is the next power-of-two slot grid (16 for 12 teams,
    // 32 for 24 teams). Top seeds get WB byes; everyone else plays a real
    // WB Round 1 match (same seededSlots()+autoCompleteBye() trick as the
    // single-elimination play-in build).
    //
    // Winners Bracket:
    //   WB R1: bracketSize/2 matches (real + auto-bye)
    //   WB R2: bracketSize/4 matches — real R1 winners vs bye-seed winners
    //   WB R3..N: standard halving down to the WB Final
    //
    // Losers Bracket:
    //   LB R1 (elim): the real WB-R1 losers play each other
    //   LB R2 (drop): LB-R1 survivors vs first half of WB-R2 losers
    //   LB R3 (drop): LB-R2 survivors vs second half of WB-R2 losers
    //   For each remaining WB round (QF, SF, ... up to but excluding the
    //   WB Final): a drop round (LB survivors vs that round's WB losers)
    //   followed by an elim round (survivors play each other) whenever
    //   more than one match remains.
    //   Losers Final: last LB survivor vs the WB Final loser.
    //
    // Grand Final caps it off (WB Final winner vs Losers Final winner).
    // =========================================================================

    private void buildPlayInDoubleElimination(Team[] teams, int bracketSize) {
        allMatches.clear();
        losersBracketMatches = new ArrayList<>();
        winnerToLoserMatch.clear();
        lbWinnerAdvanceMap = new HashMap<>();

        int matchId       = 1;
        int losersMatchId = 1000;

        Team[] slots = seededSlots(teams, bracketSize);

        // ── WB Round 1: bracketSize/2 matches (real + auto-bye) ─────────────
        List<Match> wbR1All  = new ArrayList<>();
        List<Match> wbR1Real = new ArrayList<>();
        List<Match> wbR1Bye  = new ArrayList<>();

        for (int i = 0; i < bracketSize; i += 2) {
            Team t1 = slots[i];
            Team t2 = slots[i + 1];
            if (t1 == null && t2 == null) continue;
            Match m = new Match(1);
            m.setMatchId(matchId++);
            m.setIsWinnersBracket(true);
            if (t1 != null) m.setTeam1(t1);
            if (t2 != null) m.setTeam2(t2);
            boolean isBye = autoCompleteBye(m);
            wbR1All.add(m);
            allMatches.add(m);
            if (isBye) wbR1Bye.add(m);
            else        wbR1Real.add(m);
        }

        // ── WB Round 2: real R1 winners vs bye-seed winners ──────────────────
        List<Match> wbR2 = new ArrayList<>();
        for (int i = 0; i + 1 < wbR1All.size(); i += 2) {
            Match left  = wbR1All.get(i);
            Match right = wbR1All.get(i + 1);
            Match m = new Match(2);
            m.setMatchId(matchId++);
            m.setIsWinnersBracket(true);
            m.setLeftChild(left);
            m.setRightChild(right);
            if (left.isCompleted())  m.setTeam1(left.getWinner());
            if (right.isCompleted()) m.setTeam2(right.getWinner());
            wbR2.add(m);
            allMatches.add(m);
        }

        // ── WB Rounds 3..N: standard halving ─────────────────────────────────
        int numWBRounds = (int) Math.round(Math.log(bracketSize) / Math.log(2));

        List<List<Match>> wbRounds = new ArrayList<>();
        wbRounds.add(wbR1All);
        wbRounds.add(wbR2);
        List<Match> wbCurrent = wbR2;
        for (int r = 3; r <= numWBRounds; r++) {
            List<Match> wbNext = new ArrayList<>();
            for (int i = 0; i + 1 < wbCurrent.size(); i += 2) {
                Match m = new Match(r);
                m.setMatchId(matchId++);
                m.setIsWinnersBracket(true);
                m.setLeftChild(wbCurrent.get(i));
                m.setRightChild(wbCurrent.get(i + 1));
                if (wbCurrent.get(i).isCompleted())   m.setTeam1(wbCurrent.get(i).getWinner());
                if (wbCurrent.get(i + 1).isCompleted()) m.setTeam2(wbCurrent.get(i + 1).getWinner());
                wbNext.add(m);
                allMatches.add(m);
            }
            wbRounds.add(wbNext);
            wbCurrent = wbNext;
        }
        Match wbFinal = wbCurrent.get(0);

        // ── LOSERS BRACKET ───────────────────────────────────────────────────
        int lbRound = 1;

        // LB R1 (elim): the real WB-R1 losers play each other
        List<Match> lb1 = new ArrayList<>();
        for (int i = 0; i + 1 < wbR1Real.size(); i += 2) {
            Match lm = new Match(lbRound);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR1Real.get(i),     lm);
            winnerToLoserMatch.put(wbR1Real.get(i + 1), lm);
            lb1.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }
        lbRound++;
        List<Match> lbPrev = lb1;

        // LB R2 (drop): LB-R1 survivors vs first half of WB-R2 losers
        int half = wbR2.size() / 2;
        List<Match> lb2 = new ArrayList<>();
        for (int i = 0; i < half; i++) {
            Match lm = new Match(lbRound);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR2.get(i), lm);
            lbWinnerAdvanceMap.put(lbPrev.get(i), lm);
            lb2.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }
        lbRound++;
        lbPrev = lb2;

        // LB R3 (drop): LB-R2 survivors vs second half of WB-R2 losers
        List<Match> lb3 = new ArrayList<>();
        for (int i = 0; i < half; i++) {
            Match lm = new Match(lbRound);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR2.get(half + i), lm);
            lbWinnerAdvanceMap.put(lbPrev.get(i), lm);
            lb3.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }
        lbRound++;
        lbPrev = lb3;

        // Remaining WB rounds (QF, SF, ... up to but excluding the WB Final):
        // each gets a drop round, followed by an elim round whenever more
        // than one match survives.
        for (int wr = 3; wr <= numWBRounds - 1; wr++) {
            List<Match> wbThisRound = wbRounds.get(wr - 1);

            List<Match> drop = new ArrayList<>();
            for (int i = 0; i < wbThisRound.size(); i++) {
                Match lm = new Match(lbRound);
                lm.setMatchId(losersMatchId++);
                winnerToLoserMatch.put(wbThisRound.get(i), lm);
                lbWinnerAdvanceMap.put(lbPrev.get(i), lm);
                drop.add(lm);
                losersBracketMatches.add(lm);
                allMatches.add(lm);
            }
            lbRound++;
            lbPrev = drop;

            if (drop.size() > 1) {
                List<Match> elim = new ArrayList<>();
                for (int i = 0; i + 1 < drop.size(); i += 2) {
                    Match lm = new Match(lbRound);
                    lm.setMatchId(losersMatchId++);
                    lbWinnerAdvanceMap.put(drop.get(i),     lm);
                    lbWinnerAdvanceMap.put(drop.get(i + 1), lm);
                    elim.add(lm);
                    losersBracketMatches.add(lm);
                    allMatches.add(lm);
                }
                lbRound++;
                lbPrev = elim;
            }
        }

        // Losers Final: last LB survivor vs the WB Final loser
        Match losersFinal = new Match(lbRound);
        losersFinal.setMatchId(losersMatchId++);
        winnerToLoserMatch.put(wbFinal, losersFinal);
        lbWinnerAdvanceMap.put(lbPrev.get(0), losersFinal);
        losersBracketMatches.add(losersFinal);
        allMatches.add(losersFinal);
        lbRound++;

        // ── GRAND FINAL ──────────────────────────────────────────────────────
        Match gf = new Match(lbRound);
        gf.setMatchId(matchId++);
        gf.setLeftChild(wbFinal);
        gf.setRightChild(losersFinal);
        allMatches.add(gf);

        this.grandFinals = gf;
        this.root        = gf;
        this.totalRounds = lbRound;

        System.out.println("Play-in DE built: bracketSize=" + bracketSize
            + " WBmatches=" + wbRounds.stream().mapToInt(List::size).sum()
            + " LBmatches=" + losersBracketMatches.size()
            + " total=" + allMatches.size());
    }


    // =========================================================================
    // DOUBLE ELIMINATION  (power-of-2 only: 4, 8, 16, 32)
    // =========================================================================

    private void buildDoubleElimination(Team[] teams) {
        int n         = teams.length;
        int numRounds = (int)(Math.log(n) / Math.log(2));
        int fullSize  = n;

        allMatches.clear();
        losersBracketMatches = new ArrayList<>();
        winnerToLoserMatch.clear();
        lbWinnerAdvanceMap = new HashMap<>();

        int matchId       = 1;
        int losersMatchId = 1000;

        Team[] slots = seededSlots(teams, fullSize);

        // WB Round 1
        List<Match> wbR1 = new ArrayList<>();
        for (int i = 0; i < fullSize; i += 2) {
            Match m = new Match(1);
            m.setMatchId(matchId++);
            m.setIsWinnersBracket(true);
            m.setTeam1(slots[i]);
            m.setTeam2(slots[i + 1]);
            wbR1.add(m);
            allMatches.add(m);
        }

        List<List<Match>> wbRounds = new ArrayList<>();
        wbRounds.add(wbR1);
        List<Match> wbCurrent = new ArrayList<>(wbR1);

        for (int r = 2; r <= numRounds; r++) {
            List<Match> wbNext = new ArrayList<>();
            for (int i = 0; i + 1 < wbCurrent.size(); i += 2) {
                Match m = new Match(r);
                m.setMatchId(matchId++);
                m.setIsWinnersBracket(true);
                m.setLeftChild(wbCurrent.get(i));
                m.setRightChild(wbCurrent.get(i + 1));
                wbNext.add(m);
                allMatches.add(m);
            }
            wbRounds.add(wbNext);
            wbCurrent = wbNext;
        }
        Match wbFinal = wbCurrent.get(0);

        int lbRoundNum = numRounds;
        Map<Integer, List<Match>> lbRoundMap = new LinkedHashMap<>();

        lbRoundNum++;
        List<Match> lbElim1 = new ArrayList<>();
        List<Match> wbR1List = wbRounds.get(0);
        for (int i = 0; i + 1 < wbR1List.size(); i += 2) {
            Match lm = new Match(lbRoundNum);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR1List.get(i),     lm);
            winnerToLoserMatch.put(wbR1List.get(i + 1), lm);
            lbElim1.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }
        lbRoundMap.put(lbRoundNum, lbElim1);

        List<Match> lbSurvivors = new ArrayList<>(lbElim1);

        for (int wr = 2; wr <= numRounds; wr++) {
            List<Match> wbThisRound = wbRounds.get(wr - 1);

            lbRoundNum++;
            List<Match> dropRound = new ArrayList<>();
            for (int i = 0; i < wbThisRound.size() && i < lbSurvivors.size(); i++) {
                Match lm = new Match(lbRoundNum);
                lm.setMatchId(losersMatchId++);
                lm.setLeftChild(lbSurvivors.get(i));
                winnerToLoserMatch.put(wbThisRound.get(i), lm);
                // Wire: winner of lbSurvivors[i] advances into lm as team2
                lbWinnerAdvanceMap.put(lbSurvivors.get(i), lm);
                dropRound.add(lm);
                losersBracketMatches.add(lm);
                allMatches.add(lm);
            }
            lbRoundMap.put(lbRoundNum, dropRound);

            if (dropRound.size() > 1) {
                lbRoundNum++;
                List<Match> elimRound = new ArrayList<>();
                for (int i = 0; i + 1 < dropRound.size(); i += 2) {
                    Match lm = new Match(lbRoundNum);
                    lm.setMatchId(losersMatchId++);
                    lm.setLeftChild(dropRound.get(i));
                    lm.setRightChild(dropRound.get(i + 1));
                    // Wire: winners of dropRound matches advance into lm
                    lbWinnerAdvanceMap.put(dropRound.get(i),     lm);
                    lbWinnerAdvanceMap.put(dropRound.get(i + 1), lm);
                    elimRound.add(lm);
                    losersBracketMatches.add(lm);
                    allMatches.add(lm);
                }
                lbRoundMap.put(lbRoundNum, elimRound);
                lbSurvivors = elimRound;
            } else {
                lbSurvivors = dropRound;
            }
        }

        Match lbFinal = lbSurvivors.isEmpty() ? null : lbSurvivors.get(0);

        lbRoundNum++;
        grandFinals = new Match(lbRoundNum);
        grandFinals.setMatchId(matchId);
        grandFinals.setLeftChild(wbFinal);
        if (lbFinal != null) grandFinals.setRightChild(lbFinal);
        allMatches.add(grandFinals);
        this.root        = grandFinals;
        this.totalRounds = lbRoundNum;

        System.out.println("DE built: n=" + n + " WBr=" + numRounds
            + " LBmatches=" + losersBracketMatches.size()
            + " total=" + allMatches.size());
    }

    // =========================================================================
    // ROUND ROBIN
    // =========================================================================

    private void buildRoundRobin(Team[] teams) {
        int n = teams.length;
        allMatches.clear();

        // If n is odd, add a dummy null slot so the circle algorithm works cleanly.
        // The match involving the null slot is simply skipped (that team has a bye).
        int size = (n % 2 == 0) ? n : n + 1;
        Team[] circle = new Team[size];
        for (int i = 0; i < n; i++) circle[i] = teams[i];
        // circle[size-1] == null when n is odd

        int numRounds = size - 1;
        this.totalRounds = numRounds;

        int matchId = 1;
        for (int round = 1; round <= numRounds; round++) {
            // Pair position 0 vs size/2, then 1 vs size-1, 2 vs size-2, …
            for (int i = 0; i < size / 2; i++) {
                Team t1 = circle[i];
                Team t2 = circle[size - 1 - i];
                if (t1 != null && t2 != null) {
                    Match m = new Match(round);
                    m.setMatchId(matchId++);
                    m.setTeam1(t1);
                    m.setTeam2(t2);
                    allMatches.add(m);
                }
            }
            // Rotate: keep circle[0] fixed, rotate the rest one step clockwise
            Team last = circle[size - 1];
            for (int i = size - 1; i > 1; i--) circle[i] = circle[i - 1];
            circle[1] = last;
        }

        this.root = null;
        System.out.println("Round Robin built: " + n + " teams, " + numRounds
            + " rounds, " + allMatches.size() + " matches");
    }

    // =========================================================================
    // SWISS SYSTEM
    // =========================================================================

    // =========================================================================
    // SWISS SYSTEM — lazy round generation
    //
    // Only Round 1 is generated at construction time.
    // Call generateNextSwissRound() after every round's matches are all complete
    // to produce the next round's standings-based pairings.
    //
    // Pairing order (each round):
    //   1. Sort teams by wins DESC, Buchholz DESC, point-differential DESC
    //   2. Greedily pair adjacent teams, skipping rematches with a look-ahead swap
    //   3. If a team is left over (odd count), give a bye (1 win, no score recorded)
    //
    // Rounds per team count: 4-8 = 3, 9-16 = 4, 17-20 = 5
    // =========================================================================

    private int swissCurrentRound = 0;

    private void buildSwissSystem(Team[] teams) {
        int numTeams = teams.length;

        if      (numTeams <=  8) this.totalRounds = 3;
        else if (numTeams <= 16) this.totalRounds = 4;
        else                     this.totalRounds = 5;

        allMatches.clear();
        swissCurrentRound = 0;
        this.root = null;

        generateNextSwissRound();

        System.out.println("Swiss System built: " + numTeams + " teams, "
            + totalRounds + " rounds planned, R1 matches=" + allMatches.size());
    }

    /**
     * Returns true if all matches in the current Swiss round are complete
     * and there are still rounds left to generate.
     */
    public boolean canGenerateNextSwissRound() {
        if (swissCurrentRound >= totalRounds) return false;
        if (swissCurrentRound == 0) return false;
        List<Match> current = getMatchesByRound(swissCurrentRound);
        if (current.isEmpty()) return false;
        for (Match m : current) if (!m.isCompleted()) return false;
        return true;
    }

    /**
     * Generates the next Swiss round's pairings based on current standings.
     * Called at construction (R1) and after each round completes.
     */
    public void generateNextSwissRound() {
        if (swissCurrentRound >= totalRounds) return;
        swissCurrentRound++;
        int round = swissCurrentRound;

        List<Team> sorted = new ArrayList<>(Arrays.asList(teams));

        // Round 1: shuffle for variety; subsequent rounds: standings-based
        if (round == 1) {
            Collections.shuffle(sorted);
        } else {
            sorted.sort((a, b) -> {
                int wDiff = Integer.compare(b.getWins(), a.getWins());
                if (wDiff != 0) return wDiff;
                int bDiff = Integer.compare(buchholz(b), buchholz(a));
                if (bDiff != 0) return bDiff;
                return Integer.compare(b.getPointDifference(), a.getPointDifference());
            });
        }

        List<Team> unpaired = new ArrayList<>(sorted);
        List<int[]> pairs   = new ArrayList<>();

        while (unpaired.size() >= 2) {
            Team t1 = unpaired.remove(0);
            boolean paired = false;
            for (int j = 0; j < unpaired.size(); j++) {
                Team t2 = unpaired.get(j);
                if (!havePlayedBefore(t1, t2)) {
                    unpaired.remove(j);
                    pairs.add(new int[]{ indexOf(t1), indexOf(t2) });
                    paired = true;
                    break;
                }
            }
            // Every remaining opponent is a rematch — take closest anyway
            if (!paired && !unpaired.isEmpty()) {
                Team t2 = unpaired.remove(0);
                pairs.add(new int[]{ indexOf(t1), indexOf(t2) });
            }
        }

        // Bye for odd team count — create a bye match (team1 wins automatically)
        if (!unpaired.isEmpty()) {
            Team bye = unpaired.get(0);
            Match byeMatch = new Match(round);
            byeMatch.setTeam1(bye);
            byeMatch.setWinner(bye, 1, 0); // auto-complete: bye = 1-0 win
            allMatches.add(byeMatch);
            System.out.println("Swiss R" + round + " BYE: " + bye.getName());
        }

        for (int[] p : pairs) {
            Match m = new Match(round);
            m.setTeam1(teams[p[0]]);
            m.setTeam2(teams[p[1]]);
            allMatches.add(m);
        }

        System.out.println("Swiss R" + round + " generated: " + pairs.size() + " matches");
    }

    /** Buchholz = sum of all opponents' current wins */
    private int buchholz(Team t) {
        int sum = 0;
        for (Match m : allMatches) {
            if (!m.isCompleted()) continue;
            if (m.getTeam1() == t && m.getTeam2() != null) sum += m.getTeam2().getWins();
            else if (m.getTeam2() == t && m.getTeam1() != null) sum += m.getTeam1().getWins();
        }
        return sum;
    }

    private boolean havePlayedBefore(Team a, Team b) {
        for (Match m : allMatches) {
            if ((m.getTeam1() == a && m.getTeam2() == b)
             || (m.getTeam1() == b && m.getTeam2() == a)) return true;
        }
        return false;
    }

    private int indexOf(Team t) {
        for (int i = 0; i < teams.length; i++) if (teams[i] == t) return i;
        return -1;
    }

    public int getSwissCurrentRound() { return swissCurrentRound; }


    // =========================================================================
    // FREE FOR ALL
    // =========================================================================

    private void buildFreeForAll(Team[] teams) {
        // FFA = full round-robin schedule (every team plays every other team once),
        // distributed across (n-1) rounds using the standard circle/polygon algorithm.
        // Requires at least 4 teams.
        int n = teams.length;
        allMatches.clear();

        // If n is odd, add a dummy null slot so the circle algorithm works cleanly
        // (the match involving null is simply skipped).
        int size = (n % 2 == 0) ? n : n + 1;
        Team[] circle = new Team[size];
        for (int i = 0; i < n; i++) circle[i] = teams[i];
        // circle[size-1] == null for odd n

        int numRounds = size - 1;
        this.totalRounds = numRounds;

        for (int round = 1; round <= numRounds; round++) {
            // Pair position 0 with position (size/2), then 1 with (size-1), 2 with (size-2), …
            for (int i = 0; i < size / 2; i++) {
                Team t1 = circle[i];
                Team t2 = circle[size - 1 - i];
                if (t1 != null && t2 != null) {
                    Match m = new Match(round);
                    m.setTeam1(t1);
                    m.setTeam2(t2);
                    allMatches.add(m);
                }
            }
            // Rotate: keep circle[0] fixed, rotate the rest one step clockwise
            Team last = circle[size - 1];
            for (int i = size - 1; i > 1; i--) circle[i] = circle[i - 1];
            circle[1] = last;
        }

        this.root = null;
    }

    // =========================================================================
    // SEEDING HELPERS
    // =========================================================================

    private Team[] seededSlots(Team[] teams, int bracketSize) {
        int[] seedInSlot = buildSeedOrder(bracketSize);
        Team[] slots = new Team[bracketSize];
        for (int i = 0; i < bracketSize; i++) {
            int seedNum = seedInSlot[i];
            if (seedNum <= teams.length) slots[i] = teams[seedNum - 1];
        }
        return slots;
    }

    private int[] buildSeedOrder(int size) {
        List<Integer> result = buildSeedOrderList(size);
        int[] arr = new int[size];
        for (int i = 0; i < size; i++) arr[i] = result.get(i);
        return arr;
    }

    private List<Integer> buildSeedOrderList(int n) {
        if (n == 1) {
            List<Integer> base = new ArrayList<>();
            base.add(1);
            return base;
        }
        List<Integer> half = buildSeedOrderList(n / 2);
        List<Integer> result = new ArrayList<>();
        for (int h : half) {
            result.add(h);
            result.add(n + 1 - h);
        }
        return result;
    }

    private boolean autoCompleteBye(Match m) {
        if (m.getTeam1() != null && m.getTeam2() == null) {
            m.setWinner(m.getTeam1(), 1, 0);
            return true;
        }
        if (m.getTeam1() == null && m.getTeam2() != null) {
            m.setWinner(m.getTeam2(), 0, 1);
            return true;
        }
        return false;
    }

    // =========================================================================
    // QUERY
    // =========================================================================

    public List<Match> getMatchesByRound(int round) {
        List<Match> result = new ArrayList<>();
        for (Match m : allMatches)
            if (m.getRound() == round) result.add(m);
        result.sort(Comparator.comparingInt(Match::getMatchId));
        return result;
    }

    public List<Match> getWinnersMatchesByRound(int round) {
        List<Match> result = new ArrayList<>();
        for (Match m : allMatches)
            if (m.getRound() == round && m != grandFinals && m.isWinnersBracket())
                result.add(m);
        result.sort(Comparator.comparingInt(Match::getMatchId));
        return result;
    }

    public List<Match> getAllMatches()            { return allMatches; }
    public List<Match> getLosersBracketMatches()  { return losersBracketMatches; }
    public Match       getGrandFinals()           { return grandFinals; }
    public int         getTotalRounds()           { return totalRounds; }
    public Team[]      getTeams()                 { return teams; }
    public Match       getChampionship()          { return root; }

    public int         getWinnersRounds() {
        if (tournamentType == TournamentType.DOUBLE_ELIMINATION && teams.length == 24) return 5;
        if (tournamentType == TournamentType.DOUBLE_ELIMINATION && teams.length == 12) return 4;
        return (int) Math.ceil(Math.log(Math.max(teams.length, 2)) / Math.log(2));
    }

    public List<Match> getWinnersBracketMatches() {
        List<Match> r = new ArrayList<>();
        for (Match m : allMatches)
            if (m.isWinnersBracket() && m != grandFinals) r.add(m);
        return r;
    }

    public List<Match> getPendingMatches() {
        List<Match> r = new ArrayList<>();
        for (Match m : allMatches)
            if (!m.isCompleted() && m.getTeam1() != null && m.getTeam2() != null)
                r.add(m);
        return r;
    }

    public Match getCurrentMatch() {
        List<Match> p = getPendingMatches();
        return p.isEmpty() ? null : p.get(0);
    }

    public String getProgress() {
        int total = allMatches.size(), done = 0;
        for (Match m : allMatches) if (m.isCompleted()) done++;
        return done + "/" + total + " matches completed";
    }

    public Team getTournamentWinner() {
        if (tournamentType == TournamentType.ROUND_ROBIN
                || tournamentType == TournamentType.SWISS
                || tournamentType == TournamentType.FREE_FOR_ALL) {
            // Only declare a winner when every match has been played
            for (Match m : allMatches) if (!m.isCompleted()) return null;
            if (allMatches.isEmpty()) return null;
            Team champ = null; int best = -1;
            for (Team t : teams) {
                if (t.getWins() > best) { best = t.getWins(); champ = t; }
                else if (t.getWins() == best && champ != null
                         && t.getPointDifference() > champ.getPointDifference())
                    champ = t;
            }
            return champ;
        }
        if (root == null || !root.isCompleted()) return null;
        if (tournamentType == TournamentType.DOUBLE_ELIMINATION) {
            if (grandFinals == null) return null;
            if (grandFinals.getTeam1() == null || grandFinals.getTeam2() == null) return null;
        }
        if (root.getTeam1() == null || root.getTeam2() == null) return null;
        return root.getWinner();
    }

    // =========================================================================
    // RECORD WINNER / PROPAGATION
    // =========================================================================

    public void recordWinner(Match match, Team winner, int score1, int score2) {
        if (match.getTeam1() == null || match.getTeam2() == null) {
            System.out.println("Error: Match missing a team!");
            return;
        }
        if (score1 == score2) {
            System.out.println("Error: Tied scores (" + score1 + "-" + score2 + ") are not allowed. Match not recorded.");
            return;
        }
        match.setWinner(winner, score1, score2);

        // Record scores in the matrix using team array index as ID
        int id1 = -1, id2 = -1;
        for (int i = 0; i < teams.length; i++) {
            if (teams[i] == match.getTeam1()) id1 = i;
            if (teams[i] == match.getTeam2()) id2 = i;
        }
        if (id1 >= 0 && id2 >= 0) {
            // score1 is always team1's score, score2 is always team2's score — no swap needed
            scoreMatrix.recordMatch(id1, id2, score1, score2);
        }

        if (tournamentType == TournamentType.SINGLE_ELIMINATION
                || tournamentType == TournamentType.DOUBLE_ELIMINATION)
            propagateWinnerUp(match, winner);

        System.out.println("✓ Recorded: " + match);
    }

    public ScoreMatrix getScoreMatrix() { return scoreMatrix; }

    /** Clears the entire score matrix (all matches revert to "not played"). */
    public void clearScoreMatrix() {
        for (int i = 0; i < teams.length; i++)
            for (int j = 0; j < teams.length; j++)
                scoreMatrix.clearMatch(i, j);
    }

    /** Public wrapper for recalculateTeamStats — used by the undo system in app.java. */
    public void recalculateAllTeamStats() {
        recalculateTeamStats();
    }

    /**
     * Resets every team's win, loss, and point-difference counters to zero,
     * then replays all completed matches to rebuild the correct totals.
     * Call this after any undo/revert operation.
     */
    private void recalculateTeamStats() {
        for (Team t : teams) t.resetStats();
        for (Match m : allMatches) {
            if (!m.isCompleted() || m.getWinner() == null) continue;
            Team winner = m.getWinner();
            Team loser  = (m.getTeam1() == winner) ? m.getTeam2() : m.getTeam1();
            if (loser == null) continue;
            if (m.getScore() != null) {
                try {
                    String[] parts = m.getScore().split("-");
                    int s1 = Integer.parseInt(parts[0].trim());
                    int s2 = Integer.parseInt(parts[1].trim());
                    // s1 is always team1's score, s2 is always team2's score
                    boolean winnerIsTeam1 = (m.getTeam1() == winner);
                    int winnerScore = winnerIsTeam1 ? s1 : s2;
                    int loserScore  = winnerIsTeam1 ? s2 : s1;
                    winner.addWin(winnerScore, loserScore);
                    loser.addLoss(loserScore, winnerScore);
                } catch (Exception ignored) {
                    // Fallback: no score data, just count the win/loss with zeros
                    winner.addWin(0, 0);
                    loser.addLoss(0, 0);
                }
            }
        }
    }

    public void revertMatch(Match match, String winnerId, String score) {
        if (winnerId == null) {
            // Match was incomplete in the snapshot — wipe its result
            match.clearResult();
        } else {
            // Match was completed — find the winner by name and restore it
            Team winner = null;
            for (Team t : teams) {
                if (t.getName().equals(winnerId)) { winner = t; break; }
            }
            if (winner != null) {
                match.forceSetResult(winner, score);
                // Re-record in the score matrix
                int id1 = -1, id2 = -1;
                for (int i = 0; i < teams.length; i++) {
                    if (teams[i] == match.getTeam1()) id1 = i;
                    if (teams[i] == match.getTeam2()) id2 = i;
                }
                if (id1 >= 0 && id2 >= 0 && score != null) {
                    try {
                        String[] parts = score.split("-");
                        int s1 = Integer.parseInt(parts[0].trim());
                        int s2 = Integer.parseInt(parts[1].trim());
                        scoreMatrix.recordMatch(id1, id2, s1, s2);
                    } catch (Exception ignored) {}
                }
                // Propagate winner into the next round's team slot,
                // exactly as recordWinner does — this fills later-round TBA slots.
                if (tournamentType == TournamentType.SINGLE_ELIMINATION
                        || tournamentType == TournamentType.DOUBLE_ELIMINATION) {
                    propagateWinnerUp(match, winner);
                }
            }
        }
        // Note: call recalculateAllTeamStats() once after ALL matches are reverted
        // (done by performUndo in app.java) rather than here per-match,
        // to avoid counting partial state during the undo loop.
    }

    private void propagateWinnerUp(Match currentMatch, Team winner) {
        Team loser = (currentMatch.getTeam1() == winner)
                     ? currentMatch.getTeam2() : currentMatch.getTeam1();

        // ── Feed loser into LB (WB matches only) ─────────────────────────────
        if (tournamentType == TournamentType.DOUBLE_ELIMINATION && loser != null
                && currentMatch.getTeam1() != null && currentMatch.getTeam2() != null) {
            Match lbSlot = winnerToLoserMatch.get(currentMatch);
            if (lbSlot != null && !lbSlot.isCompleted()) {
                if (lbSlot.getTeam1() == null) {
                    lbSlot.setTeam1(loser);
                } else if (lbSlot.getTeam2() == null && lbSlot.getTeam1() != loser) {
                    lbSlot.setTeam2(loser);
                }
            }
        }

        // ── Advance winner via lbWinnerAdvanceMap (LB drop/elim rounds) ──────
        Match nextLb = lbWinnerAdvanceMap.get(currentMatch);
        if (nextLb != null && !nextLb.isCompleted()) {
            if (nextLb.getTeam2() == null) {
                nextLb.setTeam2(winner);
                System.out.println("→ LB Winner " + winner.getName() + " → match " + nextLb.getMatchId() + " (team2)");
            } else if (nextLb.getTeam1() == null) {
                nextLb.setTeam1(winner);
                System.out.println("→ LB Winner " + winner.getName() + " → match " + nextLb.getMatchId() + " (team1)");
            }
            return;
        }


        // ── Advance winner via child links (WB rounds + GF) ───────────────────
        for (Match m : allMatches) {
            if (m.getLeftChild() == currentMatch) {
                m.setTeam1(winner);
                if (m.isCompleted()) propagateWinnerUp(m, m.getWinner());
                return;
            } else if (m.getRightChild() == currentMatch) {
                m.setTeam2(winner);
                if (m.isCompleted()) propagateWinnerUp(m, m.getWinner());
                return;
            }
        }
    }


    // =========================================================================
    // DEBUG
    // =========================================================================

    public void printBracket() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🏆 TOURNAMENT - " + tournamentType.getDisplayName());
        System.out.println("=".repeat(60));
        if (tournamentType == TournamentType.DOUBLE_ELIMINATION) {
            int numRounds = (teams.length == 24) ? 5
                          : (int) Math.ceil(Math.log(teams.length) / Math.log(2));
            System.out.println("\nWINNERS BRACKET:");
            for (int r = 1; r <= numRounds; r++) {
                List<Match> ms = getWinnersMatchesByRound(r);
                if (!ms.isEmpty()) { System.out.println("  R" + r + ":"); for (Match m : ms) System.out.println("    " + m); }
            }
            System.out.println("\nLOSERS BRACKET:");
            for (Match m : losersBracketMatches) System.out.println("  R" + m.getRound() + ": " + m);
            if (grandFinals != null) System.out.println("\nGRAND FINAL:\n  " + grandFinals);
        } else {
            for (int r = 1; r <= totalRounds; r++) {
                List<Match> ms = getMatchesByRound(r);
                if (!ms.isEmpty()) { System.out.println("  R" + r + ":"); for (Match m : ms) System.out.println("    " + m); }
            }
        }
    }

    public void printStandings() {
        System.out.println("\n📊 STANDINGS:");
        List<Team> sorted = new ArrayList<>(Arrays.asList(teams));
        sorted.sort((a, b) -> Integer.compare(b.getWins(), a.getWins()));
        for (int i = 0; i < sorted.size(); i++) {
            Team t = sorted.get(i);
            System.out.printf("%d. %-12s W:%d L:%d PD:%d Win%%:%.1f%%%n",
                    i+1, t.getName(), t.getWins(), t.getLosses(),
                    t.getPointDifference(), t.getWinPercentage());
        }
    }
}